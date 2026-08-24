package com.itsu.sandbox.controller;

import lombok.Data;

@Data
public class FileWriteRequest {
    private String sessionId;
    private String path;
    private String content;
}
