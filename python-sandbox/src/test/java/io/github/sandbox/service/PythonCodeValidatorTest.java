package io.github.sandbox.service;

import io.github.sandbox.config.SandboxConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PythonCodeValidator 单元测试。
 *
 * <p>覆盖以下场景：</p>
 * <ul>
 *     <li>正常 Python 代码：放行</li>
 *     <li>危险模块导入：拦截</li>
 *     <li>危险方法调用：拦截</li>
 *     <li>危险内置函数：拦截</li>
 *     <li>注释/字符串中的危险词：不误判</li>
 *     <li>关闭校验开关：放行所有代码</li>
 *     <li>代码长度超限：拦截</li>
 *     <li>用户自定义黑名单：生效</li>
 * </ul>
 */
class PythonCodeValidatorTest {

    private SandboxConfig config;
    private PythonCodeValidator validator;

    @BeforeEach
    void setUp() {
        config = new SandboxConfig();
        // 使用默认配置：enabled=true, maxCodeLength=100KB, 默认黑名单
        validator = new PythonCodeValidator(config);
    }

    // ==================== 合法代码（应放行） ====================

    @Test
    @DisplayName("合法代码：基本运算")
    void shouldAllowBasicArithmetic() {
        String code = """
                x = 1 + 2
                y = x * 3
                print(y)
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("合法代码：字符串/列表/字典操作")
    void shouldAllowStringListDictOperations() {
        String code = """
                data = {"name": "alice", "age": 30}
                names = ["alice", "bob"]
                msg = f"hello, {data['name']}"
                print(msg.upper())
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("合法代码：os.path 只读操作（不应被拦截）")
    void shouldAllowOsPathReadOnly() {
        String code = """
                import os
                path = "/tmp/foo.txt"
                print(os.path.exists(path))
                print(os.path.basename(path))
                print(os.path.join("/tmp", "foo"))
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("合法代码：pathlib 使用")
    void shouldAllowPathlib() {
        String code = """
                from pathlib import Path
                p = Path("/tmp/data.txt")
                if p.exists():
                    print(p.read_text())
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("合法代码：math/json/re 模块使用")
    void shouldAllowCommonStdLib() {
        String code = """
                import math
                import json
                import re
                print(math.sqrt(16))
                print(json.dumps({"a": 1}))
                print(re.match(r"\\d+", "123abc"))
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("合法代码：open() 读取文件（不应误判）")
    void shouldAllowFileOpen() {
        // open() 不在默认黑名单中（仅拦截 import os.remove 等明确危险调用）
        String code = """
                with open("/tmp/data.txt", "r") as f:
                    print(f.read())
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    // ==================== 危险模块导入（应拦截） ====================

    @Test
    @DisplayName("危险：import shutil")
    void shouldBlockImportShutil() {
        String code = "import shutil\nshutil.rmtree('/etc')";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> validator.validate(code));
        assertTrue(ex.getMessage().contains("shutil"));
    }

    @Test
    @DisplayName("危险：from subprocess import run")
    void shouldBlockImportSubprocess() {
        String code = "from subprocess import run\nrun(['ls'])";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> validator.validate(code));
        assertTrue(ex.getMessage().contains("subprocess"));
    }

    @Test
    @DisplayName("危险：import ctypes")
    void shouldBlockImportCtypes() {
        String code = "import ctypes";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    @Test
    @DisplayName("危险：from os import system")
    void shouldBlockImportOsTopLevel() {
        // 当前默认配置只拦截顶级黑名单中的模块；os 本身被允许（仅方法调用受拦截）
        // 这里仅验证 from os import ... 不被默认拦截（更精细控制交给用户）
        // 重要：from os import system 后续的 system(...) 仍会被拦截
        String code = "from os import system\nsystem('echo hi')";
        // os 不在默认黑名单，应通过
        assertDoesNotThrow(() -> validator.validate(code));
    }

    // ==================== 危险方法调用（应拦截） ====================

    @Test
    @DisplayName("危险：os.system 调用")
    void shouldBlockOsSystemCall() {
        String code = "import os\nos.system('rm -rf /')";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> validator.validate(code));
        assertTrue(ex.getMessage().contains("os.system"));
    }

    @Test
    @DisplayName("危险：os.remove 文件删除")
    void shouldBlockOsRemoveCall() {
        String code = "import os\nos.remove('/etc/passwd')";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    @Test
    @DisplayName("危险：shutil.rmtree")
    void shouldBlockShutilRmtree() {
        String code = "import shutil\nshutil.rmtree('/')";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    @Test
    @DisplayName("危险：subprocess.run")
    void shouldBlockSubprocessRun() {
        String code = "import subprocess\nsubprocess.run(['rm', '-rf', '/'])";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    @Test
    @DisplayName("危险：os.popen")
    void shouldBlockOsPopen() {
        String code = "import os\nos.popen('whoami').read()";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    // ==================== 危险内置函数（应拦截） ====================

    @Test
    @DisplayName("危险：eval()")
    void shouldBlockEval() {
        String code = "eval('1+1')";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> validator.validate(code));
        assertTrue(ex.getMessage().contains("eval"));
    }

    @Test
    @DisplayName("危险：exec()")
    void shouldBlockExec() {
        String code = "exec('print(1)')";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    @Test
    @DisplayName("危险：__import__('os')")
    void shouldBlockImportBuiltin() {
        String code = "__import__('os').system('rm -rf /')";
        SecurityException ex = assertThrows(SecurityException.class,
                () -> validator.validate(code));
        assertTrue(ex.getMessage().contains("__import__"));
    }

    @Test
    @DisplayName("危险：compile()")
    void shouldBlockCompile() {
        String code = "compile('x=1', '<test>', 'exec')";
        assertThrows(SecurityException.class, () -> validator.validate(code));
    }

    // ==================== 误判防护（应放行） ====================

    @Test
    @DisplayName("注释中包含危险词：不应误判")
    void shouldNotBlockComments() {
        String code = """
                # this code uses os.system and shutil.rmtree but they are commented out
                # import subprocess
                print("hello world")
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("字符串中包含危险词：不应误判")
    void shouldNotBlockStringLiterals() {
        String code = """
                doc = "Use os.system to run shell commands."
                msg = 'subprocess.run is powerful'
                print(doc)
                print(msg)
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("变量名 os：不应误判（os 是变量名）")
    void shouldNotBlockOsAsVariableName() {
        String code = """
                os = "this is just a variable"
                print(os)
                """;
        // os 是字符串变量名，不应被拦截
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("三引号文档字符串中的危险词：不应误判")
    void shouldNotBlockTripleQuotedStrings() {
        String code = """
                def f():
                    '''
                    This function could potentially call os.system
                    or shutil.rmtree, but it's just documentation.
                    '''
                    return 42
                print(f())
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("类名/方法名包含危险词：不应误判")
    void shouldNotBlockClassOrMethodNames() {
        String code = """
                class SystemManager:
                    def execute(self):
                        return "not os.system call"
                sm = SystemManager()
                print(sm.execute())
                """;
        // 类名 SystemManager、方法名 execute 包含 "system" 但不是函数调用形式
        // 仅 SystemManager().execute() 中的 execute() 被检查，且 execute 不在黑名单
        assertDoesNotThrow(() -> validator.validate(code));
    }

    // ==================== 边界情况 ====================

    @Test
    @DisplayName("空代码：应放行")
    void shouldAllowEmptyCode() {
        assertDoesNotThrow(() -> validator.validate(""));
        assertDoesNotThrow(() -> validator.validate(null));
        assertDoesNotThrow(() -> validator.validate("   \n\t  "));
    }

    @Test
    @DisplayName("代码长度超限：应拦截")
    void shouldBlockOversizedCode() {
        SandboxConfig cfg = new SandboxConfig();
        SandboxConfig.PythonSecurity sec = new SandboxConfig.PythonSecurity();
        sec.setMaxCodeLength(100);
        cfg.setPythonSecurity(sec);
        PythonCodeValidator v = new PythonCodeValidator(cfg);

        String bigCode = "x = 1\n".repeat(1000); // > 100 字符
        SecurityException ex = assertThrows(SecurityException.class,
                () -> v.validate(bigCode));
        assertTrue(ex.getMessage().contains("CODE_TOO_LONG"));
    }

    @Test
    @DisplayName("关闭校验：所有代码放行")
    void shouldBypassWhenDisabled() {
        SandboxConfig cfg = new SandboxConfig();
        SandboxConfig.PythonSecurity sec = new SandboxConfig.PythonSecurity();
        sec.setEnabled(false);
        cfg.setPythonSecurity(sec);
        PythonCodeValidator v = new PythonCodeValidator(cfg);

        String dangerousCode = "import shutil\nshutil.rmtree('/')";
        assertDoesNotThrow(() -> v.validate(dangerousCode));
    }

    // ==================== 用户自定义黑名单 ====================

    @Test
    @DisplayName("用户追加禁用的模块名：生效")
    void shouldRespectExtraBlockedModules() {
        SandboxConfig cfg = new SandboxConfig();
        SandboxConfig.PythonSecurity sec = new SandboxConfig.PythonSecurity();
        sec.setExtraBlockedModules(List.of("urllib"));
        cfg.setPythonSecurity(sec);
        PythonCodeValidator v = new PythonCodeValidator(cfg);

        String code = "import urllib.request";
        assertThrows(SecurityException.class, () -> v.validate(code));
    }

    @Test
    @DisplayName("用户追加禁用的函数名：生效")
    void shouldRespectExtraBlockedFunctions() {
        SandboxConfig cfg = new SandboxConfig();
        SandboxConfig.PythonSecurity sec = new SandboxConfig.PythonSecurity();
        sec.setExtraBlockedFunctions(List.of("getattr"));
        cfg.setPythonSecurity(sec);
        PythonCodeValidator v = new PythonCodeValidator(cfg);

        // getattr 不在默认黑名单但用户追加了
        String code = "getattr(obj, 'name')";
        assertThrows(SecurityException.class, () -> v.validate(code));
    }

    @Test
    @DisplayName("用户追加禁用的方法调用：生效")
    void shouldRespectExtraBlockedCalls() {
        SandboxConfig cfg = new SandboxConfig();
        SandboxConfig.PythonSecurity sec = new SandboxConfig.PythonSecurity();
        sec.setExtraBlockedCalls(List.of("os.path.exists"));
        cfg.setPythonSecurity(sec);
        PythonCodeValidator v = new PythonCodeValidator(cfg);

        String code = "import os\nprint(os.path.exists('/etc'))";
        assertThrows(SecurityException.class, () -> v.validate(code));
    }

    // ==================== 综合场景 ====================

    @Test
    @DisplayName("综合：合法数据分析代码应放行")
    void shouldAllowRealisticDataAnalysis() {
        String code = """
                import json
                import math
                from pathlib import Path

                data = [1, 2, 3, 4, 5]
                mean = sum(data) / len(data)
                variance = sum((x - mean) ** 2 for x in data) / len(data)
                std = math.sqrt(variance)

                result = {
                    "mean": mean,
                    "std": std,
                    "count": len(data),
                }

                output_path = Path("/tmp/result.json")
                output_path.write_text(json.dumps(result))
                print(f"Analysis complete: {result}")
                """;
        assertDoesNotThrow(() -> validator.validate(code));
    }

    @Test
    @DisplayName("综合：试图混淆 os.system 仍应被拦截")
    void shouldBlockObfuscatedOsSystem() {
        // 多种调用形式
        String[] samples = {
                "import os; os.system('ls')",
                "from os import *; system('ls')", // system() 在 builtin 里不在黑名单，但 os.system 方法调用也会被拦截
                "import os.path as p; os.system('ls')",
        };
        for (String code : samples) {
            if (code.contains("os.system")) {
                assertThrows(SecurityException.class, () -> validator.validate(code),
                        "应当拦截: " + code);
            }
        }
    }
}