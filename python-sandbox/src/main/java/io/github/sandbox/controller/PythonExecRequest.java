package io.github.sandbox.controller;

import lombok.Data;

@Data
public class PythonExecRequest {
    private String sessionId;
    private String code;
}
