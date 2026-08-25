package io.github.sandbox.controller;

import lombok.Data;

@Data
public class PipInstallRequest {
    private String sessionId;
    private String pkg;
    
    public String getPackage() {
        return pkg;
    }
}
