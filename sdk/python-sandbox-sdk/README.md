# python-sandbox-sdk

[![Python](https://img.shields.io/badge/python-≥3.8-blue)](https://www.python.org)
[![License: MIT](https://img.shields.io/badge/license-MIT-green)](LICENSE)
[![Docker](https://img.shields.io/badge/docker-required-blue)](https://www.docker.com)

Python Sandbox 官方 Python SDK — 在隔离的 Docker 容器中执行 Python 代码、Shell 命令、管理 pip 包、读写文件。

对应后端：[`python-sandbox`](https://github.com/zjwan461/python-sandbox)（Spring Boot 3 + Java 17）。

## ✨ 特性

- 🚀 **简洁 API**：10 个方法覆盖会话、代码执行、Shell、pip、文件读写、文件上传下载、健康检查
- 🔌 **上下文管理器**：自动管理 HTTP 连接与会话生命周期
- 🛡️ **异常体系**：独立的 `ApiRequestError` / `SandboxError` 类型
- 🧵 **线程安全**：`requests.Session` 复用，支持 `ThreadPoolExecutor` 并发
- 🐍 **Python ≥ 3.8**：仅依赖 `requests>=2.31.0`

## 📦 安装

```bash
# 方式 A：从 PyPI 安装（推荐）
pip install python-sandbox-sdk

# 方式 B：从源码安装
git clone https://github.com/zjwan461/python-sandbox.git
cd python-sandbox/sdk/python-sandbox-sdk
pip install -e .
```

## 🚀 快速开始

### 0. 启动后端服务

参考 [`python-sandbox/README.md`](https://github.com/zjwan461/python-sandbox/blob/main/python-sandbox/README.md) 启动：

```bash
cd python-sandbox
cp .env.example .env
docker-compose up -d --build
curl http://localhost:8080/health
```

### 1. 最简示例

```python
from python_sandbox_sdk import SandboxClient

with SandboxClient("http://localhost:8080", "sandbox-secret-key") as client:
    if not client.is_health():
        raise SystemExit("Sandbox not ready")

    session_id = client.create_session()
    try:
        result = client.exec_python(session_id, "print('Hello from sandbox!')")
        print(result.stdout)              # Hello from sandbox!
        print(result.exit_code)           # 0
        print(result.success)             # True
    finally:
        client.delete_session(session_id)
```

### 2. 安装并使用第三方包

```python
client.pip_install(session_id, "requests==2.31.0")
result = client.exec_python(session_id, """
import requests
print(requests.get('https://httpbin.org/get', timeout=5).status_code)
""")
```

### 3. 文件读写

```python
# 文本
client.write_file(session_id, "/tmp/config.json", '{"key": "value"}')
content = client.read_file(session_id, "/tmp/config.json")

# 二进制
client.upload_file(session_id, "/tmp/image.png", b"\\x89PNG...")
data = client.download_file(session_id, "/tmp/image.png")
```

## 📚 使用样例（覆盖全部 API 与典型场景）

完整可运行样例见 [`usage/`](usage/) 目录：

| 编号 | 场景 | 覆盖 API |
|---|---|---|
| [`01_hello_world.py`](usage/01_hello_world.py) | 入门：连通性 + Hello World | `is_health`, `create_session`, `exec_python`, `delete_session` |
| [`02_session_management.py`](usage/02_session_management.py) | 会话管理：独立 / 复用 / 异常清理 | `create_session`, `delete_session` |
| [`03_python_execution.py`](usage/03_python_execution.py) | Python 执行：语法 / 异常 / 状态 / 长任务 | `exec_python` |
| [`04_shell_execution.py`](usage/04_shell_execution.py) | Shell 执行：基础 + 黑名单 + 安全路径 | `exec_shell` |
| [`05_pip_packages.py`](usage/05_pip_packages.py) | pip 管理：安装 / 卸载 / 列表 / 版本约束 | `pip_install`, `pip_uninstall`, `pip_list` |
| [`06_text_file_ops.py`](usage/06_text_file_ops.py) | 文本文件：JSON / CSV / 中文 / 日志 | `write_file`, `read_file` |
| [`07_binary_file_ops.py`](usage/07_binary_file_ops.py) | 二进制：图片缩略图 + numpy 序列化 | `upload_file`, `download_file` |
| [`08_data_analysis.py`](usage/08_data_analysis.py) | 数据分析实战：requests + pandas + matplotlib | 综合 |
| [`09_long_running.py`](usage/09_long_running.py) | 长任务：进度回报 + 异常安全 | `exec_python`, `exec_shell` |
| [`10_concurrent_sessions.py`](usage/10_concurrent_sessions.py) | 并发：`ThreadPoolExecutor` + 上限约束 | 多会话并行 |
| [`11_error_handling.py`](usage/11_error_handling.py) | 错误全景：鉴权 / 会话 / 黑名单 / 网络 | 全 API 错误路径 |

运行样例：

```bash
cd usage/
export SANDBOX_API_KEY="sandbox-secret-key"

python 01_hello_world.py
for f in 0[1-9]_*.py 1[0-1]_*.py; do python "$f"; done
```

## 🔧 API 速查

| 方法 | 返回 | 说明 |
|---|---|---|
| `is_health()` | `bool` | 健康检查（无需 API Key） |
| `create_session()` | `str` | 创建沙箱会话 |
| `delete_session(session_id)` | `None` | 删除会话并清理容器 |
| `exec_python(session_id, code)` | `CommandResult` | 执行 Python 代码 |
| `exec_shell(session_id, command)` | `CommandResult` | 执行 Shell 命令 |
| `pip_install(session_id, pkg)` | `CommandResult` | 安装 pip 包（支持版本约束） |
| `pip_uninstall(session_id, pkg)` | `CommandResult` | 卸载 pip 包 |
| `pip_list(session_id)` | `str` | 列出已安装包 |
| `write_file(session_id, path, content)` | `None` | 写文本文件 |
| `read_file(session_id, path)` | `str` | 读文本文件 |
| `upload_file(session_id, path, data)` | `None` | 上传二进制文件 |
| `download_file(session_id, path)` | `bytes` | 下载文件 |

`CommandResult` 字段：`exit_code`, `stdout`, `stderr`, `success`（property）, `combined_output`（property）。

## ⚙️ 环境变量

为避免硬编码，样例通过环境变量注入连接信息：

| 变量 | 默认值 | 说明 |
|---|---|---|
| `SANDBOX_BASE_URL` | `http://localhost:8080` | Sandbox 服务地址 |
| `SANDBOX_API_KEY` | `sandbox-secret-key` | API 鉴权密钥（与后端 `SANDBOX_API_KEY` 保持一致） |

## 🛡️ 异常处理

```python
from python_sandbox_sdk import SandboxClient, ApiRequestError, SandboxError

try:
    client.exec_python(session_id, "print(1)")
except ApiRequestError as e:
    # 后端返回的 4xx / 5xx（鉴权失败、会话不存在、黑名单、容器上限等）
    print(f"API Error: HTTP {e.status_code} - {e}")
except SandboxError as e:
    # SDK 抛出的通用错误
    print(f"SDK Error: {e}")
except OSError as e:
    # 网络错误（连接失败、超时等）
    print(f"Network Error: {e}")
```

## 🧵 并发使用

```python
from concurrent.futures import ThreadPoolExecutor
# 每个任务使用独立的 client + session（线程安全）
with ThreadPoolExecutor(max_workers=5) as pool:
    futures = [pool.submit(run_one_task, i) for i in range(5)]
    for f in futures:
        print(f.result())
```

> ⚠️ 并发数受后端 `sandbox.max-containers`（默认 10）约束。

## 📦 打包发布

```bash
# 安装构建工具
pip install build twine

# 构建
cd sdk/python-sandbox-sdk
python -m build

# 上传到 TestPyPI
twine upload --repository testpypi dist/*

# 上传到 PyPI
twine upload dist/*
```

发布前请修改 `pyproject.toml` 中的 `version` 与 `urls` 字段。

## 🔗 相关项目

- 后端服务：[`python-sandbox/`](https://github.com/zjwan461/python-sandbox)
- Java SDK：[`sdk/java-sandbox-sdk/`](https://github.com/zjwan461/python-sandbox/tree/main/sdk/java-sandbox-sdk)
- SDK 总览：[`sdk/README.md`](https://github.com/zjwan461/python-sandbox/tree/main/sdk)

## 📄 许可证

MIT License
