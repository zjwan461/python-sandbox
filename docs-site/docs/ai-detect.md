# AI 危险代码检测

本文档说明 Python Sandbox 的双策略危险代码检测体系（CodeGuard）：执行前拦截链路的编排方式、
策略开关的配置入口，以及底层微调模型（Jarvis-Coder）的训练与三种推理服务部署方案。

## 双策略架构（CodeGuard）

执行 Python 代码（`POST /api/sandbox/exec/python`）前，`CodeGuardService`
串联两个**互相独立、可分别开关**的检测策略，任一命中即拒绝执行（HTTP 403 `SECURITY_VIOLATION`）：

```
                     ┌─ 策略1 静态校验 ── PythonCodeValidator（正则三层扫描）
runPythonCode ──guard─┤        开关: codeguard.static.enabled（默认开）
                     └─ 策略2 模型推理 ── HTTP 调用微调模型推理服务（POST /detect）
                              开关: codeguard.model.enabled（默认关）
                              降级: codeguard.model.fail-open（推理服务不可用时放行/拒绝）
```

### 策略1：静态校验

`PythonCodeValidator` 采用"预处理 + 正则静态分析"：

1. **长度限制**：防止超大代码攻击（`SANDBOX_PYTHON_CODE_MAX_LENGTH`，默认 100KB）
2. **字符串/注释剥离**：避免误判字面量中的危险词
3. **三层扫描**：危险模块导入（shutil/subprocess/ctypes/socket 等）、
   危险属性调用（os.system/os.remove/shutil.rmtree 等）、
   危险内置函数（eval/exec/compile/__import__）
4. 支持经环境变量追加自定义黑名单（与默认黑名单合并生效）

### 策略2：模型推理

`ModelCodeDetector` 经 HTTP 调用推理服务（compose 网络别名 `code-detect:8000`），
由 Qwen2.5-Coder 微调模型判定代码危险性。相比静态规则，模型能识别**语义级危险**
（变量传参、字符串拼接混淆、未见过的危险组合），与静态黑名单互为补充。

### 策略开关（管理端配置）

三个开关存于 `sys_config` 表，在管理端「系统设置」页面即时修改：

| 稳定键 | 类型 | 默认 | 说明 |
|--------|------|------|------|
| `codeguard.static.enabled` | BOOLEAN | `true` | 静态校验策略开关 |
| `codeguard.model.enabled` | BOOLEAN | `false` | 模型推理策略开关（需推理服务在线后开启） |
| `codeguard.model.fail-open` | BOOLEAN | `true` | 推理服务不可用时放行（false=拒绝执行，安全优先） |

- 服务端每 60s 定时拉取（复用 `sandbox.ratelimit.refresh-interval-millis`），
  拉取失败保留旧缓存（fail-open）
- 每次模型推理调用的结果**异步落库** `codeguard_detect_log` 表（见下节）
- `sys_config` 缺键时回落到本地配置（`sandbox.code-guard.*-fallback`）
- 推理服务**地址与超时属基础设施配置**，不入库：`SANDBOX_CODEGUARD_DETECT_BASE_URL`
  （默认 `http://code-detect:8000`）、`SANDBOX_CODEGUARD_DETECT_TIMEOUT_MILLIS`（默认 5000）

## 检测记录落库（codeguard_detect_log）

python-sandbox 每次调用推理服务后，经 `AsyncLogService`（logExecutor 线程池）异步写入
`codeguard_detect_log` 表一条记录，**记录失败不影响检测与执行主流程**。

| 字段组 | 内容 |
|--------|------|
| 关联审计 | `trace_id`（对账 api_log）、`session_id`、`client_id`/`api_key_id`/`owner_user_id`（来自鉴权上下文） |
| 样本原文 | `code_snippet`（送检代码原文，MEDIUMTEXT）、`code_length` |
| 模型结果 | `model_name`、`label`（SAFE/DANGEROUS）、`dangerous`、`raw_output`、`latency_ms` |
| 处置口径 | `detect_status`（OK / SERVICE_ERROR）、`decision`（ALLOW / BLOCK / FAIL_OPEN / FAIL_CLOSE）、`error_message` |

**再训练数据回流**：表按 `(label, create_time)`、`(dangerous, create_time)` 建索引，
可直接导出线上样本（含 Hard-Negative 疑似误杀场景：label=SAFE 但被拦截或用户申诉），
转换为 `train/datasets` 的 JSONL 格式重训模型，形成数据飞轮。

> 表 schema：`cross-cutting/database/schema/008-codeguard-detect-log.sql`
> （compose 初始化自动执行；已有库需手工补跑一次）。

## 检测模型：Jarvis-Coder

基于 **Qwen2.5-Coder-1.5B-Instruct** 的 LoRA（QLoRA 4bit）微调模型，
任务为二分类判定：给定代码片段，仅输出 `DANGEROUS` 或 `SAFE`。
发布仓库：`zjwan461/jarvis-coder`（HuggingFace）。

### 训练流水线（train/ 目录）

