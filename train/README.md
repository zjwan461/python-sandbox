<div align="center">

# 🤖 Jarvis-Coder · 危险代码检测模型

**基于 Qwen2.5-Coder LoRA 微调的代码危险性判定模型训练与推理全流程**

`数据集合成` → `QLoRA 微调` → `LoRA 合并` → `测试集评估` → `推理服务` → `容器化上线`

产出模型：`zjwan461/jarvis-coder`（HuggingFace）· 输出标签：`DANGEROUS` / `SAFE`

</div>

---

本目录是 [Python Sandbox](../README.md) 平台的双策略危险检测中 **模型推理策略** 的训练侧，
覆盖从数据集合成到推理服务容器化上线的完整链路。训练完成的模型经
[`python-sandbox` 的 CodeGuardService](../python-sandbox/src/main/java/io/github/sandbox/service/CodeGuardService.java)
在执行 Python 代码前调用，与静态黑名单校验互为补充。

## 🗂️ 目录结构

```
train/
├── .env.example             # 训练/生成侧环境变量示例（百炼 API Key 等）
├── pyproject.toml           # uv 工程依赖（torch cu128 + peft + trl 等）
├── check_env.py             # GPU 环境自检（算力 / bf16 支持）
│
├── generate_dataset.py      # 🧪 数据集合成：调用百炼 API 生成三类样本
├── check_dataset.py         # 🔍 数据集质检：格式/标签校验 + 坏样本剥离
├── train_sft_lora.py        # 🎯 QLoRA SFT 微调主脚本（含训练后自动合并）
├── train_lora.py            #    LoRA 通用训练参考脚本（手工流程示例）
├── evaluate_testset.py      # 📊 测试集离线评估（准确率 + 错误样本导出）
│
├── datasets/                # 数据集（JSONL）
│   ├── raw_generated.jsonl  #   原始生成结果
│   ├── train.jsonl          #   训练集（~90%）
│   ├── val.jsonl            #   验证集（参与训练期 epoch 评估）
│   ├── test.jsonl           #   测试集（🚫 全程不参与训练/调参，仅最终评估）
│   └── bad_samples.jsonl    #   质检剥离的坏样本
│
├── output/                  # 训练产物（.gitignore）
│   ├── jarvis-coder-lora/   #   LoRA adapter checkpoints
│   └── jarvis-coder/        #   合并后完整模型（推理/评估加载目录）
│
└── infer/                   # 🚀 推理服务（FastAPI）
    ├── infer_detect.py      #   transformers 推理核心（init/detect/release）
    ├── api_server.py        #   transformers 版 API（POST /detect）
    ├── vllm_api_server.py   #   vLLM 版 API（POST /detect，高吞吐）
    ├── requirements-{cpu,cuda,vllm}.txt
    ├── Dockerfile.{cpu,cuda,vllm}   # 模型权重构建期直打镜像
    └── .env.example         #   推理侧环境变量（BASE_MODEL 等）
```

## 🧪 第一步：环境准备

要求：Python 3.11–3.12，NVIDIA GPU（显存参考下表），CUDA 12.8 驱动栈。

```bash
cd train
# 本工程使用 uv 管理依赖（torch 系走 pytorch-cu128 专用源，见 pyproject.toml）
uv sync

# GPU 自检：确认算力与 bf16 支持
uv run python check_env.py
```

配置 `.env`（参考 [.env.example](.env.example)）：

| 变量 | 说明 |
|------|------|
| `MODEL_API_KEY` | 阿里云百炼 API Key（数据集合成用，模型如 `qwen-plus`） |
| `BASE_URL` | OpenAI 兼容端点，默认 dashscope |
| `SEMAPHORE_VALUE` | 样本生成并发数，默认 20 |
| `CUDA_VISIBLE_DEVICES` | 训练用卡，默认 `0` |

## 📐 第二步：数据集合成与质检

```bash
# 生成三类样本（默认合计 3400 条），自动备份旧数据集目录
uv run python generate_dataset.py

# 质检：校验字段/标签合法性，坏样本剥离到 datasets/bad_samples.jsonl
uv run python check_dataset.py
```

三类样本设计（可在 [`generate_dataset.py`](generate_dataset.py) 配置区调整数量）：

| 类型 | 默认数量 | 标签 | 意图 |
|------|---------|------|------|
| 危险真实操作 | 1200 | `DANGEROUS` | 真实包含删除系统文件/改配置/反弹 shell/提权等操作，且使用变量传参、字符串拼接等混淆技巧 |
| 普通业务代码 | 1200 | `SAFE` | 正常业务逻辑，无高危系统操作 |
| Hard-Negative | 1000 | `SAFE` | **迷惑样本**：危险命令出现在注释/仅赋值打印的字符串中，绝不实际执行——防止模型"见词色变"误杀 |

样本格式（与训练/推理的 Prompt 模板严格一致）：

```json
{"instruction": "判断下面代码片段是否包含危险违规系统操作：...只输出 DANGEROUS 或者 SAFE...",
 "input": "import os\nos.remove('/etc/shadow')",
 "output": "DANGEROUS"}
```

## 🎯 第三步：QLoRA 微调

```bash
uv run python train_sft_lora.py
```

[`train_sft_lora.py`](train_sft_lora.py) 关键设定（配置区集中可调）：

