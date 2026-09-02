<div align="center">

# 🚀 危险代码检测 · 推理服务部署

**Jarvis-Coder 模型的三种推理服务部署方案：CPU / CUDA / vLLM**

同一接口 `POST /detect` · 三种引擎形态 · 模型权重构建期直打镜像

</div>

---

本目录提供危险代码检测模型（[训练全流程见上级 README](../README.md)）的**在线推理服务**。
三种部署方案对上游暴露**完全一致的 HTTP 接口**，可互为替代、按场景切换。它们是
[Python Sandbox 平台](../../README.md) 双策略危险检测中"模型推理策略"的执行端。

## 📊 三种方案对比

| 维度 | ① CPU 版 | ② CUDA 版 | ③ vLLM 版 ⭐推荐 |
|------|---------|----------|-----------------|
| 推理引擎 | transformers | transformers | **vLLM**（PagedAttention） |
| 服务入口 | [`api_server.py`](api_server.py) | [`api_server.py`](api_server.py) | [`vllm_api_server.py`](vllm_api_server.py) |
| 推理核心 | [`infer_detect.py`](infer_detect.py) | [`infer_detect.py`](infer_detect.py) | 自包含（无独立核心文件） |
| 依赖清单 | [`requirements-cpu.txt`](requirements-cpu.txt) | [`requirements-cuda.txt`](requirements-cuda.txt) | [`requirements-vllm.txt`](requirements-vllm.txt) |
| 镜像文件 | [`Dockerfile.cpu`](Dockerfile.cpu) | [`Dockerfile.cuda`](Dockerfile.cuda) | [`Dockerfile.vllm`](Dockerfile.vllm) |
| 硬件要求 | 无 GPU（x86 任意机器） | NVIDIA GPU + CUDA 12.8 | NVIDIA GPU + CUDA 12.x，显存 ≥ 8GB |
| 操作系统 | 跨平台 | 跨平台 | **仅 Linux**（Windows 走 WSL2） |
| 并发吞吐 | 低（同步串行） | 中（同步串行 + GPU） | **高**（continuous batching） |
| 单次延迟 | 秒级 | 百毫秒级 | 百毫秒级 |
| 镜像体积 | 中（~2-3GB） | 大（torch cu128） | 最大（vLLM + torch） |
| 适用场景 | 功能验证 / CI / 离线冒烟 | 单卡小流量 / 过渡方案 | **生产环境主力** |

> 三者接口形态一致，切换方案不影响上游 Java 侧（[ModelCodeDetector](../../python-sandbox/src/main/java/io/github/sandbox/service/ModelCodeDetector.java)）与 compose 网络别名 `code-detect`，仅需改 compose profile。

## 🔌 统一接口契约

无论哪种方案，服务默认监听 `:8000`，仅暴露一个业务端点：

```
POST /detect
Content-Type: application/json

请求体  { "code": "<待检测的 Python/Shell 代码片段>" }
响应体  { "label": "SAFE" | "DANGEROUS", "raw_output": "<模型原始输出>" }
```

内部统一 Prompt 模板（与训练时严格一致，见 [`infer_detect.py`](infer_detect.py:22)）：

```
判断下面代码片段是否包含危险违规系统操作……只输出DANGEROUS或者SAFE，不要输出多余内容。
###代码片段：
{code}
###输出：
```

贪婪解码（`temperature=0`），`max_new_tokens=10`——输出仅 `DANGEROUS`/`SAFE` 几个 token。

冒烟测试（三种方案通用）：

```bash
curl -X POST http://localhost:8000/detect -H "Content-Type: application/json" ^
     -d "{\"code\":\"import os\nos.remove('/etc/shadow')\"}"
# => {"label":"DANGEROUS","raw_output":"DANGEROUS"}
```

## ⚙️ 配置（.env）

首次运行前复制示例并按需修改（[`copy .env.example .env`](.env.example)）：

| 变量 | 适用方案 | 默认值 | 说明 |
|------|---------|--------|------|
| `BASE_MODEL` | 全部 | `zjwan461/jarvis-coder` | 模型仓库名或本地路径 |
| `HTTP_PROXY` / `HTTPS_PROXY` | 全部 | 空 | 本地部署时下载模型的代理 |
| `MAX_MODEL_LEN` | 仅 vLLM | `1024` | 上下文总长（输入代码+输出），非越长越好 |
| `GPU_MEMORY_UTILIZATION` | 仅 vLLM | `0.85` | vLLM 预留显存比例 |

> **`MAX_MODEL_LEN` 说明**：模型输出只有几个 token，上下文窗口几乎全部消耗在**输入的待检测代码**上。默认 1024 足以覆盖常规代码片段（数百行）；代码普遍更短可降到 512 省显存，需检测超长代码再上调。

## 🛠️ 方案①：CPU 版部署（transformers）

**无需 GPU**，适合功能验证与 CI。默认 `device_map="auto"` 会落到 CPU。

```bash
# 裸机 / 虚拟环境
pip install -r requirements-cpu.txt
python api_server.py                     # 首次运行自动下载模型到 HF 缓存

# Docker（模型构建期直打镜像，运行零下载）
docker build -f Dockerfile.cpu -t code-detect:cpu .
docker run -d -p 8000:8000 -e BASE_MODEL=zjwan461/jarvis-coder code-detect:cpu
```

换模型需重建镜像：`docker build -f Dockerfile.cpu --build-arg BASE_MODEL=xxx -t code-detect:cpu .`

## 🛠️ 方案②：CUDA 版部署（transformers + GPU）

