package com.itsu.sandbox.exception;

import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class SandboxException extends RuntimeException {
    
    private final String errorCode;
    
    public SandboxException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    
    public SandboxException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
