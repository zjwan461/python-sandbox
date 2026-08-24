package com.itsu.sandbox.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(SandboxException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleSandboxException(SandboxException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", e.getErrorCode());
        response.put("message", e.getMessage());
        return response;
    }
    
    @ExceptionHandler(SecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Map<String, Object> handleSecurityException(SecurityException e) {
        log.warn("Security violation blocked: {}", e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("error", "SECURITY_VIOLATION");
        response.put("message", e.getMessage());
        return response;
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Map<String, Object> handleException(Exception e) {
        log.error("Unexpected error", e);
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INTERNAL_ERROR");
        response.put("message", "Internal server error");
        return response;
    }
}
