# Sandbox SDK

Python Sandbox 的官方客户端 SDK，提供 Java 和 Python 两种语言的封装。

> 提示：`execPython` 在服务端受 **CodeGuard 双策略危险检测**保护（静态校验 + 可选模型推理），
> 危险代码会收到 HTTP 403 `SECURITY_VIOLATION`，SDK 侧表现为对应异常，请做好错误处理
> （见 `usage/11_error_handling.py` 示例）。策略说明详见 [AI 危险代码检测](ai-detect.md)。

## 快速开始

### Java SDK

#### 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>io.github.sandbox</groupId>
    <artifactId>sdk-java</artifactId>
    <version>1.0.0</version>
</dependency>
```

#### 基本使用

```java
import io.github.sandbox.sdk.SandboxClient;
import io.github.sandbox.sdk.dto.SandboxResponse;

public class Main {
    public static void main(String[] args) throws Exception {
        // 创建客户端
        SandboxClient client = new SandboxClient(
            "http://localhost:8080",
            "sandbox-secret-key"
        );
        
        // 检查服务状态
        if (client.isHealth()) {
            System.out.println("Sandbox is ready!");
        }
        
        // 创建会话
        String sessionId = client.createSession();
        System.out.println("Session created: " + sessionId);
        
        // 安装包
        SandboxResponse installResult = client.pipInstall(sessionId, "requests");
        System.out.println("Install exit code: " + installResult.getExitCode());
        
        // 执行 Python 代码
        SandboxResponse result = client.execPython(sessionId, 
            "import requests\nprint(requests.__version__)"
        );
        System.out.println("Output: " + result.getStdout());
        
        // 执行 Shell 命令
        SandboxResponse shellResult = client.execShell(sessionId, "pip list");
        System.out.println(shellResult.getStdout());
        
        // 文件操作
        client.writeFile(sessionId, "/tmp/hello.txt", "Hello from Java SDK!");
        String content = client.readFile(sessionId, "/tmp/hello.txt");
        System.out.println(content);
        
        // 清理会话
        client.deleteSession(sessionId);
    }
}
```

#### 高级用法 - try-with-resources

```java
try (SandboxClient client = new SandboxClient(url, apiKey)) {
    String sessionId = client.createSession();
    try {
        client.execPython(sessionId, "print('Working!')");
    } finally {
        client.deleteSession(sessionId);
    }
} // 连接自动关闭
```

---

### Python SDK

#### 安装

从 PyPI（发布后）：

```bash
pip install python-sandbox-sdk

# 安装异步支持（可选）
pip install python-sandbox-sdk[async]
```

或从本地源码：

```bash
cd sdk/python-sandbox-sdk
pip install -e .
```

#### 基本使用

```python
from python_sandbox_sdk import SandboxClient

# 创建客户端（支持上下文管理器）
with SandboxClient("http://localhost:8080", "sandbox-secret-key") as client:
    # 检查服务状态
    if client.is_health():
        print("Sandbox is ready!")
    
    # 创建会话
    session_id = client.create_session()
    print(f"Session created: {session_id}")
    
    # 安装包
    result = client.pip_install(session_id, "requests")
    print(f"Install exit code: {result.exit_code}")
    
    # 执行 Python 代码
    result = client.exec_python(session_id, 
        "import requests\nprint(requests.__version__)"
    )
    print(f"Output:\n{result.stdout}")
    
    # 执行 Shell 命令
    result = client.exec_shell(session_id, "pip list")
    print(result.stdout)
    
    # 文件操作
    client.write_file(session_id, "/tmp/hello.txt", "Hello from Python SDK!")
    content = client.read_file(session_id, "/tmp/hello.txt")
    print(content)
    
    # 上传下载文件
    file_data = b"Binary content here"
    client.upload_file(session_id, "/tmp/binary.bin", file_data)
    downloaded = client.download_file(session_id, "/tmp/binary.bin")
    assert downloaded == file_data
    
    # 清理会话
    client.delete_session(session_id)
