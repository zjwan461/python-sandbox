package com.itsu.sandbox.controller;

import com.itsu.sandbox.service.SandboxService;
import com.itsu.sandbox.service.SandboxService.CommandResult;
import com.itsu.sandbox.service.ShellCommandValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final SandboxService sandboxService;
    private final ShellCommandValidator shellCommandValidator;

    // ==================== 会话管理 ====================

    @PostMapping("/session")
    public ResponseEntity<Map<String, String>> createSession() {
        String sessionId = sandboxService.generateSessionId();
        sandboxService.createContainer(sessionId);
        
        Map<String, String> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("message", "Sandbox session created");
        
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Map<String, String>> deleteSession(@PathVariable String sessionId) {
        sandboxService.removeContainer(sessionId);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Sandbox session destroyed");
        
        return ResponseEntity.ok(response);
    }

    // ==================== Python代码执行 ====================

    @PostMapping("/exec/python")
    public ResponseEntity<Map<String, Object>> execPython(@RequestBody PythonExecRequest request) {
        String sessionId = request.getSessionId();
        CommandResult result = sandboxService.runPythonCode(sessionId, request.getCode());
        
        Map<String, Object> response = new HashMap<>();
        response.put("exitCode", result.getExitCode());
        response.put("stdout", result.getStdout());
        response.put("stderr", result.getStderr());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.OK).body(response);
        }
    }

    // ==================== Shell命令执行 ====================

    @PostMapping("/exec/shell")
    public ResponseEntity<Map<String, Object>> execShell(@RequestBody ShellExecRequest request) {
        String sessionId = request.getSessionId();
        String command = request.getCommand();
        
        // 执行安全验证
        shellCommandValidator.validate(command);
        
        CommandResult result = sandboxService.execInContainer(sessionId, "sh", "-c", "'" + command + "'");
        
        Map<String, Object> response = new HashMap<>();
        response.put("exitCode", result.getExitCode());
        response.put("stdout", result.getStdout());
        response.put("stderr", result.getStderr());
        
        return ResponseEntity.ok(response);
    }

    // ==================== pip包管理 ====================

    @PostMapping("/pip/install")
    public ResponseEntity<Map<String, Object>> pipInstall(@RequestBody PipInstallRequest request) {
        String sessionId = request.getSessionId();
        String packageName = request.getPackage();
        
        CommandResult result = sandboxService.pipInstall(sessionId, packageName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exitCode", result.getExitCode());
        response.put("stdout", result.getStdout());
        response.put("stderr", result.getStderr());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @PostMapping("/pip/uninstall")
    public ResponseEntity<Map<String, Object>> pipUninstall(@RequestBody PipInstallRequest request) {
        String sessionId = request.getSessionId();
        String packageName = request.getPackage();
        
        CommandResult result = sandboxService.pipUninstall(sessionId, packageName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("exitCode", result.getExitCode());
        response.put("stdout", result.getStdout());
        response.put("stderr", result.getStderr());
        
        if (result.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
    }

    @GetMapping("/pip/list")
    public ResponseEntity<Map<String, Object>> pipList(@RequestParam String sessionId) {
        CommandResult result = sandboxService.pipList(sessionId);
        
        Map<String, Object> response = new HashMap<>();
        response.put("packages", result.getStdout().trim());
        
        return ResponseEntity.ok(response);
    }

    // ==================== 文件操作 ====================

    @PostMapping("/file/upload")
    public ResponseEntity<Map<String, String>> uploadFile(
            @RequestParam String sessionId,
            @RequestParam String path,
            @RequestParam("file") MultipartFile file) throws IOException {
        
        String actualPath = sandboxService.uploadFile(sessionId, path, file.getBytes(), file.getOriginalFilename());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "File uploaded successfully");
        response.put("path", actualPath);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/file/download")
    public ResponseEntity<byte[]> downloadFile(
            @RequestParam String sessionId,
            @RequestParam String path) {
        
        byte[] content = sandboxService.downloadFile(sessionId, path);
        
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(content.length)
                .body(content);
    }

    @PostMapping("/file/write")
    public ResponseEntity<Map<String, String>> writeFile(
            @RequestBody FileWriteRequest request) {
        
        sandboxService.writeFile(request.getSessionId(), request.getPath(), request.getContent());
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "File written successfully: " + request.getPath());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/file/read")
    public ResponseEntity<Map<String, String>> readFile(
            @RequestParam String sessionId,
            @RequestParam String path) {
        
        String content = sandboxService.readFile(sessionId, path);
        
        Map<String, String> response = new HashMap<>();
        response.put("path", path);
        response.put("content", content);
        
        return ResponseEntity.ok(response);
    }
}