与 CPU 版同一入口，仅依赖换成 `requirements-cuda.txt`（torch cu128 wheel 自带 CUDA 运行时，
无需 CUDA 基础镜像）。宿主机需装 **nvidia-container-toolkit**。

```bash
# 裸机 / 虚拟环境
pip install -r requirements-cuda.txt
python api_server.py

# Docker
docker build -f Dockerfile.cuda -t code-detect:cuda .
docker run -d --gpus all -p 8000:8000 -e BASE_MODEL=zjwan461/jarvis-coder code-detect:cuda
```

> 校验 GPU 是否被 torch 识别：`python -c "import torch; print(torch.cuda.is_available())"`。

## 🛠️ 方案③：vLLM 版部署 ⭐（生产推荐）

vLLM 引擎，**continuous batching** 高并发吞吐。`vllm_api_server.py` 自包含（不依赖 `infer_detect.py`）。
官方仅支持 **Linux + NVIDIA GPU**；Windows 请在 **WSL2** 或 Docker 中运行。

```bash
# WSL2 / Linux 裸机
pip install -r requirements-vllm.txt
python vllm_api_server.py

# Docker（额外装 libgomp1 供 vLLM OpenMP 使用）
docker build -f Dockerfile.vllm -t code-detect:vllm .
docker run -d --gpus all -p 8000:8000 \
  -e BASE_MODEL=zjwan461/jarvis-coder \
  -e MAX_MODEL_LEN=1024 -e GPU_MEMORY_UTILIZATION=0.85 \
  code-detect:vllm
```

vLLM 自动拉取适配版本的 torch/transformers，故 `requirements-vllm.txt` 不单独固定 torch 版本。
离线吞吐吃紧时，可进一步改造为 `AsyncLLMEngine`（见文末演进方向）。

## 🐳 集成到全栈 docker-compose（推荐上线路径）

三个方案已作为带 **profile** 的可选服务集成到[仓库根 `docker-compose.yml`](../../docker-compose.yml)，
**默认均不启动**，三选一启用，并以网络别名 `code-detect` 供 `sandbox-api` 访问：

```bash
# 方式 A：根 .env 设置 COMPOSE_PROFILES=detect-vllm，然后
docker compose up -d --build

# 方式 B：命令行临时指定
docker compose --profile detect-cpu up -d --build    # 或 detect-cuda / detect-vllm
```

compose 侧要点：
- `build.args` 注入 `BASE_MODEL`，构建期下载与运行期配置共用同一变量（单一真相来源）
- 模型已打进镜像，**未再挂载 `hf-cache` 卷**（挂载会以空卷遮蔽镜像内模型目录）
- 三者共用宿主机端口 `DETECT_PORT`（默认 8000）——profile 三选一，不会冲突

## 🔗 与执行链路的衔接（CodeGuard）

推理服务起来后，还需在**管理端「系统设置」**打开模型策略开关，才真正接入 Python 执行前校验：

1. `codeguard.model.enabled = true`（策略总开关，默认关）
2. `codeguard.static.enabled = true`（静态校验策略，保留）
3. `codeguard.model.fail-open` —— 推理服务不可用时放行（`true`，可用性优先）/ 拒绝执行（`false`，安全优先）

`python-sandbox` 侧 [CodeGuardService](../../python-sandbox/src/main/java/io/github/sandbox/service/CodeGuardService.java)
每 60s 拉取一次开关；调用地址由 ENV `SANDBOX_CODEGUARD_DETECT_BASE_URL`（默认 `http://code-detect:8000`）决定。
CPU 版延迟较高，超过超时阈值（默认 5s）将按 fail-open 降级，**生产请用 vLLM 版**。

## ⚡ 常见问题

| 现象 | 处理 |
|------|------|
| 首次启动卡在下载模型 | 已在镜像内 `hf download` 预置；裸机部署可配 `HTTP_PROXY` 或 `HF_ENDPOINT=https://hf-mirror.com` |
| 私有 / gated 模型下载 401 | 构建机先 `hf auth login`，或改用 BuildKit secret 传 `HF_TOKEN` |
| vLLM 在 Windows 装不上 | vLLM 仅支持 Linux，请用 WSL2 或 Docker 运行 |
| vLLM 启动 OOM / 抢占过多显存 | 下调 `GPU_MEMORY_UTILIZATION`（如 0.7）或 `MAX_MODEL_LEN`（如 512） |
| CUDA 版 `torch.cuda.is_available()` 为 False | 检查 nvidia-container-toolkit、`--gpus all`、驱动版本 |
| `/detect` 返回 500 | 看容器日志：多为 `BASE_MODEL` 与镜像内构建时不一致导致加载失败 |
| CodeGuard 不生效 | 管理端确认 `codeguard.model.enabled=true`，等待 ≤60s；查 sandbox-api 日志 `CodeGuard 策略开关` |

## 🔗 相关文档

- [← 模型训练与推理总览](../README.md)（数据集 / QLoRA 微调 / 评估）
- [← Python Sandbox 主 README](../../README.md)（平台全貌 / compose 编排 / 双策略检测）
- [CodeGuardService](../../python-sandbox/src/main/java/io/github/sandbox/service/CodeGuardService.java)（执行前双策略编排）
- [ModelCodeDetector](../../python-sandbox/src/main/java/io/github/sandbox/service/ModelCodeDetector.java)（Java 侧 HTTP 客户端）

## 🧭 演进方向

- [ ] vLLM 改造为 `AsyncLLMEngine` / `vllm serve` OpenAI 兼容模式，提升在线并发吞吐
- [ ] 暴露 `/health` 供 compose healthcheck 与滚动探活
- [ ] 批处理端点 `POST /detect/batch`，一次多段代码降低往返开销
