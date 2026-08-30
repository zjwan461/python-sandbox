package io.github.sandbox.service;

import io.github.sandbox.config.SandboxConfig;
import io.github.sandbox.config.SandboxConfig.PythonSecurity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Python 代码安全校验器
 *
 * <p>采用"预处理 + 正则静态分析"的方式识别 Python 代码中的危险操作。
 * 设计原则：平衡安全性与可用性，默认仅拦截真正高危的破坏性操作，
 * 保留对常用模块（math、json、os.path、pathlib 等）的正常使用。</p>
 *
 * <p>校验流程：
 * <ol>
 *   <li>长度限制：防止超大代码攻击</li>
 *   <li>字符串/注释预处理：避免误判注释或字符串字面量中的危险词</li>
 *   <li>三层扫描：模块导入、属性方法调用、内置函数调用</li>
 * </ol>
 * </p>
 *
 * <p>注意：静态分析只能拦截已知的危险模式，无法完全防御高度混淆的攻击。
 * 本校验器应与 Docker 容器隔离、内存限制等纵深防御措施配合使用。</p>
 */
@Slf4j
@Component
public class PythonCodeValidator {

    // ==================== 默认黑名单 ====================

    /**
     * 默认禁用的危险模块（顶级模块名）。
     * 这些模块可直接破坏文件系统或执行任意命令。
     */
    static final Set<String> DEFAULT_BLOCKED_MODULES = Set.of(
            // 文件系统破坏
            "shutil",
            // 子进程与命令执行
            "subprocess",
            // 系统底层调用
            "ctypes", "cffi",
            // 网络底层
            "socket", "selectors",
            // 进程与权限
            "fcntl", "pty", "pwd", "grp", "spwd", "resource"
    );

    /**
     * 默认禁用的危险内置函数。
     * 这些函数可以执行任意代码或动态加载任意模块。
     */
    static final Set<String> DEFAULT_BLOCKED_FUNCTIONS = Set.of(
            "__import__",
            "exec", "eval", "compile"
    );

    /**
     * 默认禁用的危险方法调用（module.func 形式）。
     * 仅列出真正具有破坏性的方法调用，不影响日常使用。
     */
    static final Set<String> DEFAULT_BLOCKED_CALLS = Set.of(
            // os 子进程 / 命令执行
            "os.system", "os.popen", "os.execv", "os.execvp",
            "os.execve", "os.execvpe", "os.execl", "os.execle",
            "os.execlp", "os.execlpe", "os.fork", "os.forkpty",
            "os.spawnl", "os.spawnle", "os.spawnlp", "os.spawnlpe",
            "os.spawnv", "os.spawnve", "os.spawnvp", "os.spawnvpe",
            "os.posix_spawn", "os.startfile",
            // os 文件删除
            "os.remove", "os.unlink", "os.rmdir", "os.removedirs",
            "os.rename", "os.replace",
            // shutil 破坏性
            "shutil.rmtree", "shutil.move",
            "shutil.copy", "shutil.copy2", "shutil.copyfile",
            "shutil.copymode", "shutil.copystat", "shutil.copytree",
            // subprocess
            "subprocess.run", "subprocess.call", "subprocess.Popen",
            "subprocess.check_call", "subprocess.check_output",
            "subprocess.getoutput", "subprocess.getstatusoutput"
    );

    // ==================== 正则模式 ====================

