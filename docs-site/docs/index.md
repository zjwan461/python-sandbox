# Python Sandbox

基于 Docker 容器化技术构建的 Python 隔离运行环境，提供安全的代码沙箱服务、
RBAC 管理控制台与 AI 危险代码检测能力。

**核心特性**

- 使用 docker-java API 直接操作 Docker 守护进程，支持本地和远程 Docker 连接
- 执行前**双策略危险代码检测**（正则静态校验 + Qwen2.5-Coder 微调模型推理），管理端即时可配
- ApiKey 全生命周期管理、多维度限流、traceId 全链路审计
- Java / Python 双 SDK，全栈一键 Docker Compose 编排

## 项目结构

```
├── python-sandbox/          # 🖥️ 沙箱执行服务 (Spring Boot 3 + Java 17, :8080)
│   ├── src/main/java/       # 源代码（controller/service/mapper/aspect/interceptor）
│   ├── src/main/resources/  # application.yml + db/init.sql
│   ├── pom.xml              # Maven 配置
│   └── Dockerfile           # 应用容器镜像（根目录为 build context）
│
├── admin-server/            # 🛠️ 管理端后端 (Spring Boot 3 + Sa-Token, :9090, /admin-api)
├── admin-web/               # 🎨 管理端前端 (Vue 3 + TypeScript + Vite + Element Plus, :80)
│
├── train/                   # 🤖 AI 危险代码检测模型（Qwen2.5-Coder LoRA 微调）
│   ├── datasets/            #   训练/验证/测试 JSONL 数据集
│   ├── *.py                 #   数据合成 / QLoRA 训练 / 测试集评估
│   └── infer/               #   推理服务（FastAPI，CPU / CUDA / vLLM 三种部署）
│
├── cross-cutting/           # 🗄️ 三工程共享资料：管理端 schema 增量 / 种子数据 / ER 对齐
│   └── database/{schema,seed}/
│
├── sdk/                     # 💻 官方客户端 SDK
│   ├── java-sandbox-sdk/    # Java SDK (Maven)
│   └── python-sandbox-sdk/  # Python SDK (pip，同步 + 异步 + 12 个 usage 示例)
│
├── docker-compose.yml       # 🐳 全栈编排（根目录统一入口，含推理服务 profile）
├── .env.example             # ⚙️ 根环境变量示例（所有服务统一读取）
└── LICENSE                  # MIT License
```

## 快速开始

### 全栈启动（推荐）

```bash
# 1. 准备环境变量（Windows 用 copy）
cp .env.example .env         # 至少修改 DB_PASSWORD / ADMIN_INTERNAL_TOKEN

# 2. 启动核心栈（mysql + redis + sandbox-api + admin-server + admin-web + prometheus）
docker compose up -d --build

# 3. 按需启用 AI 检测推理服务（三选一，默认不启动）
docker compose --profile detect-vllm up -d --build
```

- 沙箱服务验证：`http://localhost:8080/health`
- 管理控制台：`http://localhost`（默认账号 admin / Admin@123，生产必改）

详见 [服务端文档](server.md)、[AI 危险代码检测](ai-detect.md)。

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

完整接口说明与更多场景示例见 [SDK 文档](sdk.md)。

## 核心功能

### 沙箱执行服务

| 功能 | 说明 |
|------|------|
| **Python 代码执行** | 在隔离容器中运行任意 Python 代码 |
| **Shell 命令执行** | 支持 shell 命令（内置黑名单防护） |
| **pip 包管理** | 安装、卸载 Python 包 |
| **文件操作** | 上传、下载、读写沙箱中的文件 |
| **双策略危险检测** | 静态校验 + 模型推理，独立开关，管理端即时可改 |
| **ApiKey 认证** | 基于 SHA-256 摘要校验，明文仅一次性展示 |
| **多维限流** | API_KEY / CLIENT / GLOBAL 三维度规则，60s 定时热生效 |
| **自动清理** | 会话超时自动清理容器 |
| **容量限制** | 可配置最大活跃容器数量及超限策略 |
| **远程 Docker** | 支持连接本地或远程 Docker 守护进程（docker-java API） |
| **可观测性** | Prometheus 指标 + API/沙箱操作双审计日志 |

### 管理控制台

| 模块 | 能力 |
|------|------|
| **认证与安全** | Sa-Token 会话、验证码、登录失败锁定、Remember-Me |
| **RBAC** | 用户/角色/菜单，按钮级权限码，数据权限 |
| **ApiKey / 客户端** | 签发、吊销、掩码展示；业务方档案与统计 |
| **限流规则** | 三维度规则 CRUD，时间窗与优先级 |
| **会话运维** | 活跃会话查询、单个/批量强制销毁 |
| **日志审计** | API 日志、沙箱操作日志、登录/操作审计（traceId 串联） |
| **系统设置** | 受控 KV：锁定阈值、默认限流、匿名灰度、CodeGuard 策略开关 |

## 安全特性

- 🔒 **Docker 隔离**：每个会话运行独立的 Python 容器（内存限制 + 容量上限）
- 🔍 **纵深检测**：CodeGuard 双策略——静态黑名单 + 微调模型推理，执行前拦截
- 🚫 **命令黑名单**：防止执行危险 shell 命令（删除、格式化、权限提升等）
- 🔑 **凭证安全**：ApiKey 仅存 SHA-256 摘要；内部通道独立 token（ENV-only）
- 🚦 **限流防护**：三维度规则叠加，全局默认兜底
- 📜 **全链路审计**：traceId 串联 API 日志、沙箱操作日志、登录/操作审计

## 配置示例

全栈部署使用**根目录** `.env`（参考根 `.env.example`，所有服务统一读取）；
单服务本地开发参考 `python-sandbox/.env.example`。要点：

```bash
# .env 示例（节选）
DB_PASSWORD=root                                # MySQL root 密码
ADMIN_INTERNAL_TOKEN=change-me-...              # 管理端内部通道凭证（两侧一致，生产必改）
SANDBOX_MAX_CONTAINERS=5                        # 最大活跃容器数
SANDBOX_SESSION_TIMEOUT_MILLIS=3600000          # 会话 1 小时超时
SANDBOX_MAX_CONTAINERS_BEHAVIOR=evict-oldest    # 超限驱逐最旧
SANDBOX_PULL_IMAGE_ON_STARTUP=true              # 启动预拉取镜像
DOCKER_HOST=                                    # 留空自动检测本地 Docker
                                                # 远程: tcp://192.168.1.100:2375

# AI 危险代码检测（CodeGuard）
COMPOSE_PROFILES=detect-vllm                    # 推理服务 profile（三选一，留空不启动）
BASE_MODEL=zjwan461/jarvis-coder                # 模型（构建期打入镜像）
SANDBOX_CODEGUARD_DETECT_BASE_URL=http://code-detect:8000   # 推理服务地址
```

完整配置项、环境变量对照表以及远程 Docker 配置说明请参阅
[服务端文档](server.md)；模型训练与推理部署见 [AI 危险代码检测](ai-detect.md)。

## 文档导航

- [服务端文档](server.md) —— 接口、配置、远程 Docker、Shell 黑名单
- [AI 危险代码检测](ai-detect.md) —— CodeGuard 双策略、模型训练、三种推理部署
- [SDK 文档](sdk.md) —— Java / Python SDK 使用说明
- [调用链路](request-flow.md) —— 请求处理全链路剖析

## 许可证

MIT
