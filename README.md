# Python Sandbox

基于 Docker 容器化技术构建的 Python 隔离运行环境，提供安全的代码沙箱服务。

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
import com.itsu.sandbox.sdk.SandboxClient;

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
| **容量限制** | 可配置最大活跃容器数量 |

## 安全特性

- 🔒 **Docker 隔离**：每个会话运行独立的 Python 容器
- 🚫 **命令黑名单**：防止执行危险 shell 命令（删除、格式化、权限提升等）
- 🔑 **API Key 认证**：所有接口通过 API Key 保护
- ⏱️ **会话超时**：支持配置的自动超时清理机制
- 📦 **容量控制**：可配置最大并发容器数量

## 配置示例

通过 `.env` 文件或环境变量自定义配置：

```bash
SANDBOX_API_KEY=my-secret-key
SANDBOX_MAX_CONTAINERS=5
SANDBOX_SESSION_TIMEOUT_MILLIS=3600000     # 1 小时超时
SANDBOX_MAX_CONTAINERS_BEHAVIOR=evict-oldest  # 超过则驱逐最旧
```

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

---

© 2024 ITSU Team. All rights reserved.
