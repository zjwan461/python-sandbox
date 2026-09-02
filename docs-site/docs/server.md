# Python Sandbox

基于 Docker 容器化技术构建的 Python 虚拟环境沙箱，使用 Java 17 + Spring Boot 3 提供 HTTP API。

## 功能特性

- **Python代码执行**：在隔离的 Docker 容器中运行 Python 代码
- **双策略危险代码检测**：执行 Python 前串联静态黑名单校验与 Qwen2.5-Coder 微调模型推理（CodeGuard），策略开关管理端即时可配，详见 [AI 危险代码检测](ai-detect.md)
- **Shell命令执行**：在沙箱中执行 shell 命令（内置黑名单防护）
- **pip包管理**：安装、卸载 Python 包
- **文件操作**：上传、下载、写入沙箱中的文件
- **ApiKey 认证**：基于 client_api_key 表的 SHA-256 摘要校验（X-Api-Key 请求头，除健康检查外全接口生效）
- **多维限流**：API_KEY / CLIENT / GLOBAL 三维度规则，60s 定时拉取热生效
- **自动清理**：容器重启后自动清空所有数据和依赖包
- **会话超时**：可配置的空闲会话自动回收机制
- **容量限制**：可配置最大活跃容器数及超出策略（拒绝 / 驱逐最旧）
- **启动预热**：可选择在服务启动时预拉取 Python 镜像，避免首次会话延迟
- **审计与监控**：traceId 串联 API/沙箱操作日志；Actuator + Prometheus 指标

## 技术栈

- Java 17
- Spring Boot 3.2.0
- Docker Java API (docker-java 3.3.4)
- python:3.12-trixie (沙箱基础镜像)
## 快速开始

### 1. 环境变量配置

全栈部署使用**仓库根目录** `.env`（单服务本地开发可参考 `python-sandbox/.env.example`）：

```bash
cp .env.example .env
# 编辑 .env，至少修改 DB_PASSWORD / ADMIN_INTERNAL_TOKEN
```

> 根 `.env.example` 中已包含根 `docker-compose.yml` 所有可调参数，开箱即用。

### 2. 构建并启动

