# 请求链路文档

本文档详细说明 Python Sandbox 服务中一个完整请求的处理流程。

## 系统架构概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Client (SDK/HTTP)                              │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │ HTTP Request
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Spring Boot Application                           │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        GlobalExceptionHandler                       │   │
│  │                    (统一异常处理，返回标准错误格式)                     │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                          SandboxController                          │   │
│  │                    (REST API 入口，路由请求)                          │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                        ShellCommandValidator                        │   │
│  │                    (Shell 命令安全验证，拦截高危命令)                   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                          SandboxService                             │   │
│  │              (核心业务逻辑，会话管理，Docker 操作封装)                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
│                                    │                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                          DockerClient                               │   │
│  │                    (docker-java 客户端，操作 Docker)                  │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────┬───────────────────────────────────────────┘
                                  │ Docker API
                                  ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Docker Daemon                                  │
│  ┌─────────────────────────────────────────────────────────────────────┐   │
│  │                    Sandbox Container (Python 3.12)                  │   │
│  │  ┌─────────────────────────────────────────────────────────────┐   │   │
│  │  │  • Python 解释器                                             │   │   │
│  │  │  • pip 包管理器                                              │   │   │
│  │  │  • 文件系统 (/tmp, /workspace 等)                            │   │   │
│  │  └─────────────────────────────────────────────────────────────┘   │   │
│  └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 应用启动流程

应用启动时，`SandboxService.init()` 方法会执行以下初始化操作：

```
1. 创建 DockerClient
   └─> 配置 Docker 连接（host, cert, tls, api-version）
   └─> 执行 docker ping 验证连接

2. 可选：预拉取镜像
   └─> 如果 pull-image-on-startup=true
   └─> 执行 docker pull 拉取配置的 Python 镜像

3. 可选：创建默认容器
   └─> 如果 create-default-container-on-startup=true
   └─> 创建名为 "default" 的会话容器
```

## 请求处理链路

### 1. 创建会话

**API**: `POST /api/sandbox/session`

```
Client
  │
  ▼
SandboxController.createSession()
  │
  ├─> sandboxService.generateSessionId()
  │   └─> 生成唯一 ID: "session-{timestamp}-{random}"
  │
  ├─> sandboxService.createContainer(sessionId)
  │   │
  │   ├─> 检查会话是否已存在
  │   │   └─> 如果存在，抛出 DUPLICATE_SESSION 异常
  │   │
  │   ├─> 检查容器数量限制
  │   │   ├─> 如果 max-containers-behavior="evict-oldest"
  │   │   │   └─> 驱逐最旧的会话
  │   │   └─> 如果达到上限且 behavior="reject"
  │   │       └─> 抛出 MAX_CONTAINERS_REACHED 异常
  │   │
  │   ├─> 清理同名旧容器（如果存在）
  │   │
  │   ├─> 创建 Docker 容器
  │   │   ├─> 设置容器名称: {prefix}{sessionId}
  │   │   ├─> 设置环境变量: PYTHONUNBUFFERED=1
  │   │   ├─> 启用 TTY 和 stdin/stdout/stderr
  │   │   └─> 设置内存限制（如果配置了 container-memory-limit）
  │   │
  │   ├─> 启动容器
  │   │
  │   └─> 创建 SandboxSession 对象并缓存到 sessions Map
  │
  └─> 返回响应: { sessionId, message }
```

### 2. 执行 Python 代码

**API**: `POST /api/sandbox/exec/python`

```
Client
  │
  ▼
SandboxController.execPython(request)
  │
  ├─> 从 request 获取 sessionId 和 code
  │
  ├─> sandboxService.runPythonCode(sessionId, code)
  │   │
  │   ├─> getSession(sessionId)
  │   │   ├─> 从 sessions Map 获取会话
  │   │   ├─> 检查会话是否存在
  │   │   ├─> 调用 isActive() 检查容器是否运行中
  │   │   │   └─> dockerClient.inspectContainerCmd() 查询容器状态
  │   │   └─> 更新 lastActivity 时间戳
  │   │
  │   ├─> 写入临时 Python 文件
  │   │   ├─> 生成临时文件路径: /tmp/sandbox_{timestamp}.py
  │   │   └─> 调用 writeFile() 将代码写入容器
  │   │
  │   ├─> 执行 Python 脚本
  │   │   └─> execInContainer(sessionId, "python", tmpFile)
  │   │       │
  │   │       ├─> dockerClient.execCreateCmd()
  │   │       │   └─> 创建 exec 实例，附加 stdout/stderr
  │   │       │
  │   │       ├─> dockerClient.execStartCmd()
  │   │       │   └─> 启动执行，通过 ExecOutputCallback 收集输出
  │   │       │       ├─> STDOUT 帧 -> stdoutStream
  │   │       │       └─> STDERR 帧 -> stderrStream
  │   │       │
  │   │       ├─> 等待执行完成（超时 300 秒）
  │   │       │
  │   │       └─> 获取退出码
  │   │           └─> dockerClient.inspectExecCmd() 查询执行状态
  │   │
  │   └─> 清理临时文件
  │       └─> execInContainer(sessionId, "rm", "-f", tmpFile)
  │
  └─> 返回响应: { exitCode, stdout, stderr }
```