```

#### 异步客户端

Python SDK 还提供完整的异步客户端 `AsyncSandboxClient`，基于 `aiohttp` 实现：

```python
import asyncio
from python_sandbox_sdk import AsyncSandboxClient

async def main():
    async with AsyncSandboxClient("http://localhost:8080", "sandbox-secret-key") as client:
        # 检查服务状态
        if await client.is_health():
            print("Sandbox is ready!")
        
        # 创建会话
        session_id = await client.create_session()
        print(f"Session created: {session_id}")
        
        # 执行 Python 代码
        result = await client.exec_python(session_id, "print('Async works!')")
        print(f"Output: {result.stdout}")
        
        # 文件操作
        await client.write_file(session_id, "/tmp/async.txt", "Async content")
        content = await client.read_file(session_id, "/tmp/async.txt")
        print(content)
        
        # 清理会话
        await client.delete_session(session_id)

asyncio.run(main())
```

异步客户端的优势：
- 所有方法均为 `async`，支持 `await` 调用
- 支持 `async with` 上下文管理器
- 适合与 `asyncio` 生态集成，实现高并发场景

#### 不使用上下文管理器

```python
client = SandboxClient("http://localhost:8080", "sandbox-secret-key")
try:
    session_id = client.create_session()
    try:
        client.exec_python(session_id, "print('Working!')")
    finally:
        client.delete_session(session_id)
finally:
    client.close()
```

---

## API 参考

### 通用方法

| 方法 | 说明 |
|------|------|
| `createSession()` / `create_session()` | 创建新的沙箱会话 |
| `deleteSession(id)` / `delete_session(id)` | 删除会话并清理容器 |
| `isHealth()` / `is_health()` | 检查服务是否可用 |

### 代码执行

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `execPython(id, code)` / `exec_python(id, code)` | sessionId, Python 代码 | CommandResult | 执行 Python 代码 |
| `execShell(id, cmd)` / `exec_shell(id, cmd)` | sessionId, Shell 命令 | CommandResult | 执行 Shell 命令 |

### pip 包管理

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `pipInstall(id, pkg)` / `pip_install(id, pkg)` | sessionId, 包名 | CommandResult | 安装包 |
| `pipUninstall(id, pkg)` / `pip_uninstall(id, pkg)` | sessionId, 包名 | CommandResult | 卸载包 |
| `pipList(id)` / `pip_list(id)` | sessionId | String | 列出已安装包 |

### 文件操作

| 方法 | 参数 | 返回 | 说明 |
|------|------|------|------|
| `writeFile(id, path, content)` / `write_file(id, path, content)` | sessionId, 路径, 内容 | void | 写入文件 |
| `readFile(id, path)` / `read_file(id, path)` | sessionId, 路径 | String | 读取文本文件 |
| `uploadFile(id, path, data)` / `upload_file(id, path, data)` | sessionId, 路径, 字节数据 | void | 上传二进制文件 |
| `downloadFile(id, path)` / `download_file(id, path)` | sessionId, 路径 | bytes | 下载文件 |

---

## 异常处理

### Java

```java
try {
    client.execPython(sessionId, "...");
} catch (IOException e) {
    // HTTP 请求错误（超时、连接失败等）
    System.err.println("Request failed: " + e.getMessage());
}
```

### Python

```python
from python_sandbox_sdk import ApiRequestError

try:
    client.exec_python(session_id, "...")
except ApiRequestError as e:
    # HTTP 4xx/5xx 错误
    print(f"API Error ({e.status_code}): {e}")
except requests.RequestException as e:
    # 网络错误（超时、连接失败等）
    print(f"Network error: {e}")
```

---

## 环境变量

SDK 配置也可以通过环境变量指定（适用于 docker-compose 部署场景）：

```bash
export SANDBOX_API_KEY=my-secret-key
export SANDBOX_BASE_URL=http://my-server:8080
```

---

## 许可证

MIT License