```bash
# 仓库根目录：使用 docker-compose 启动全栈（mysql/redis/sandbox-api/admin-server/admin-web）
docker compose up -d --build

# 或直接使用 Maven 本地运行沙箱服务（在 python-sandbox 目录）
mvn spring-boot:run
```
```

### 3. 验证服务

```bash
curl http://localhost:8080/health
```

## 配置项说明

所有配置均可在 `.env`（推荐）或 `application.yml` 中调整，最终生效值以环境变量为准。

| 环境变量 | application.yml 对应 | 默认值 | 说明 |
|---|---|---|---|
| `ADMIN_INTERNAL_TOKEN` | `sandbox.internal.token` | `change-me-...` | 管理端内部接口（`/internal/**`）共享凭证，与 admin-server 侧一致；不入库，生产必改 |
| `SANDBOX_RATELIMIT_REFRESH_MILLIS` | `sandbox.ratelimit.refresh-interval-millis` | `60000` | 限流规则 / CodeGuard 策略开关定时拉取间隔 |
| `SANDBOX_CODEGUARD_DETECT_BASE_URL` | `sandbox.code-guard.detect-base-url` | `http://code-detect:8000` | 模型推理检测服务地址（compose 网络别名） |
| `SANDBOX_CODEGUARD_DETECT_TIMEOUT_MILLIS` | `sandbox.code-guard.detect-timeout-millis` | `5000` | 推理服务调用超时（毫秒） |
| `SANDBOX_PYTHON_SECURITY_ENABLED` | `sandbox.python-security.enabled` | `true` | Python 静态校验明细开关（策略总开关见 sys_config） |
| `SANDBOX_PYTHON_BLOCKED_MODULES` | `sandbox.python-security.extra-blocked-modules` | 留空 | 追加禁用模块（逗号分隔，与默认黑名单合并） |
| `SANDBOX_API_KEY` | `sandbox.api-key` | `sandbox-secret-key` | 【已废弃】静态密钥占位；认证已改为 client_api_key 表摘要校验 |
| `SANDBOX_IMAGE` | `sandbox.image` | `python:3.12-trixie` | 沙箱使用的 Python 镜像 |
| `SANDBOX_CONTAINER_NAME_PREFIX` | `sandbox.container-name-prefix` | `python-sandbox-` | 沙箱容器名称前缀 |
| `SANDBOX_SESSION_TIMEOUT_MILLIS` | `sandbox.session-timeout-millis` | `86400000` | 会话超时时间（毫秒），默认 24 小时 |
| `SANDBOX_SESSION_CLEANUP_INTERVAL_MILLIS` | `sandbox.session-cleanup-interval-millis` | `3600000` | 过期会话扫描间隔，默认 1 小时 |
| `SANDBOX_MAX_CONTAINERS` | `sandbox.max-containers` | `10` | 最大活跃沙箱容器数 |
| `SANDBOX_MAX_CONTAINERS_BEHAVIOR` | `sandbox.max-containers-behavior` | `reject` | 超限策略：`reject` / `evict-oldest` |
| `SANDBOX_PULL_IMAGE_ON_STARTUP` | `sandbox.pull-image-on-startup` | `false` | 是否在服务启动时预拉取镜像 |
| `DOCKER_HOST` | `sandbox.docker-host` | 留空（自动检测） | Docker 守护进程连接地址 |
| `DOCKER_CERT_PATH` | `sandbox.docker-cert-path` | 留空 | TLS 证书目录路径 |
| `DOCKER_TLS_VERIFY` | `sandbox.docker-tls-verify` | `false` | 是否启用 TLS 验证 |
| `DOCKER_API_VERSION` | `sandbox.docker-api-version` | 留空（使用默认） | Docker API 版本号 |

### 启动预拉取镜像（`SANDBOX_PULL_IMAGE_ON_STARTUP`）

服务启动时（`SandboxService` 的 `@PostConstruct` 钩子）会按需预拉取 Python 镜像：

- **`false`（默认）**：不预拉取，首次创建会话时才触发拉取（冷启动稍慢）
- **`true`**：启动 Bean 时同步执行 `docker pull`，确保首个会话请求零延迟

> 拉取失败仅记录日志，不会阻断应用启动。

### Docker 连接方式（`DOCKER_HOST`）

系统使用 **docker-java API** 直接操作 Docker 守护进程，支持本地和远程连接。

#### 本地 Docker（默认）

`DOCKER_HOST` 留空时自动检测本地 Docker socket，也可明确指定：

```env
DOCKER_HOST=
# 或明确指定
DOCKER_HOST=unix:///var/run/docker.sock
```

> docker-compose 部署时需要挂载宿主机 socket：
> ```yaml
> volumes:
>   - /var/run/docker.sock:/var/run/docker.sock
> ```

#### 远程 Docker（TCP，无加密）

> ⚠️ 仅适用于内网环境，Docker API 无认证，任何能访问该端口的人都可以控制 Docker 主机。

**1. 服务端配置（`.env`）**：

```env
DOCKER_HOST=tcp://192.168.1.100:2375
```

**2. 远程 Docker 主机配置**：

编辑远程主机上的 `/etc/docker/daemon.json`：

```json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2375"]
}
```

重启 Docker 服务：

```bash
sudo systemctl restart docker
```

**3. docker-compose.yml 调整**：

注释或删除本地 socket 挂载：

```yaml
services:
  python-sandbox:
    # volumes:
    #   - /var/run/docker.sock:/var/run/docker.sock   # 注释掉
    environment:
      - DOCKER_HOST=tcp://192.168.1.100:2375
```

#### 远程 Docker（TLS 加密，推荐生产环境）

**1. 生成 TLS 证书**：

在远程 Docker 主机上执行以下脚本生成 CA 和客户端证书：

```bash
#!/bin/bash
# === 配置变量 ===
SERVER_IP="192.168.1.100"    # 远程 Docker 主机 IP
CERT_DIR="./docker-certs"    # 证书输出目录

mkdir -p "$CERT_DIR"

# 生成 CA 密钥和证书
openssl genrsa -out "$CERT_DIR/ca-key.pem" 4096
openssl req -x509 -new -nodes -key "$CERT_DIR/ca-key.pem" \
  -days 3650 -out "$CERT_DIR/ca.pem" \
  -subj "/CN=docker-ca"

# 生成服务端密钥和证书
openssl genrsa -out "$CERT_DIR/server-key.pem" 4096

cat > "$CERT_DIR/server-ext.cnf" << EOF
subjectAltName = IP:${SERVER_IP},IP:127.0.0.1
EOF

openssl req -new -key "$CERT_DIR/server-key.pem" \
  -out "$CERT_DIR/server.csr" \
  -subj "/CN=docker-server"

openssl x509 -req -in "$CERT_DIR/server.csr" \
  -CA "$CERT_DIR/ca.pem" -CAkey "$CERT_DIR/ca-key.pem" \
  -CAcreateserial -out "$CERT_DIR/server-cert.pem" \
  -days 3650 -extfile "$CERT_DIR/server-ext.cnf"

# 生成客户端密钥和证书
openssl genrsa -out "$CERT_DIR/client-key.pem" 4096

cat > "$CERT_DIR/client-ext.cnf" << EOF
extendedKeyUsage = clientAuth
EOF

openssl req -new -key "$CERT_DIR/client-key.pem" \
  -out "$CERT_DIR/client.csr" \
  -subj "/CN=docker-client"

openssl x509 -req -in "$CERT_DIR/client.csr" \
  -CA "$CERT_DIR/ca.pem" -CAkey "$CERT_DIR/ca-key.pem" \
  -CAcreateserial -out "$CERT_DIR/client-cert.pem" \
  -days 3650 -extfile "$CERT_DIR/client-ext.cnf"

# 清理中间文件
rm -f "$CERT_DIR"/*.csr "$CERT_DIR"/*.cnf "$CERT_DIR"/*.srl

echo "证书生成完毕，目录: $CERT_DIR"
```

**2. 配置远程 Docker 主机**：

将服务端证书复制到 Docker 配置目录：

```bash
sudo cp server-cert.pem server-key.pem ca.pem /etc/docker/
```

编辑 `/etc/docker/daemon.json`：

```json
{
  "hosts": ["unix:///var/run/docker.sock", "tcp://0.0.0.0:2376"],
  "tls": true,
  "tlscacert": "/etc/docker/ca.pem",
  "tlscert": "/etc/docker/server-cert.pem",
  "tlskey": "/etc/docker/server-key.pem",
  "tlsverify": true
}
```

重启 Docker 服务：

```bash
sudo systemctl restart docker
```

**3. 服务端配置（`.env`）**：

将客户端证书（`ca.pem`、`client-cert.pem`、`client-key.pem`）放到服务端可访问的目录，然后配置：

```env
DOCKER_HOST=tcp://192.168.1.100:2376
DOCKER_CERT_PATH=/path/to/client-certs    # 包含 ca.pem、cert.pem（即 client-cert.pem）、key.pem（即 client-key.pem）
DOCKER_TLS_VERIFY=true
```

> ⚠️ `DOCKER_CERT_PATH` 目录下的三个文件必须命名为：
> - `ca.pem` — CA 证书
> - `cert.pem` — 客户端证书（即生成的 `client-cert.pem`）
> - `key.pem` — 客户端私钥（即生成的 `client-key.pem`）

**4. docker-compose.yml 调整**：

```yaml
services:
  python-sandbox:
    # volumes:
    #   - /var/run/docker.sock:/var/run/docker.sock   # 注释掉
    volumes:
      - ./docker-certs:/app/certs:ro                   # 挂载客户端证书（只读）
    environment:
      - DOCKER_HOST=tcp://192.168.1.100:2376
      - DOCKER_CERT_PATH=/app/certs
      - DOCKER_TLS_VERIFY=true
```

#### 连接验证

配置完成后，启动服务并访问健康检查接口验证连接：

```bash
curl http://localhost:8080/health
```

成功响应示例：

```json
{
  "status": "UP",
  "activeContainers": 0
}
```

如果连接失败，请检查：
- 远程主机防火墙是否开放了对应端口（2375 或 2376）
- Docker 守护进程是否正确监听（`ss -tlnp | grep 2375`）
- TLS 证书是否正确且未过期
- 服务端日志中的具体错误信息

## API 文档
### 认证说明

除了 `/health` 端点外，所有 API 都需要在请求头中包含 `X-Api-Key`：

```
X-Api-Key: sk_live_xxx
```

ApiKey 由管理端签发（明文 `sk_live_` + 40 hex，仅创建时一次性展示），库内只存 SHA-256 摘要；
校验通过后还会依次执行数据权限绑定与三维度限流判定。`/internal/**` 为管理端内部通道，
使用独立凭证头 `X-Admin-Internal-Token`。详见 [AI 危险代码检测](ai-detect.md) 与
[调用链路](request-flow.md)。
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

> ⚠️ 执行前经 **CodeGuard 双策略检测**（静态校验 + 可选模型推理，开关见管理端系统设置）。
> 命中危险代码返回 HTTP 403：
> ```json
> { "error": "SECURITY_VIOLATION", "message": "Calling 'os.remove' is prohibited [VIOLATION: BLOCKED_CALL]" }
> ```
> 模型策略命中的 VIOLATION 标记为 `MODEL_DETECTED_DANGEROUS` / `MODEL_DETECTION_UNAVAILABLE`，
> 详见 [AI 危险代码检测](ai-detect.md)。

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
├── .env.example                # 单服务本地开发环境变量模板（全栈用仓库根 .env）
└── src/
    └── main/
        ├── java/
        │   └── io/github/sandbox/
        │       ├── PythonSandboxApplication.java
        │       ├── aspect/                      # ApiLog / SandboxOperationLog 切面（traceId 审计）
        │       ├── config/
        │       │   ├── AsyncConfig.java / SandboxConfig.java / WebConfig.java
        │       ├── context/
        │       │   └── AuthContext.java         # 请求级认证上下文
        │       ├── controller/
        │       │   ├── SandboxController.java / InternalSandboxController.java / HealthController.java
        │       │   └── PythonExecRequest.java / ShellExecRequest.java / PipInstallRequest.java / FileWriteRequest.java
        │       ├── entity/ mapper/              # MyBatis-Plus：ApiKey/限流/日志/sys_config 只读视图
        │       ├── exception/
        │       │   ├── SandboxException.java
        │       │   └── GlobalExceptionHandler.java
        │       ├── filter/ interceptor/         # TraceFilter / ApiKeyAuthInterceptor / InternalTokenInterceptor
        │       └── service/
        │           ├── SandboxService.java      # @PostConstruct 启动钩子；runPythonCode 前调 CodeGuard
        │           ├── CodeGuardService.java    # 双策略编排（静态+模型），sys_config 开关 60s 拉取
        │           ├── PythonCodeValidator.java # 策略1：正则三层静态扫描
        │           ├── ModelCodeDetector.java   # 策略2：HTTP 调用微调模型推理服务（POST /detect）
        │           ├── RatelimitService.java / ApiKeyAuthService.java / AsyncLogService.java
        │           └── ShellCommandValidator.java
        └── resources/
            ├── application.yml
            └── db/init.sql
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

## 安全注意事项

1. **ApiKey 保护**：ApiKey 明文仅签发时一次性展示，库内只存 SHA-256 摘要；泄露后立即在管理端吊销
2. **内部凭证**：`ADMIN_INTERNAL_TOKEN` 两侧必须一致且生产必改，留空即拒绝一切 `/internal/**` 调用
3. **网络隔离**：建议将沙箱服务部署在内部网络，不要直接暴露到公网
4. **资源限制**：生产环境建议配置 Docker 容器的 CPU 和内存限制（`SANDBOX_CONTAINER_MEMORY_LIMIT`）
5. **危险检测**：CodeGuard 模型推理策略默认关闭；开启前请确保推理服务可用，并按安全要求设置
   `codeguard.model.fail-open`（false 时推理服务故障将拒绝执行代码）
6. **额外防护**：如需更严格的控制，可进一步扩展 `ShellCommandValidator` / `PythonCodeValidator` 规则，
   或在 sys_config 调整策略开关

### Q: 容器重启后数据会丢失吗？
A: 是的，每次重启容器都会清空所有 Python 包和上传的文件。这是设计如此。

### Q: 如何修改默认端口？
A: 在 `application.yml` 中修改 `server.port` 配置项。

### Q: 如何自定义 API Key？
A: 通过环境变量 `SANDBOX_API_KEY` 或 `application.yml` 中的 `sandbox.api-key` 配置。

### Q: 如何启用启动时预拉取镜像？
A: 在 `.env` 中设置 `SANDBOX_PULL_IMAGE_ON_STARTUP=true`，重启服务即可。

### Q: 如何连接远程 Docker？
A: 在 `.env` 中将 `DOCKER_HOST` 设为 `tcp://host:port`，并按需在 `docker-compose.yml` 中移除本地 socket 挂载。

### Q: 如何开启模型推理危险检测？
A: ① 启用推理服务 profile（如 `COMPOSE_PROFILES=detect-vllm`）；② 管理端「系统设置」打开
`codeguard.model.enabled`；③ 等待 ≤60s 生效。详见 [AI 危险代码检测](ai-detect.md)。

## License

MIT
