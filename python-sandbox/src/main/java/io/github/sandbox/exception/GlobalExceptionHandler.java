package io.github.sandbox.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

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
    
    // ==================== 参数校验异常 -> 400 ====================
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INVALID_PARAMETER");
        response.put("message", message);
        return response;
    }
    
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingServletRequestParameterException(MissingServletRequestParameterException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "MISSING_PARAMETER");
        response.put("message", "Missing required parameter: " + e.getParameterName());
        return response;
    }
    
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMissingServletRequestPartException(MissingServletRequestPartException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "MISSING_PARAMETER");
        response.put("message", "Missing required part: " + e.getRequestPartName());
        return response;
    }
    
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleHttpMessageNotReadableException(HttpMessageNotReadableException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INVALID_REQUEST_BODY");
        response.put("message", "Request body is malformed or unreadable");
        return response;
    }
    
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleMethodArgumentTypeMismatchException(MethodArgumentTypeMismatchException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INVALID_PARAMETER");
        response.put("message", "Parameter '" + e.getName() + "' has invalid type");
        return response;
    }
    
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleIllegalArgumentException(IllegalArgumentException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "INVALID_PARAMETER");
        response.put("message", e.getMessage());
        return response;
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Map<String, Object> handle404(NoResourceFoundException e) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "NOT_FOUND");
        response.put("message", e.getMessage());
        return response;
    }
    
    // ==================== 未知异常 -> 500 ====================
    
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
