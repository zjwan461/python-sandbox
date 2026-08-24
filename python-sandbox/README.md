# Python Sandbox

基于 Docker 容器化技术构建的 Python 虚拟环境沙箱，使用 Java 17 + Spring Boot 3 提供 HTTP API。

## 功能特性

- **Python代码执行**：在隔离的 Docker 容器中运行 Python 代码
- **Shell命令执行**：在沙箱中执行 shell 命令（内置黑名单防护）
- **pip包管理**：安装、卸载 Python 包
- **文件操作**：上传、下载、写入沙箱中的文件
- **API Key认证**：所有接口（除健康检查外）都需要 X-Api-Key 请求头
- **自动清理**：容器重启后自动清空所有数据和依赖包

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Docker (通过 docker CLI 管理)
- python:3.12-trixie (沙箱基础镜像)

## 快速开始

### 1. 环境变量配置

创建 `.env` 文件：

```bash
SANDBOX_API_KEY=your-secret-api-key-here
```

### 2. 构建并启动

```bash
# 使用 docker-compose 启动
docker-compose up -d --build

# 或直接使用 Maven 本地运行
cd src/main
mvn spring-boot:run
```

### 3. 验证服务

```bash
curl http://localhost:8080/health
```

## API 文档

### 认证说明

除了 `/health` 端点外，所有 API 都需要在请求头中包含 `X-Api-Key`：

```
X-Api-Key: your-secret-api-key-here
```

### 1. 健康检查

```
GET /api/sandbox/health
```

响应示例：
```json
{
  "status": "UP",
  "activeContainers": 0
}
```

### 2. 创建沙箱会话

```
POST /api/sandbox/session
Headers: X-Api-Key: your-key
```

响应示例：
```json
{
  "sessionId": "session-1234567890-abc1234",
  "message": "Sandbox session created"
}
```

### 3. 删除沙箱会话

```
DELETE /api/sandbox/session/{sessionId}
Headers: X-Api-Key: your-key
```

### 4. 执行Python代码

```
POST /api/sandbox/exec/python
Content-Type: application/json
Headers: X-Api-Key: your-key

{
  "sessionId": "session-xxx",
  "code": "print('Hello, World!')\nimport json\nprint(json.dumps({'status': 'ok'}))"
}
```

响应示例：
```json
{
  "exitCode": 0,
  "stdout": "Hello, World!\n{\"status\": \"ok\"}\n",
  "stderr": ""
}
```

### 5. 执行Shell命令

```
POST /api/sandbox/exec/shell
Content-Type: application/json
Headers: X-Api-Key: your-key

{
  "sessionId": "session-xxx",
  "command": "ls -la && pwd"
}
```

### 6. pip安装包

```
POST /api/sandbox/pip/install
Content-Type: application/json
Headers: X-Api-Key: your-key

{
  "sessionId": "session-xxx",
  "package": "requests"
}
```

### 7. pip卸载包

```
POST /api/sandbox/pip/uninstall
Content-Type: application/json
Headers: X-Api-Key: your-key

{
  "sessionId": "session-xxx",
  "package": "requests"
}
```

### 8. pip列出已安装包

```
GET /api/sandbox/pip/list?sessionId=session-xxx
Headers: X-Api-Key: your-key
```

### 9. 上传文件

```
POST /api/sandbox/file/upload
Content-Type: multipart/form-data
Headers: X-Api-Key: your-key

FormData:
- sessionId: session-xxx
- path: /root/uploads/test.txt
- file: [binary data]
```

### 10. 下载文件

```
GET /api/sandbox/file/download?sessionId=session-xxx&path=/root/uploads/test.txt
Headers: X-Api-Key: your-key
```

### 11. 写入文件

```
POST /api/sandbox/file/write
Content-Type: application/json
Headers: X-Api-Key: your-key

{
  "sessionId": "session-xxx",
  "path": "/root/data/output.txt",
  "content": "Hello from sandbox!"
}
```

### 12. 读取文件

```
GET /api/sandbox/file/read?sessionId=session-xxx&path=/root/data/output.txt
Headers: X-Api-Key: your-key
```

## 项目结构

```
python-sandbox/
├── pom.xml
├── Dockerfile
├── docker-compose.yml
├── .env.example
└── src/
    └── main/
        ├── java/
        │   └── com/example/sandbox/
        │       ├── PythonSandboxApplication.java
        │       ├── config/
        │       │   ├── SandboxConfig.java
        │       │   └── WebConfig.java
        │       ├── controller/
        │       │   ├── SandboxController.java
        │       │   ├── PythonExecRequest.java
        │       │   ├── ShellExecRequest.java
        │       │   ├── PipInstallRequest.java
        │       │   └── FileWriteRequest.java
        │       ├── exception/
        │       │   ├── SandboxException.java
        │       │   └── GlobalExceptionHandler.java
        │       └── service/
        │           └── SandboxService.java
        └── resources/
            └── application.properties
```

## Shell 命令黑名单

本系统内置了完善的 Shell 命令安全防护机制，以下类型命令将被自动拦截：

| 风险等级 | 类别 | 示例命令 |
|---------|------|---------|
| 🔴 致命 | 文件系统破坏 | `rm -rf /`, `rm -rf /*` (除/tmp外) |
| 🔴 致命 | 磁盘格式化 | `mkfs`, `fdisk`, `dd if=... of=/dev/` |
| 🔴 致命 | 磁盘覆盖 | `> /dev/sda`, `truncate --size=0 /dev/` |
| 🟠 高危 | 网络攻击工具 | `nc -e`, `socat exec:""`, `nmap -sS` |
| 🟠 高危 | 挖矿程序 | `xmrig`, `minerd`, `stratum+` |
| 🟠 高危 | 权限提升 | `sudo`, `chmod 777`, `chattr -i` |
| 🟠 高危 | 系统配置修改 | `sysctl kernel.panic`, `iptables -F` |
| 🟠 高危 | 进程杀死 | `kill -9`, `killall -9` |
| 🟠 高危 | 敏感文件访问 | `/etc/shadow`, `/root/.ssh/` |
| 🟠 高危 | 数据外传 | `curl --data @/etc/shadow` |
| 🟡 中等 | 危险管道执行 | `curl ... \| sh`, `eval $()` |
| 🟡 中等 | 端口扫描 | `masscan`, `zmap` |
| 🟢 基础 | 系统关机 | `shutdown`, `reboot`, `halt` |

## 安全注意事项

1. **API Key 保护**：请妥善保管 `SANDBOX_API_KEY`，不要暴露在公共仓库中
2. **网络隔离**：建议将沙箱服务部署在内部网络，不要直接暴露到公网
3. **资源限制**：生产环境建议配置 Docker 容器的 CPU 和内存限制
4. **额外防护**：如需更严格的控制，可进一步扩展 `ShellCommandValidator` 类中的规则

## 常见问题

### Q: 容器重启后数据会丢失吗？
A: 是的，每次重启容器都会清空所有 Python 包和上传的文件。这是设计如此。

### Q: 如何修改默认端口？
A: 在 `application.properties` 中修改 `server.port` 配置项。

### Q: 如何自定义 API Key？
A: 通过环境变量 `SANDBOX_API_KEY` 或 `application.properties` 中的 `sandbox.api-key` 配置。

## License

MIT