    /**
     * 匹配 import 与 from ... import 语句。
     * group(1) 捕获 "import x" 中的 x，group(2) 捕获 "from x import" 中的 x。
     */
    private static final Pattern IMPORT_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:import\\s+([\\w.]+)|from\\s+([\\w.]+)\\s+import)"
    );

    /**
     * 匹配属性调用 a.b.c()。要求至少一个属性点。
     * group(1) 捕获完整的属性链。
     */
    private static final Pattern ATTR_CALL_PATTERN = Pattern.compile(
            "(?<![\\w.])([a-zA-Z_][\\w]*(?:\\.[a-zA-Z_][\\w]*)+)\\s*\\("
    );

    /**
     * 匹配简单函数调用 func()。
     * 前置负向预查 (?<![\w.]) 避免匹配属性调用中的方法名。
     */
    private static final Pattern FUNC_CALL_PATTERN = Pattern.compile(
            "(?<![\\w.])([a-zA-Z_][\\w]*)\\s*\\("
    );

    /** 三引号字符串字面量（单行或多行） */
    private static final Pattern TRIPLE_STRING_PATTERN = Pattern.compile(
            "(?s)\"\"\".*?\"\"\"|'''.*?'''"
    );

    /** 普通字符串字面量（单行） */
    private static final Pattern STRING_PATTERN = Pattern.compile(
            "\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*'"
    );

    /** 行注释（从 # 到行尾） */
    private static final Pattern COMMENT_PATTERN = Pattern.compile("(?m)#.*$");

    // ==================== 注入 ====================

    private final SandboxConfig config;

    public PythonCodeValidator(SandboxConfig config) {
        this.config = config;
    }

    // ==================== 公共 API ====================

    /**
     * 校验 Python 代码是否安全。
     * 如果不合法，抛出 {@link SecurityException}（由 {@code GlobalExceptionHandler} 转为 403 响应）。
     *
     * @param code 待校验的 Python 源码
     * @throws SecurityException 当检测到危险操作时
     */
    public void validate(String code) {
        if (code == null || code.trim().isEmpty()) {
            return;
        }

        PythonSecurity pyConfig = config.getPythonSecurity();
        if (pyConfig == null || !pyConfig.isEnabled()) {
            log.debug("Python security validation is disabled, skipping");
            return;
        }

        // 1. 长度限制
        int maxLen = pyConfig.getMaxCodeLength();
        if (code.length() > maxLen) {
            log.warn("Blocked Python code: length {} exceeds limit {}", code.length(), maxLen);
            throw new SecurityException(
                    "Python code exceeds maximum length of " + maxLen
                            + " characters [VIOLATION: CODE_TOO_LONG]");
        }

        // 2. 字符串/注释预处理，避免误判
        String sanitized = sanitize(code);

        // 3. 合并默认黑名单与用户追加黑名单
        Set<String> blockedModules = new HashSet<>(DEFAULT_BLOCKED_MODULES);
        if (pyConfig.getExtraBlockedModules() != null) {
            blockedModules.addAll(pyConfig.getExtraBlockedModules());
        }

        Set<String> blockedFunctions = new HashSet<>(DEFAULT_BLOCKED_FUNCTIONS);
        if (pyConfig.getExtraBlockedFunctions() != null) {
            blockedFunctions.addAll(pyConfig.getExtraBlockedFunctions());
        }

        Set<String> blockedCalls = new HashSet<>(DEFAULT_BLOCKED_CALLS);
        if (pyConfig.getExtraBlockedCalls() != null) {
            blockedCalls.addAll(pyConfig.getExtraBlockedCalls());
        }

        // 4. 三层扫描
        checkImports(sanitized, blockedModules);
        checkAttributeCalls(sanitized, blockedCalls);
        checkBuiltinFunctionCalls(sanitized, blockedFunctions);
    }

    // ==================== 内部实现 ====================

    /**
     * 预处理：剥离字符串字面量与注释，避免误判其中包含的危险词。
     */
    private String sanitize(String code) {
        String result = TRIPLE_STRING_PATTERN.matcher(code).replaceAll("\"\"");
        result = STRING_PATTERN.matcher(result).replaceAll("\"\"");
        result = COMMENT_PATTERN.matcher(result).replaceAll("");
        return result;
    }

    /**
     * 扫描危险模块导入：import x / from x import y。
     * 仅校验顶级模块名（如 "shutil.rmtree" 取 "shutil"）。
     */
    private void checkImports(String code, Set<String> blockedModules) {
        Matcher m = IMPORT_PATTERN.matcher(code);
        while (m.find()) {
            String module = m.group(1) != null ? m.group(1) : m.group(2);
            if (module == null || module.isEmpty()) {
                continue;
            }
            String topLevel = module.split("\\.")[0];
            if (blockedModules.contains(topLevel)) {
                log.warn("Blocked dangerous Python import '{}'", module);
                throw new SecurityException(
                        "Importing module '" + topLevel + "' is prohibited"
                                + " [VIOLATION: BLOCKED_MODULE]");
            }
        }
    }

    /**
     * 扫描属性方法调用 a.b.c()。
     * 检查所有可能的连续子链是否在黑名单中，例如：
     * 对于 chain = "os.path.join"，会检查 "os.path"、"path.join"、"os.path.join"。
     */
    private void checkAttributeCalls(String code, Set<String> blockedCalls) {
        Matcher m = ATTR_CALL_PATTERN.matcher(code);
        while (m.find()) {
            String chain = m.group(1);
            if (chain == null || chain.isEmpty()) {
                continue;
            }
            String[] parts = chain.split("\\.");
            // 枚举所有长度 >= 2 的连续子链
            for (int len = 2; len <= parts.length; len++) {
                for (int start = 0; start + len <= parts.length; start++) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = start; k < start + len; k++) {
                        if (sb.length() > 0) sb.append('.');
                        sb.append(parts[k]);
                    }
                    String candidate = sb.toString();
                    if (blockedCalls.contains(candidate)) {
                        log.warn("Blocked dangerous Python call '{}'", candidate);
                        throw new SecurityException(
                                "Calling '" + candidate + "' is prohibited"
                                        + " [VIOLATION: BLOCKED_CALL]");
                    }
                }
            }
        }
    }

    /**
     * 扫描危险内置函数调用：eval()、exec()、__import__() 等。
     * 通过前置负向预查 (?<![\w.]) 避免与属性调用重复触发。
     */
    private void checkBuiltinFunctionCalls(String code, Set<String> blockedFunctions) {
        Matcher m = FUNC_CALL_PATTERN.matcher(code);
        while (m.find()) {
            String funcName = m.group(1);
            if (blockedFunctions.contains(funcName)) {
                log.warn("Blocked dangerous Python function '{}'", funcName);
                throw new SecurityException(
                        "Calling built-in function '" + funcName + "' is prohibited"
                                + " [VIOLATION: BLOCKED_FUNCTION]");
            }
        }
    }
}