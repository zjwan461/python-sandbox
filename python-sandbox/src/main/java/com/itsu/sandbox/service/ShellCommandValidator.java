package com.itsu.sandbox.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shell命令安全验证器
 * 用于防止执行高危命令
 */
@Slf4j
@Component
public class ShellCommandValidator {

    /**
     * 高危命令列表
     * 按照风险等级分类：
     * CRITICAL - 致命级别（直接拒绝）
     * HIGH - 高风险级别（需要特殊权限）
     * MEDIUM - 中等风险（记录日志）
     */
    
    // ==================== 致命级别命令 ====================
    
    /** 删除系统文件/目录 */
    private static final List<Pattern> DELETE_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*rm\\s+(-[a-zA-Z]*\\s+)*-[rfRv]+\\s+(-[a-zA-Z]*\\s+)*-[fRv]+.*\\s+/\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+(-[a-zA-Z]*\\s+)*-[rfRv]+\\s+(-[a-zA-Z]*\\s+)*-[fRv]+\\s+/.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/home", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/var", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/etc", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/usr", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/root", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*rm\\s+-rf\\s+/(?!tmp)"),  // 除了/tmp外禁止删除根目录下任何东西
            Pattern.compile("^\\s*rm\\s+-r\\s+/\\.\\.")  // 禁止删除上级目录
    );

    /** 格式化磁盘/分区 */
    private static final List<Pattern> FORMAT_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*(mkfs|mkfs\\..*)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*fdisk\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*parted\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*dd\\s+.*of=/dev/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*shred\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*wipe\\s+", Pattern.CASE_INSENSITIVE)
    );

    /** 覆盖磁盘内容 */
    private static final List<Pattern> OVERWRITE_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*>\\s*/dev/(sd|hd|nvme)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*truncate\\s+.*--size=0\\s+/dev/", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 高风险命令 ====================
    
    /** 网络攻击相关 */
    private static final List<Pattern> NETWORK_ATTACK_PATTERNS = Arrays.asList(
            Pattern.compile("\\b(nc|ncat|netcat)\\s+.*(-e|-c)\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(socat)\\s+.*exec:\"", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(nmap)\\s+.*-s[SPTU]\\s", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(hydra)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(metasploit|msfconsole)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(burpsuite)\\s+", Pattern.CASE_INSENSITIVE)
    );

    /** 挖矿程序 */
    private static final List<Pattern> MINING_PATTERNS = Arrays.asList(
            Pattern.compile("\\b(xmrig|minerd|cgminer|bfgminer|ethminer|stratum+)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(cryptonight|randomx|aesproxy)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(hashrate|pool\\.stratum|wallet)\\s*=", Pattern.CASE_INSENSITIVE),
            Pattern.compile("https?://[^\\s]*([mM]ining|[pP]ool|[wW]allet)", Pattern.CASE_INSENSITIVE)
    );

    /** 权限提升 */
    private static final List<Pattern> PRIVILEGE_ESCALATION_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*sudo\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*su\\s+-", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*passwd\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(chmod\\s+[0-7]?777\\s+)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*chattr\\s+[-icjsaADdIiSs]\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(setuid|setgid)\\s+", Pattern.CASE_INSENSITIVE)
    );

    /** 修改关键系统配置 */
    private static final List<Pattern> SYSTEM_CONFIG_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*(sysctl)\\s+.*(kernel\\.|panic|reboot)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(iptables|ip6tables|nft)\\s+(-flush|-F)\\s*$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(iptables|ip6tables|nft)\\s+.*(-j DROP|DROP)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(systemctl)\\s+(stop|restart)\\s+(docker|sshd|network)\\s*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(echo\\s+.+\\s*>\\s*/proc/sys/)", Pattern.CASE_INSENSITIVE)
    );

    /** 进程破坏 */
    private static final List<Pattern> PROCESS_KILL_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*kill\\s+-9\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*killall\\s+(-9|--signal\\s+9)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*nohup\\s+kill\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(taskkill)\\s+.*(/F)\\s+", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 数据泄露相关 ====================
    
    /** 访问敏感文件 */
    private static final List<Pattern> SENSITIVE_FILE_PATTERNS = Arrays.asList(
            Pattern.compile("/etc/shadow\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/etc/gshadow\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(cat|more|less|tail|head)\\s+/etc/shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(grep)\\s+.*password\\s+/etc/shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/root/\\.ssh/", Pattern.CASE_INSENSITIVE),
            Pattern.compile("/root/\\.bash_history", Pattern.CASE_INSENSITIVE)
    );

    /** 数据外传 */
    private static final List<Pattern> DATA_EXFIL_PATTERNS = Arrays.asList(
            Pattern.compile("(curl|wget)\\s+.*--data.*-/etc/shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(curl|wget)\\s+.*--data.*-/etc/passwd", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(curl|wget)\\s+.*@.*--data", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bscp\\s+.*:/etc/shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bftp\\s+.*put\\s+/etc/shadow", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bpscp\\s+.*:/etc/shadow", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 混合执行相关 ====================
    
    /** 危险的管道和重定向组合 */
    private static final List<Pattern> DANGEROUS_PIPE_PATTERNS = Arrays.asList(
            Pattern.compile("(wget|curl)\\s+[^|]*\\|\\s*(sh|bash)\\b", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(wget|curl)\\s+[^|]*\\|\\s*/bin/(sh|bash)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("((apt|yum|dnf|brew)\\s+.*\\|\\s*sh)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\b(eval)\\s+\\$", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\beval\\s+.", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 端口扫描 ====================
    
    private static final List<Pattern> PORT_SCAN_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*(masscan|zmap)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bsocat\\s+TCP:[^:]+:\\d+", Pattern.CASE_INSENSITIVE)
    );

    // ==================== 系统包管理（沙箱内应使用 pip，不应修改系统包） ====================

    private static final List<Pattern> SYSTEM_PACKAGE_PATTERNS = Arrays.asList(
            Pattern.compile("^\\s*(apt|apt-get)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(yum|dnf|rpm|dpkg)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(apk)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(pacman|yaourt)\\s+", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^\\s*(brew|snap)\\s+", Pattern.CASE_INSENSITIVE)
    );

    /**
     * 验证shell命令是否安全
     * @param command 待验证的命令
     * @return CommandValidationResult 包含验证结果和拒绝原因
     */
    public CommandValidationResult validate(String command) {
        if (command == null || command.trim().isEmpty()) {
            return new CommandValidationResult(true, null, null);
        }

        String trimmedCommand = command.trim();
        
        // 1. 基本长度限制（防止超长字符串攻击）
        if (trimmedCommand.length() > 2048) {
            return new CommandValidationResult(false, "COMMAND_TOO_LONG", 
                    "Command exceeds maximum length of 2048 characters");
        }

        // 2. 检查基本危险命令（系统电源管理）
        String[] basicDangerousCommands = {
                "shutdown", "halt", "reboot", "poweroff"
        };
        for (String cmd : basicDangerousCommands) {
            // 使用 find() 而非 matches()，matches() 要求整个字符串完全匹配正则，
            // 而 "shutdown -h now" 在 "shutdown\s" 之后还有内容，matches() 永远返回 false
            Matcher matcher = Pattern.compile(
                    "^\\s*" + cmd + "(\\s|$)", Pattern.CASE_INSENSITIVE
            ).matcher(trimmedCommand);
            if (matcher.find()) {
                log.warn("Blocked dangerous command (DANGEROUS_COMMAND): {}", trimmedCommand);
                throw new SecurityException(
                        "System power management commands are not allowed: " + cmd
                                + " [VIOLATION: DANGEROUS_COMMAND]");
            }
        }
        
        // 3. 按风险等级检查正则模式
        
        // 致命级别
        checkPatterns(trimmedCommand, DELETE_PATTERNS, "DELETE_OPERATIONS",
                "File system destruction operations are strictly prohibited");
        checkPatterns(trimmedCommand, FORMAT_PATTERNS, "DISK_FORMAT",
                "Disk formatting/partition operations are prohibited");
        checkPatterns(trimmedCommand, OVERWRITE_PATTERNS, "DISK_OVERWRITE",
                "Disk overwrite operations are prohibited");
                
        // 高风险级别
        checkPatterns(trimmedCommand, NETWORK_ATTACK_PATTERNS, "NETWORK_ATTACK",
                "Network attack tools are prohibited");
        checkPatterns(trimmedCommand, MINING_PATTERNS, "CRYPTO_MINING",
                "Cryptocurrency mining is prohibited");
        checkPatterns(trimmedCommand, PRIVILEGE_ESCALATION_PATTERNS, "PRIVILEGE_ESCALATION",
                "Privilege escalation attempts are prohibited");
        checkPatterns(trimmedCommand, SYSTEM_CONFIG_PATTERNS, "SYSTEM_CONFIG",
                "Critical system configuration changes are prohibited");
        checkPatterns(trimmedCommand, PROCESS_KILL_PATTERNS, "PROCESS_KILL",
                "Arbitrary process termination is prohibited");
        checkPatterns(trimmedCommand, SENSITIVE_FILE_PATTERNS, "SENSITIVE_FILE_ACCESS",
                "Access to sensitive files is prohibited");
        checkPatterns(trimmedCommand, DATA_EXFIL_PATTERNS, "DATA_EXFILTRATION",
                "Data exfiltration attempts are prohibited");
        checkPatterns(trimmedCommand, DANGEROUS_PIPE_PATTERNS, "DANGEROUS_PIPE",
                "Remote code execution via pipe is prohibited");
        checkPatterns(trimmedCommand, PORT_SCAN_PATTERNS, "PORT_SCAN",
                "Port scanning tools are prohibited");
        checkPatterns(trimmedCommand, SYSTEM_PACKAGE_PATTERNS, "SYSTEM_PACKAGE",
                "System package management commands are prohibited, use pip instead");
        
        return new CommandValidationResult(true, null, null);
    }
    
    /**
     * 检查命令是否匹配给定的危险模式
     */
    private void checkPatterns(String command, List<Pattern> patterns, String violationType, String message) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(command).find()) {
                log.warn("Blocked dangerous command ({}): {}", violationType, command);
                throw new SecurityException(message + " [VIOLATION: " + violationType + "]");
            }
        }
    }
    
    /**
     * 验证结果类
     */
    public static class CommandValidationResult {
        private final boolean safe;
        private final String violationType;
        private final String reason;

        public CommandValidationResult(boolean safe, String violationType, String reason) {
            this.safe = safe;
            this.violationType = violationType;
            this.reason = reason;
        }

        public boolean isSafe() {
            return safe;
        }

        public String getViolationType() {
            return violationType;
        }

        public String getReason() {
            return reason;
        }
    }
}
