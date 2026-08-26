"""
异步客户端使用示例

演示如何使用 AsyncSandboxClient 进行异步操作。
需要安装 aiohttp: pip install python-sandbox-sdk[async]
"""

import asyncio
from python_sandbox_sdk import AsyncSandboxClient


async def main():
    # 使用 async with 自动管理连接
    async with AsyncSandboxClient("http://localhost:8080", "your-api-key") as client:
        # 创建会话
        session_id = await client.create_session()
        print(f"Created session: {session_id}")

        try:
            # 执行 Python 代码
            result = await client.exec_python(session_id, "print('Hello from async!')")
            print(f"Exit code: {result.exit_code}")
            print(f"Output: {result.stdout}")

            # 执行 Shell 命令
            result = await client.exec_shell(session_id, "echo 'Async shell works!'")
            print(f"Shell output: {result.stdout}")

            # 文件操作
            await client.write_file(session_id, "/tmp/test.txt", "Async file content")
            content = await client.read_file(session_id, "/tmp/test.txt")
            print(f"File content: {content}")

            # 健康检查
            is_healthy = await client.is_health()
            print(f"Service healthy: {is_healthy}")

        finally:
            # 清理会话
            await client.delete_session(session_id)
            print("Session deleted")


if __name__ == "__main__":
    asyncio.run(main())