| 项 | 默认值 | 说明 |
|----|--------|------|
| `BASE_MODEL` | `Qwen/Qwen2.5-Coder-1.5B-Instruct` | 基座模型 |
| 量化 | 4bit NF4 + bf16 计算 + double quant | QLoRA，显存占用大幅压缩 |
| LoRA | `r=8, alpha=16, target=[q_proj, v_proj]` | 仅训注意力投影 |
| `MAX_SEQ_LENGTH` | 1536 | 按显存调整（见下表） |
| `BATCH_SIZE × GRAD_ACCUM` | 4 × 4 = 16 | 总有效 batch |
| `EPOCHS / LR` | 3 / 2e-4 | epoch 级 eval + save |
| `DO_MERGE_AFTER_TRAIN` | `True` | 训练完自动合并 LoRA → `output/jarvis-coder/` |

脚本内置显存-参数速查表（5060Ti → A100 共 20 档），OOM 时打开
`gradient_checkpointing=True` 并下调 batch。

> [`train_lora.py`](train_lora.py) 为通用 LoRA 手工训练流程的参考脚本（非本模型主线），
> 主线流程只认 `train_sft_lora.py`。

## 📊 第四步：测试集离线评估

```bash
uv run python evaluate_testset.py
```

- 加载**合并后**的完整模型 `output/jarvis-coder/`，`temperature=0` 逐条推理 `datasets/test.jsonl`
- 输出总体准确率 + DANGEROUS/SAFE 分类召回，错误样本写入 `output/test_error_samples.jsonl`
- ⚠️ 纪律：test 集全程不参与训练与调参，仅在此步使用一次

## 🚀 第五步：推理服务

模型上传 HuggingFace（如 `zjwan461/jarvis-coder`）后即可脱离训练环境部署。
`infer/` 提供两种引擎、同一接口（`POST /detect`，请求 `{"code": "..."}`，响应
`{"label": "SAFE|DANGEROUS", "raw_output": "..."}`）：

| 方案 | 入口 | 依赖 | 适用 |
|------|------|------|------|
| transformers | [`api_server.py`](infer/api_server.py) + [`infer_detect.py`](infer/infer_detect.py) | `requirements-{cpu,cuda}.txt` | CPU 验证 / 单卡小流量 |
| vLLM | [`vllm_api_server.py`](infer/vllm_api_server.py)（自包含） | `requirements-vllm.txt` | GPU 生产（continuous batching 高吞吐） |

```bash
cd infer
copy .env.example .env        # 设置 BASE_MODEL（可含代理 HTTP_PROXY/HTTPS_PROXY）

# transformers 版
pip install -r requirements-cuda.txt
python api_server.py          # :8000

# vLLM 版（Linux/WSL2；MAX_MODEL_LEN 默认 1024 —— 输出仅几个 token，上下文留给输入代码）
pip install -r requirements-vllm.txt
python vllm_api_server.py     # :8000
```

冒烟测试：

```bash
curl -X POST http://localhost:8000/detect -H "Content-Type: application/json" ^
     -d "{\"code\":\"import os\nos.remove('/etc/shadow')\"}"
# => {"label":"DANGEROUS","raw_output":"DANGEROUS"}
```

> 三种部署方案的详细对比、依赖与上线细节，见
> [infer/README.md · 推理服务部署](infer/README.md)。

## 🐳 第六步：容器化上线

三个 Dockerfile 对应三种形态，**模型权重在构建期 `hf download` 直接打入镜像**，
运行零下载（换模型需 `--build-arg BASE_MODEL=xxx` 重建）：

| Dockerfile | 引擎 | GPU | 备注 |
|-----------|------|-----|------|
| `Dockerfile.cpu` | transformers | ❌ | 功能验证 |
| `Dockerfile.cuda` | transformers | ✅ | torch cu128 wheel 自带运行时 |
| `Dockerfile.vllm` | vLLM | ✅ | 需 libgomp1；显存建议 ≥8GB |

```bash
cd infer
docker build -f Dockerfile.vllm --build-arg BASE_MODEL=zjwan461/jarvis-coder -t code-detect:vllm .
```

全栈编排已集成到仓库根 [`docker-compose.yml`](../docker-compose.yml)：三个服务
（`detect-cpu` / `detect-cuda` / `detect-vllm`）以 profile 三选一启用，默认不启动；
启动后以网络别名 `code-detect` 供 `sandbox-api` 访问，详见
[根 README · 双策略危险检测](../README.md#-双策略危险检测codeguard)。

## ⚡ 常见问题

| 现象 | 处理 |
|------|------|
| 训练 OOM | 打开 `gradient_checkpointing`；`BATCH_SIZE=1` 并加大 `GRAD_ACCUM`；下调 `MAX_SEQ_LENGTH` |
| 安培卡（30 系）训练慢 | bf16 硬件性能弱，优先 fp16 训练 |
| 模型把 `print("rm -rf /")` 误判 DANGEROUS | 补充 Hard-Negative 样本重训（这是该类误杀的唯一解） |
| 推理服务下载模型慢 | 设置 `HF_ENDPOINT=https://hf-mirror.com` 或配置 `HTTP(S)_PROXY` |
| vLLM 启动显存不足 | 下调 `GPU_MEMORY_UTILIZATION`（默认 0.85）或 `MAX_MODEL_LEN` |
| CodeGuard 策略不生效 | 确认管理端 `codeguard.model.enabled=true` 且等待 ≤60s 定时拉取；查 sandbox-api 日志 `CodeGuard 策略开关` |

## 🔗 相关文档

- [← Python Sandbox 主 README](../README.md)（平台全貌 / compose 编排 / CodeGuard 链路）
- [CodeGuardService](../python-sandbox/src/main/java/io/github/sandbox/service/CodeGuardService.java)（执行前双策略编排）
- [ModelCodeDetector](../python-sandbox/src/main/java/io/github/sandbox/service/ModelCodeDetector.java)（Java 侧调用客户端）
- [003-codeguard.sql](../cross-cutting/database/seed/003-codeguard.sql)（策略开关 sys_config 种子）