### 3. 执行 Shell 命令

**API**: `POST /api/sandbox/exec/shell`

```
Client
  │
  ▼
SandboxController.execShell(request)
  │
  ├─> 从 request 获取 sessionId 和 command
  │
  ├─> shellCommandValidator.validate(command)
  │   │
  │   ├─> 检查命令长度（最大 2048 字符）
  │   │
  │   ├─> 检查基础危险命令
  │   │   └─> shutdown, halt, reboot, poweroff
  │   │
  │   ├─> 按风险等级检查正则模式
  │   │   ├─> CRITICAL: 删除操作、磁盘格式化、磁盘覆盖
  │   │   ├─> HIGH: 网络攻击、挖矿、权限提升、系统配置、进程破坏
  │   │   ├─> MEDIUM: 敏感文件访问、数据外传、危险管道、端口扫描
  │   │   └─> LOW: 系统包管理（apt, yum 等）
  │   │
  │   └─> 如果验证失败，抛出 SecurityException
  │
  ├─> sandboxService.execInContainer(sessionId, "sh", "-c", command)
  │   │
  │   └─> (同 Python 执行流程中的 execInContainer)
  │
  └─> 返回响应: { exitCode, stdout, stderr }
```

### 4. pip 包管理

**API**: 
- `POST /api/sandbox/pip/install`
- `POST /api/sandbox/pip/uninstall`
- `GET /api/sandbox/pip/list`

```
Client
  │
  ▼
SandboxController.pipInstall/pipUninstall/pipList(request)
  │
  ├─> 从 request 获取 sessionId 和 packageName
  │
  ├─> sandboxService.pipInstall(sessionId, packageName)
  │   └─> execInContainer(sessionId, "pip", "install", packageName)
  │
  ├─> sandboxService.pipUninstall(sessionId, packageName)
  │   └─> execInContainer(sessionId, "pip", "uninstall", "-y", packageName)
  │
  ├─> sandboxService.pipList(sessionId)
  │   └─> execInContainer(sessionId, "pip", "list", "--format=freeze")
  │
  └─> 返回响应: { exitCode, stdout, stderr } 或 { packages }
```

### 5. 文件操作

#### 5.1 写入文本文件

**API**: `POST /api/sandbox/file/write`

```
Client
  │
  ▼
SandboxController.writeFile(request)
  │
  ├─> sandboxService.writeFile(sessionId, path, content)
  │   │
  │   ├─> getSession(sessionId)
  │   │
  │   ├─> 将内容写入宿主机临时文件
  │   │   └─> writeToTempFile(content.getBytes())
  │   │
  │   ├─> 复制文件到容器
  │   │   └─> dockerClient.copyArchiveToContainerCmd()
  │   │       ├─> withHostResource(tmpFile)
  │   │       └─> withRemotePath(dir)
  │   │
  │   ├─> 删除宿主机临时文件
  │   │
  │   └─> 如果文件名不匹配，重命名
  │       └─> execInContainer("sh", "-c", "mv tmpFile targetPath")
  │
  └─> 返回响应: { message }
```

#### 5.2 读取文本文件

**API**: `GET /api/sandbox/file/read`

```
Client
  │
  ▼
SandboxController.readFile(sessionId, path)
  │
  ├─> sandboxService.readFile(sessionId, path)
  │   │
  │   ├─> downloadFile(sessionId, path)
  │   │   │
  │   │   ├─> getSession(sessionId)
  │   │   │
  │   │   ├─> 从容器复制文件
  │   │   │   └─> dockerClient.copyArchiveFromContainerCmd()
  │   │   │
  │   │   └─> 解析 tar 流提取文件内容
  │   │       └─> readTarEntry(inputStream)
  │   │
  │   └─> 将字节转换为字符串 (UTF-8)
  │
  └─> 返回响应: { path, content }
```

#### 5.3 上传文件

**API**: `POST /api/sandbox/file/upload`

```
Client (multipart/form-data)
  │
  ▼
SandboxController.uploadFile(sessionId, path, file)
  │
  ├─> sandboxService.uploadFile(sessionId, path, file.getBytes(), originalFilename)
  │   │
  │   ├─> 确定容器内实际路径
  │   │   ├─> 如果 path 以 "/" 结尾: path + originalFilename
  │   │   ├─> 如果 path 包含 ".": 视为完整路径
  │   │   └─> 否则: path + "/" + originalFilename
  │   │
  │   └─> (后续流程同 writeFile)
  │
  └─> 返回响应: { message, path }
```

