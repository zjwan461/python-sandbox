# Python Sandbox

基于 Docker 容器化技术构建的 Python 隔离运行环境，提供安全的代码沙箱服务。

**核心特性**：使用 docker-java API 直接操作 Docker 守护进程，支持本地和远程 Docker 连接。

## 项目结构

```
├── python-sandbox/          # 🖥️ 后端 API 服务 (Spring Boot 3 + Java 17)
│   ├── src/main/java/       # 源代码
│   ├── pom.xml              # Maven 配置
│   ├── Dockerfile           # 应用容器镜像
│   ├── docker-compose.yml   # Docker Compose 编排
│   └── README.md            # 详细文档
│
├── sdk/                     # 💻 官方客户端 SDK
│   ├── README.md            # SDK 使用说明
│   ├── java-sandbox-sdk/    # Java SDK (Maven)
│   │   ├── pom.xml
│   │   └── src/main/java/
│   └── python-sandbox-sdk/  # Python SDK (pip)
│       ├── pyproject.toml
│       └── python_sandbox_sdk/
│
├── .gitignore               # Git 忽略规则
└── LICENSE                  # MIT License
```

## 快速开始

### 启动沙箱服务

使用 Docker Compose 一键启动：

```bash
cd python-sandbox
docker-compose up -d --build
```

然后访问 `http://localhost:8080/health` 验证服务状态。

### 使用 SDK

#### Java

```java
import io.github.sandbox.sdk.SandboxClient;

try (SandboxClient client = new SandboxClient("http://localhost:8080", "your-api-key")) {
    String sessionId = client.createSession();
    SandboxResponse result = client.execPython(sessionId, "print('Hello!')");
    System.out.println(result.getStdout());
    client.deleteSession(sessionId);
}
```

#### Python

```python
from python_sandbox_sdk import SandboxClient

with SandboxClient("http://localhost:8080", "your-api-key") as client:
    session_id = client.create_session()
    result = client.exec_python(session_id, "print('Hello!')")
    print(result.stdout)
    client.delete_session(session_id)
```

## 核心功能

| 功能 | 说明 |
|------|------|
| **Python 代码执行** | 在隔离容器中运行任意 Python 代码 |
| **Shell 命令执行** | 支持 shell 命令（内置黑名单防护） |
| **pip 包管理** | 安装、卸载 Python 包 |
| **文件操作** | 上传、下载、读写沙箱中的文件 |
| **API Key 认证** | 所有接口安全认证 |
| **自动清理** | 会话超时自动清理容器 |
| **容量限制** | 可配置最大活跃容器数量及超限策略 |
| **启动预热** | 可选择在服务启动时预拉取 Python 镜像 |
| **远程 Docker** | 支持连接本地或远程 Docker 守护进程（通过 docker-java API） |

## 安全特性

- 🔒 **Docker 隔离**：每个会话运行独立的 Python 容器
- 🚫 **命令黑名单**：防止执行危险 shell 命令（删除、格式化、权限提升等）
- 🔑 **API Key 认证**：所有接口通过 API Key 保护
- ⏱️ **会话超时**：支持配置的自动超时清理机制
- 📦 **容量控制**：可配置最大并发容器数量

## 配置示例

首次启动前建议复制示例文件并按需修改：

```bash
cd python-sandbox
cp .env.example .env
# 编辑 .env，调整至少以下字段：
#   SANDBOX_API_KEY                  - API 鉴权密钥
#   SANDBOX_PULL_IMAGE_ON_STARTUP    - 是否启动时预拉取镜像
#   SANDBOX_MAX_CONTAINERS           - 最大活跃容器数
#   DOCKER_HOST                      - Docker 连接地址（本地 socket / 远程 tcp）
```

```bash
# .env 示例
SANDBOX_API_KEY=my-secret-key
SANDBOX_IMAGE=python:3.12-trixie
SANDBOX_MAX_CONTAINERS=5
SANDBOX_SESSION_TIMEOUT_MILLIS=3600000     # 1 小时超时
SANDBOX_MAX_CONTAINERS_BEHAVIOR=evict-oldest  # 超过则驱逐最旧
SANDBOX_PULL_IMAGE_ON_STARTUP=true          # 启动预拉取镜像
DOCKER_HOST=                                # 留空自动检测本地 Docker
# 或连接远程 Docker
# DOCKER_HOST=tcp://192.168.1.100:2375
# DOCKER_CERT_PATH=/path/to/certs          # TLS 证书目录
# DOCKER_TLS_VERIFY=true
```

完整配置项、环境变量对照表以及远程 Docker 配置说明请参阅
[python-sandbox/README.md](server.md)。

## 许可证

MIT License
