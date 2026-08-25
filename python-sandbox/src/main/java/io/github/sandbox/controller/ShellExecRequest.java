package io.github.sandbox.controller;

import lombok.Data;

@Data
public class ShellExecRequest {
    private String sessionId;
    private String command;
}