#### 5.4 下载文件

**API**: `GET /api/sandbox/file/download`

```
Client
  │
  ▼
SandboxController.downloadFile(sessionId, path)
  │
  ├─> sandboxService.downloadFile(sessionId, path)
  │   └─> (同 readFile 中的 downloadFile)
  │
  ├─> 提取文件名
  │
  └─> 返回二进制响应
      ├─> Content-Disposition: attachment; filename="..."
      ├─> Content-Type: application/octet-stream
      └─> Body: 文件内容
```

### 6. 删除会话

**API**: `DELETE /api/sandbox/session/{sessionId}`

```
Client
  │
  ▼
SandboxController.deleteSession(sessionId)
  │
  ├─> sandboxService.removeContainer(sessionId)
  │   │
  │   ├─> 从 sessions Map 移除会话
  │   │
  │   ├─> 停止容器
  │   │   └─> dockerClient.killContainerCmd()
  │   │
  │   └─> 删除容器
  │       └─> dockerClient.removeContainerCmd().withForce(true)
  │
  └─> 返回响应: { message }
```

## 会话管理机制

### 会话缓存

```java
private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();
```

- 使用 `ConcurrentHashMap` 保证线程安全
- 键: sessionId
- 值: SandboxSession 对象（包含 sessionId, containerId, name, lastActivity）

### 会话超时清理

```
@Scheduled(fixedRateString = "${sandbox.session-cleanup-interval-millis:3600000}")
cleanUpExpiredSessions()
  │
  ├─> 遍历所有会话
  │
  ├─> 检查是否超时
  │   └─> now > lastActivity + sessionTimeoutMillis
  │
  ├─> 跳过默认会话（"default"）
  │
  └─> 清理超时会话
      ├─> killContainerCmd()
      └─> removeContainerCmd()
```

### 容器数量限制

```
max-containers: 10 (默认)
max-containers-behavior: reject | evict-oldest

当达到上限时:
  ├─> reject: 抛出 MAX_CONTAINERS_REACHED 异常
  └─> evict-oldest: 驱逐 lastActivity 最旧的会话
```

## 异常处理流程

```
异常发生
  │
  ▼
GlobalExceptionHandler
  │
  ├─> SandboxException
  │   └─> HTTP 400: { error: errorCode, message: ... }
  │
  ├─> SecurityException
  │   └─> HTTP 403: { error: "SECURITY_VIOLATION", message: ... }
  │
  ├─> MethodArgumentNotValidException
  │   └─> HTTP 400: { error: "INVALID_PARAMETER", message: ... }
  │
  ├─> MissingServletRequestParameterException
  │   └─> HTTP 400: { error: "MISSING_PARAMETER", message: ... }
  │
  ├─> HttpMessageNotReadableException
  │   └─> HTTP 400: { error: "INVALID_REQUEST_BODY", message: ... }
  │
  └─> Exception (未知异常)
      └─> HTTP 500: { error: "INTERNAL_ERROR", message: "Internal server error" }
```

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| DOCKER_CONNECT_ERROR | 无法连接到 Docker 守护进程 |
| DOCKER_ERROR | Docker 操作失败 |
| DUPLICATE_SESSION | 会话 ID 已存在 |
| MAX_CONTAINERS_REACHED | 达到容器数量上限 |
| SESSION_NOT_FOUND | 会话不存在或已过期 |
| SESSION_EXPIRED | 会话容器已停止运行 |
| FILE_WRITE_ERROR | 文件写入失败 |
| FILE_READ_ERROR | 文件读取失败 |
| INTERRUPTED | 命令执行被中断 |
| SECURITY_VIOLATION | Shell 命令安全验证失败 |
| COMMAND_TOO_LONG | 命令长度超过限制 |

## 健康检查

**API**: `GET /health`

```
Client
  │
  ▼
HealthController.health()
  │
  ├─> sandboxService.getActiveCount()
  │   └─> 返回 sessions.size()
  │
  └─> 返回响应: { status: "UP", activeContainers: N }
```

## 关键配置项

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| sandbox.image | python:3.12-trixie | 沙箱容器使用的镜像 |
| sandbox.container-name-prefix | python-sandbox- | 容器名称前缀 |
| sandbox.session-timeout-millis | 86400000 (24h) | 会话超时时间 |
| sandbox.session-cleanup-interval-millis | 3600000 (1h) | 清理检查间隔 |
| sandbox.max-containers | 10 | 最大容器数量 |
| sandbox.max-containers-behavior | reject | 超限行为 |
| sandbox.container-memory-limit | 536870912 (512MB) | 容器内存限制 |
| sandbox.pull-image-on-startup | false | 启动时预拉取镜像 |
| sandbox.create-default-container-on-startup | true | 启动时创建默认容器 |
| sandbox.docker-host | unix:///var/run/docker.sock | Docker 连接地址 |
