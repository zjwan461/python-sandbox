<div align="center">

# 🏖️ Python Sandbox

**基于 Docker 容器化的 Python 代码沙箱平台**

`沙箱执行服务` · `管理控制台` · `AI 危险代码检测` · `多语言 SDK` · `一键全栈编排`

[![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)](python-sandbox/pom.xml)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?logo=springboot)](python-sandbox/pom.xml)
[![Vue](https://img.shields.io/badge/Vue-3-42b883?logo=vue.js)](admin-web/package.json)
[![Python](https://img.shields.io/badge/Python-3.11%2B-blue?logo=python)](train/infer)
[![vLLM](https://img.shields.io/badge/Inference-vLLM%20%7C%20transformers-red?logo=huggingface)](train/infer)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

</div>

---

Python Sandbox 是一套面向"安全执行不可信 Python 代码"场景的全栈解决方案：以 **docker-java API**
直接编排容器生命周期实现会话级隔离，以 **双策略代码危险检测**（正则静态校验 + Qwen2.5-Coder
微调模型推理）在执行前拦截危险代码，并配套完整的 **RBAC 管理控制台**、**ApiKey 鉴权与多维限流**、
**审计日志**与 **Java / Python 官方 SDK**。

## ✨ 功能全景

### 🖥️ 沙箱执行服务 · `python-sandbox`

| 功能 | 说明 |
|------|------|
| 🐍 **Python 代码执行** | 会话级独立容器运行，写入-执行-清理全托管 |
| 💻 **Shell 命令执行** | 内置黑名单防护（删除、格式化、提权等） |
| 📦 **pip 包管理** | 会话内安装 / 卸载 / 列出 Python 包 |
| 📁 **文件操作** | 文本/二进制文件上传、下载、读写（50MB 上限） |
| 🔍 **双策略危险检测** | 静态校验 + 模型推理，独立开关，管理端即时可改（详见下文） |
| 🔑 **ApiKey 认证** | `sk_live_+40hex` 明文仅一次性展示，库内只存 SHA-256 摘要 |
| 🚦 **多维限流** | API_KEY 滑动窗口 / CLIENT 令牌桶 / GLOBAL 规则，60s 定时拉取热生效 |
| ⏱️ **会话生命周期** | 超时自动清理（默认 24h），容量上限与驱逐策略（reject / evict-oldest） |
| 🐳 **本地 & 远程 Docker** | unix socket 或 tcp（可选 TLS），经 `DOCKER_HOST` 一键切换 |
| 📈 **可观测性** | Actuator + Prometheus 指标；API/沙箱操作双审计日志落库 |

### 🛠️ 管理控制台 · `admin-server` + `admin-web`

| 模块 | 能力 |
|------|------|
| 🔐 **认证与安全** | Sa-Token 会话、图形验证码、登录失败锁定、Remember-Me |
| 👥 **RBAC** | 用户 / 角色 / 菜单三级模型，按钮级权限码，数据权限（本人/本客户/全部） |
| 🗝️ **ApiKey 管理** | 签发、吊销、掩码展示（`sk_live_seed **** DEMO`），一次明文消费 |
| 🚦 **限流规则** | 三维度规则 CRUD，生效/过期时间窗，优先级叠加 |
| 🗂️ **客户端管理** | 业务方档案与用量统计 |
| 📋 **会话运维** | 活跃沙箱会话查询、单个/批量强制销毁 |
| 📜 **日志审计** | API 调用日志、沙箱操作日志、登录/操作审计、traceId 全链路串联 |
| ⚙️ **系统设置** | 受控 KV：注册开关、锁定阈值、全局默认限流、匿名灰度、**CodeGuard 策略开关** |
| 📢 **公告通知** | 站内公告与已读回执 |

### 🤖 AI 危险代码检测 · `train`（[训练全流程文档 →](train/README.md)）

基于 **Qwen2.5-Coder** LoRA 微调的危险代码判定模型（输出 `DANGEROUS` / `SAFE`），
覆盖训练 → 数据集 → 推理服务 → 容器化完整链路：

| 环节 | 内容 |
|------|------|
| 🧪 数据集生成 | [`generate_dataset.py`](train/generate_dataset.py) 合成 + [`check_dataset.py`](train/check_dataset.py) 质检 |
| 🎯 LoRA 微调 | [`train_sft_lora.py`](train/train_sft_lora.py)（CUDA / CPU 双档） |
| 📊 评估 | [`evaluate_testset.py`](train/evaluate_testset.py) 测试集准确率评估 |
| 🚀 推理服务 | [`api_server.py`](train/infer/api_server.py)（transformers）/ [`vllm_api_server.py`](train/infer/vllm_api_server.py)（vLLM，高吞吐）· [三种部署方案对比 →](train/infer/README.md) |
| 🐳 容器化 | CPU / CUDA / vLLM 三个 Dockerfile，**模型权重构建期直打镜像**，运行零下载 |

### 💻 官方 SDK · `sdk`

- **Java SDK**（Maven）：`SandboxClient`，try-with-resources 自动清理
- **Python SDK**（pip）：同步 `SandboxClient` + 异步 `AsyncSandboxClient`，[12 个场景示例](sdk/python-sandbox-sdk/usage)

## 🔍 双策略危险检测（CodeGuard）

执行 Python 前，[`CodeGuardService`](python-sandbox/src/main/java/io/github/sandbox/service/CodeGuardService.java)
串联两个**互相独立、可分别开关**的检测策略，任一命中即拒绝执行（HTTP 403 `SECURITY_VIOLATION`）：

```
                     ┌─ 策略1 静态校验 ── PythonCodeValidator（正则三层扫描：模块/调用/内置函数）
runPythonCode ──guard─┤        开关: codeguard.static.enabled（默认开）
                     └─ 策略2 模型推理 ── HTTP 调用 train/infer 微调模型服务（POST /detect）
                              开关: codeguard.model.enabled（默认关）
                              降级: codeguard.model.fail-open（推理服务不可用时放行/拒绝）
```

- 三个开关存于 `sys_config`，管理端「系统设置」页面即时修改，服务端 60s 定时拉取生效
- 每次模型推理调用的判定结果（代码原文、label、耗时、处置决策）异步落库
  `codeguard_detect_log` 表，供调用审计与**再训练数据回流**（数据飞轮）
- 推理服务三选一（compose profile）：`detect-cpu`（验证用）/ `detect-cuda` / `detect-vllm`（生产推荐）
- 策略明细见 [`cross-cutting/database/seed/003-codeguard.sql`](cross-cutting/database/seed/003-codeguard.sql)

## 🔍 大模型结果复检（LLM Review）

对小模型推理结果进行异步复检，使用大模型（DeepSeek/Qwen/OpenAI 等）验证小模型判定的准确性，支持人工复核和结果导出。

### 功能特性

- **异步复检任务**：定时调度（默认每 30 分钟）批量处理待复检的检测记录
- **大模型验证**：调用大模型 API 判断小模型判定是否正确，并给出解释
- **人工复核**：管理员可在页面上修改大模型的复检结果，作为最终真相
- **JSONL 导出**：导出复检结果为 JSONL 格式，用于模型微调训练
- **任务管理**：支持任务中断、重启、重试（最多 3 次）

### 配置说明

在 `sys_config` 表中配置以下参数（管理端「系统设置」页面可修改）：

| 配置键 | 说明 | 默认值 |
|--------|------|--------|
| `llm.review.enabled` | 是否开启大模型复检 | `false` |
| `llm.review.provider` | 大模型提供商（openai/deepseek/qwen） | `openai` |
| `llm.review.api.endpoint` | 大模型 API 地址 | 空 |
| `llm.review.model.name` | 大模型名称 | `gpt-4o-mini` |
| `llm.review.batch.size` | 单次复检批量大小 | `50` |
| `llm.review.cron` | 复检调度 Cron 表达式 | `0 0/30 * * * ?` |

**环境变量**：
- `LLM_REVIEW_API_KEY`：大模型 API Key（必填，不入库）

### 使用流程

1. **启用复检功能**：在管理端「系统设置」中将 `llm.review.enabled` 设为 `true`
2. **配置大模型**：设置 API endpoint、model name，并通过环境变量注入 API Key
3. **创建复检任务**：在「大模型复检」页面选择检测记录创建复检任务
4. **等待异步执行**：系统定时调度执行复检，调用大模型进行验证
5. **人工复核**：查看大模型复检结果，必要时进行人工修正
6. **导出训练数据**：导出 JSONL 格式数据，用于小模型微调训练

### 数据表

- `llm_review_task`：复检任务表，存储任务状态、大模型结果、人工复核结果
- 关联 `codeguard_detect_log`：通过 `detect_log_id` 关联小模型检测记录

### 权限控制

- `llmreview:view`：查看复检任务
- `llmreview:edit`：创建/取消任务、人工复核
- `llmreview:export`：导出 JSONL 数据

### 前端页面

管理端「调用记录」→「大模型复检」菜单：
- 任务列表：筛选、分页、查看详情
- 人工复核：同意/不同意大模型判定，填写备注
- JSONL 导出：按筛选条件导出复检结果

## 🗂️ 项目结构

```
python-sandbox/
├── python-sandbox/          # 🖥️ 沙箱执行服务 (Spring Boot 3 + Java 17, :8080)
│   ├── src/main/java/       #   源码（controller/service/mapper/aspect/interceptor）
│   ├── src/main/resources/  #   application.yml + db/init.sql
│   ├── Dockerfile           #   应用镜像（根目录为 build context）
│   └── README.md            #   详细文档（配置项/接口/远程 Docker）
│
├── admin-server/            # 🛠️ 管理端后端 (Spring Boot 3 + Sa-Token, :9090, /admin-api)
│   └── src/main/java/       #   auth/rbac/apikey/ratelimit/session/log/sys ...
│
├── admin-web/               # 🎨 管理端前端 (Vue 3 + TypeScript + Vite + Element Plus)
│   └── src/views/           #   dashboard/auth/user/role/menu/apikey/ratelimit/log/audit/...
│
├── train/                   # 🤖 模型训练与推理（Qwen2.5-Coder LoRA 微调）
│   ├── generate_dataset.py  #   训练数据集生成
│   ├── train_sft_lora.py    #   LoRA 微调训练
│   ├── evaluate_testset.py  #   测试集评估
│   ├── datasets/            #   train/val/test JSONL 数据集
│   └── infer/               #   推理服务（FastAPI）
│       ├── infer_detect.py          # transformers 推理核心
│       ├── api_server.py            # transformers 版 API 服务
│       ├── vllm_api_server.py       # vLLM 版 API 服务
│       ├── requirements-{cpu,cuda,vllm}.txt
│       └── Dockerfile.{cpu,cuda,vllm}   # 模型权重构建期打入镜像
│
├── sdk/                     # 💻 官方客户端 SDK
│   ├── java-sandbox-sdk/    #   Java (Maven)
│   └── python-sandbox-sdk/  #   Python (pip, 同步 + 异步 + 12 个 usage 示例)
│
├── cross-cutting/           # 🗄️ 三工程共享：DB schema 增量 / 种子数据 / ER 对齐
│   └── database/{schema,seed}/
│
├── docs-site/               # 📖 文档站 (MkDocs：请求链路/SDK/服务端说明)
│
├── docker-compose.yml       # 🐳 全栈编排（根目录统一入口，含推理服务 profile）
├── .env.example             # ⚙️ 根环境变量示例（所有服务统一读取）
└── maven-settings.xml       # 🇨🇳 Maven 阿里云镜像（两个 Java 工程共用）
```

## 🐳 快速开始（Docker Compose 全栈）

> 要求：Docker 24+（含 Compose v2）、可访问 Docker daemon 的宿主机；GPU 推理需 nvidia-container-toolkit。

```bash
# 1. 准备环境变量
copy .env.example .env        # Windows
# cp .env.example .env        # Linux/macOS
#   至少修改: DB_PASSWORD / ADMIN_INTERNAL_TOKEN（生产必改）

# 2. 启动核心栈（mysql + redis + sandbox-api + admin-server + admin-web + prometheus）
docker compose up -d --build

# 3. 按需启用模型推理服务（三选一，默认均不启动）
#    .env 中设置 COMPOSE_PROFILES=detect-vllm  或命令行:
docker compose --profile detect-cpu up -d --build

# 4. 验证
curl http://localhost:8080/health          # 沙箱服务
start http://localhost                     # 管理控制台（admin / Admin@123）
curl -X POST http://localhost:8000/detect -H "Content-Type: application/json" ^
     -d "{\"code\":\"import os\nos.remove('/etc/shadow')\"}"   # 推理服务
```

**compose 服务清单**

| 服务 | 端口 | 说明 | profile |
|------|------|------|---------|
| `mysql` | 3306 | 共用库 sandbox（自动执行 schema + seed 初始化） | — |
| `redis` | 6379 | 会话 / 验证码 / Remember-Me | — |
| `sandbox-api` | 8080 | 沙箱执行服务 | — |
| `admin-server` | 9090 | 管理端后端（context-path `/admin-api`） | — |
| `admin-web` | 80 | 管理端前端（Nginx 静态 + 反代） | — |
| `prometheus` | 9091 | 指标采集 | — |
| `detect-cpu` | 8000 | transformers CPU 推理（模型内置镜像） | `detect-cpu` |
| `detect-cuda` | 8000 | transformers GPU 推理 | `detect-cuda` |
| `detect-vllm` | 8000 | vLLM GPU 推理（生产推荐） | `detect-vllm` |

启用检测推理后，在管理端「系统设置」打开 **模型推理检测策略** 开关即可接入执行链路。

## 💻 SDK 快速上手

#### Java

```java
import io.github.sandbox.sdk.SandboxClient;

try (SandboxClient client = new SandboxClient("http://localhost:8080", "sk_live_xxx")) {
    String sessionId = client.createSession();
    SandboxResponse result = client.execPython(sessionId, "print('Hello!')");
    System.out.println(result.getStdout());
    client.deleteSession(sessionId);
}
```

#### Python

```python
from python_sandbox_sdk import SandboxClient

with SandboxClient("http://localhost:8080", "sk_live_xxx") as client:
    session_id = client.create_session()
    result = client.exec_python(session_id, "print('Hello!')")
    print(result.stdout)
    client.delete_session(session_id)
```

完整示例（会话管理 / 文件操作 / 长任务 / 并发 / 异步客户端等 12 个场景）见
[`sdk/python-sandbox-sdk/usage/`](sdk/python-sandbox-sdk/usage)。

## 🗄️ 数据库迁移执行顺序

管理端与沙箱服务共用 MySQL `sandbox` 库（共库不同表）。Docker Compose 启动时按下列顺序自动执行；
手工部署请按序执行 [`cross-cutting/database/`](cross-cutting/README.md)（唯一真相来源）：

```
 1. python-sandbox/src/main/resources/db/init.sql               # 基线（api_log、sandbox_operation_log）
 2. cross-cutting/database/schema/001-admin-rbac.sql            # 用户/角色/菜单
 3. cross-cutting/database/schema/002-client-apikey.sql         # 客户端与 ApiKey（明文不入库）
 4. cross-cutting/database/schema/003-ratelimit.sql             # 限流规则
 5. cross-cutting/database/schema/004-admin-audit.sql           # 登录/操作审计日志
 6. cross-cutting/database/schema/005-sys-config.sql            # 系统设置 KV
 7. cross-cutting/database/schema/006-sandbox-log-extension.sql # 日志表扩展（幂等）
 8. cross-cutting/database/schema/007-sys-notice.sql            # 公告通知
 9. cross-cutting/database/schema/008-codeguard-detect-log.sql  # 模型推理检测记录表（审计+数据回流）
10. cross-cutting/database/seed/001-admin-seed.sql              # 种子（admin/Admin@123、角色/菜单/设置）
11. cross-cutting/database/seed/002-admin-batch6.sql            # 批次6 增量权限
12. cross-cutting/database/seed/003-codeguard.sql               # CodeGuard 策略开关键
```

所有 schema 与 seed 脚本均为幂等设计（`IF NOT EXISTS` / `INSERT IGNORE`），可重复执行。
已有库升级时仅需手工补跑新增脚本（initdb 挂载只对首次初始化的数据卷生效）。

## ⚙️ 配置要点

全部环境变量以根目录 [`.env.example`](.env.example) 为准，要点摘录：

| 分组 | 关键项 | 说明 |
|------|--------|------|
| 镜像版本 | `IMAGE_TAG` | 全部自编译镜像统一版本号（含 code-detect 三变体） |
| 推理 profile | `COMPOSE_PROFILES` | `detect-cpu` / `detect-cuda` / `detect-vllm` 三选一，留空不启动 |
| 模型 | `BASE_MODEL` | 构建期下载 + 运行期加载共用（修改需重建镜像） |
| vLLM 调优 | `MAX_MODEL_LEN` / `GPU_MEMORY_UTILIZATION` | 默认 1024 / 0.85 |
| CodeGuard | `SANDBOX_CODEGUARD_DETECT_BASE_URL` | 默认 `http://code-detect:8000`（compose 网络别名） |
| 安全 | `ADMIN_INTERNAL_TOKEN` | 管理端内部通道凭证，两侧必须一致，生产必改 |
| Docker | `DOCKER_HOST` | 留空自动检测；`tcp://host:2375` 连远程守护进程 |

## 🔒 安全设计总览

- 🐳 **容器隔离**：每会话独立容器 + 内存限制（默认 512MB）+ 容量上限
- 🔍 **纵深检测**：CodeGuard 双策略（静态黑名单 + 微调模型推理）执行前拦截
- 🚫 **命令黑名单**：Shell 侧危险命令防护（删除/格式化/提权）
- 🔑 **凭证安全**：ApiKey 仅存 SHA-256 摘要、明文一次性展示；内部通道独立 token（ENV-only 不入库）
- 🚦 **限流防护**：API_KEY / CLIENT / GLOBAL 三维度规则叠加，全局默认兜底
- 📜 **全链路审计**：traceId 串联 API 日志、沙箱操作日志、登录/操作审计
- 🧱 **最小权限**：容器内非 root 运行；管理端 RBAC 按钮级权限 + 数据权限

## 📚 延伸阅读

- [沙箱服务详细文档](python-sandbox/README.md)（接口 / 配置对照表 / 远程 Docker）
- [管理端后端说明](admin-server/README.md)
- [SDK 使用说明](sdk/README.md) · [Python SDK](sdk/python-sandbox-sdk/README.md)
- [cross-cutting 说明](cross-cutting/README.md)（目录边界与 SQL 执行口径）
- [模型训练与推理全流程](train/README.md)（数据集合成 / QLoRA 微调 / 评估 / 容器化上线）
- [推理服务三种部署方案](train/infer/README.md)（CPU / CUDA / vLLM 对比、上线与 CodeGuard 衔接）
- [文档站](docs-site/docs/index.md)（请求链路 / 架构说明）

## 🧭 路线图

- [ ] 模型推理策略支持置信度阈值分级处置（观察 / 拦截）
- [ ] vLLM 服务改造为 `AsyncLLMEngine` / OpenAI 兼容模式以提升并发吞吐
- [ ] 危险检测命中样本回流训练集，形成数据飞轮

## 📄 许可证

[MIT License](LICENSE)

---

<div align="center">

© 2026 Python-Sandbox. All rights reserved.

</div>
