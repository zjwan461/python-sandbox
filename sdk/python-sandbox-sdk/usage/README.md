# Python Sandbox SDK - 使用样例

本目录包含 `python-sandbox-sdk` 的完整使用样例，覆盖 SDK 暴露的所有 API 与典型业务场景。

## 前置准备

### 1. 启动 Sandbox 服务

参考根目录 `README.md` 与 `python-sandbox/README.md` 启动后端：

```bash
cd ../../../python-sandbox
cp .env.example .env
docker-compose up -d --build
# 验证
curl http://localhost:8080/health
```

### 2. 安装 SDK

```bash
# 方式 A：从源码安装（推荐开发阶段）
cd ..
pip install -e .

# 方式 B：从 PyPI 安装（待发布）
pip install python-sandbox-sdk
```

### 3. 配置连接信息

所有样例都通过环境变量读取配置，避免硬编码：

```bash
export SANDBOX_BASE_URL="http://localhost:8080"
export SANDBOX_API_KEY="sandbox-secret-key"   # 与 python-sandbox/.env 中保持一致
```

## 样例列表

| 编号 | 文件 | 场景 | 覆盖 API |
|---|---|---|---|
| 01 | [`01_hello_world.py`](01_hello_world.py) | 入门：连通性验证 + Hello World | `is_health`, `create_session`, `exec_python`, `delete_session` |
| 02 | [`02_session_management.py`](02_session_management.py) | 会话生命周期管理（复用 / 批量） | `create_session`, `delete_session` |
| 03 | [`03_python_execution.py`](03_python_execution.py) | Python 代码执行（语法 / 异常 / 状态保持） | `exec_python` |
| 04 | [`04_shell_execution.py`](04_shell_execution.py) | Shell 命令执行（含黑名单触发） | `exec_shell` |
| 05 | [`05_pip_packages.py`](05_pip_packages.py) | pip 包管理（安装 / 卸载 / 列表） | `pip_install`, `pip_uninstall`, `pip_list` |
| 06 | [`06_text_file_ops.py`](06_text_file_ops.py) | 文本文件读写（CSV / JSON / 日志） | `write_file`, `read_file` |
| 07 | [`07_binary_file_ops.py`](07_binary_file_ops.py) | 二进制文件（图片 / 数据集） | `upload_file`, `download_file` |
| 08 | [`08_data_analysis.py`](08_data_analysis.py) | 数据分析实战：requests + pandas + matplotlib | 综合：`pip_install`, `exec_python`, `upload_file`, `download_file` |
| 09 | [`09_long_running.py`](09_long_running.py) | 长任务处理（上下文管理器 + 异常安全） | `create_session`, `exec_python`, `exec_shell`, `delete_session` |
| 10 | [`10_concurrent_sessions.py`](10_concurrent_sessions.py) | 并发会话（`ThreadPoolExecutor`） | 多会话并行 `exec_python` |
| 11 | [`11_error_handling.py`](11_error_handling.py) | 错误处理（超时 / 鉴权 / 黑名单 / 限流） | 全 API 错误路径 |

## 运行样例

```bash
cd sdk/python-sandbox-sdk/usage

# 单个样例
python 01_hello_world.py

# 全部样例按顺序执行
for f in 0[1-9]_*.py 1[0-1]_*.py; do
    echo "=========================================="
    echo "Running: $f"
    echo "=========================================="
    python "$f" || echo "❌ $f failed"
done
```

## 通用约定

- **base_url / api_key** 通过环境变量 `SANDBOX_BASE_URL`、`SANDBOX_API_KEY` 注入，参考 `common.py`。
- 每个样例都使用 `with SandboxClient(...) as client:` 上下文管理器，确保 HTTP 会话与沙箱容器都被正确释放。
- 涉及黑名单命令的样例会同时演示「触发」与「规避」两种情况，便于参考。