| 步骤 | 脚本 | 说明 |
|------|------|------|
| 1. 环境自检 | `check_env.py` | GPU 算力 / bf16 支持 |
| 2. 数据集合成 | `generate_dataset.py` | 调用百炼 API 生成三类样本（危险 1200 / 安全 1200 / Hard-Negative 1000） |
| 3. 数据集质检 | `check_dataset.py` | 字段与标签合法性校验，坏样本剥离 |
| 4. QLoRA 微调 | `train_sft_lora.py` | 4bit NF4 量化 + LoRA(r=8)，3 epochs，训练完自动合并 LoRA → 完整模型 |
| 5. 离线评估 | `evaluate_testset.py` | temperature=0 逐条推理测试集，输出准确率与错误样本 |

**Hard-Negative 样本**是数据设计的关键：危险命令出现在注释或"仅打印不执行"的字符串中
（标签 `SAFE`），防止模型对危险词"见词色变"造成误杀。

> ⚠️ 纪律：`test.jsonl` 全程不参与训练与调参，仅最终评估使用一次。

### 推理服务：三种部署方案

三种方案对上游暴露**完全一致的接口**（`POST /detect`），可互为替代，切换仅需改 compose profile：

| 维度 | ① CPU 版 | ② CUDA 版 | ③ vLLM 版 ⭐ |
|------|---------|----------|-------------|
| 推理引擎 | transformers | transformers | vLLM（PagedAttention） |
| 服务入口 | `infer/api_server.py` | `infer/api_server.py` | `infer/vllm_api_server.py` |
| Dockerfile | `Dockerfile.cpu` | `Dockerfile.cuda` | `Dockerfile.vllm` |
| 硬件 | 无 GPU | NVIDIA GPU + CUDA 12.8 | NVIDIA GPU，显存 ≥ 8GB |
| 操作系统 | 跨平台 | 跨平台 | 仅 Linux（Windows 走 WSL2） |
| 并发吞吐 | 低 | 中 | **高**（continuous batching） |
| 适用 | 功能验证 / CI | 单卡小流量 | **生产环境** |

**接口契约**（三方案一致，默认监听 `:8000`）：

```
POST /detect
Content-Type: application/json

请求体  { "code": "<待检测代码片段>" }
响应体  { "label": "SAFE" | "DANGEROUS", "raw_output": "<模型原始输出>" }
```

**模型内置镜像**：三个 Dockerfile 均在构建期执行 `hf download ${BASE_MODEL}`
将权重打进镜像（运行零下载）；换模型需 `--build-arg BASE_MODEL=xxx` 重建。

### 全栈编排（docker-compose profile）

三个推理服务已集成到根 `docker-compose.yml`，**默认均不启动**，三选一：

```bash
# 方式 A：根 .env 设置 COMPOSE_PROFILES=detect-vllm
docker compose up -d --build

# 方式 B：命令行临时指定
docker compose --profile detect-cpu up -d --build    # 或 detect-cuda / detect-vllm
```

| compose 服务 | profile | 端口 | 网络别名 |
|--------------|---------|------|---------|
| `detect-cpu` | `detect-cpu` | `DETECT_PORT`（默认 8000） | `code-detect` |
| `detect-cuda` | `detect-cuda` | 同上 | `code-detect` |
| `detect-vllm` | `detect-vllm` | 同上 | `code-detect` |

GPU 版通过 `deploy.resources.reservations.devices` 申请 nvidia 设备（宿主机需 nvidia-container-toolkit）。

### vLLM 调优参数

| 环境变量 | 默认 | 说明 |
|----------|------|------|
| `MAX_MODEL_LEN` | `1024` | 上下文总长。输出仅几个 token，窗口几乎全部留给**输入代码**；代码普遍短可降到 512 省显存 |
| `GPU_MEMORY_UTILIZATION` | `0.85` | vLLM 显存预留比例，OOM 时下调 |

## 启用步骤（端到端）

1. 启动推理服务：`.env` 设置 `COMPOSE_PROFILES=detect-vllm` 后 `docker compose up -d --build`
2. 冒烟验证：`curl -X POST http://localhost:8000/detect -H "Content-Type: application/json" -d '{"code":"import os\nos.remove(\"/etc/shadow\")"}'` → `label=DANGEROUS`
3. 管理端「系统设置」打开 `codeguard.model.enabled`
4. 等待 ≤ 60s 定时拉取（或重启 sandbox-api），sandbox-api 日志出现
   `CodeGuard 策略开关刷新成功: ... model=true ...` 即生效

## 排障速查

| 现象 | 处理 |
|------|------|
| vLLM 在 Windows 装不上 | vLLM 仅支持 Linux，用 WSL2 或 Docker |
| vLLM 启动 OOM | 下调 `GPU_MEMORY_UTILIZATION` 或 `MAX_MODEL_LEN` |
| `/detect` 返回 500 | 多为运行期 `BASE_MODEL` 与镜像构建时不一致，导致加载失败 |
| 模型误杀 `print("rm -rf /")` | 属模型侧误判，补充 Hard-Negative 样本重训 |
| CodeGuard 不生效 | 确认开关为 true 且已过 ≤60s 拉取周期；核对 `SANDBOX_CODEGUARD_DETECT_BASE_URL` 可达性 |
| CPU 版频繁超时降级 | CPU 推理秒级延迟，生产务必使用 vLLM 版 |

## 延伸阅读（仓库内文档）

- 根 `README.md` · 「双策略危险检测（CodeGuard）」章节 —— 平台视角总览
- `train/README.md` —— 数据集合成 / QLoRA 微调 / 评估的完整训练手册
- `train/infer/README.md` —— 三种推理部署方案的详细手册（含裸机 pip 安装方式）
