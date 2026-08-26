"""
异步 Python Sandbox 客户端

使用 aiohttp 提供与 SandboxClient 相同的 API，但所有方法均为 async。

使用示例：
    >>> import asyncio
    >>> from python_sandbox_sdk import AsyncSandboxClient
    >>> async def main():
    ...     async with AsyncSandboxClient("http://localhost:8080", "your-api-key") as client:
    ...         session_id = await client.create_session()
    ...         result = await client.exec_python(session_id, "print('Hello!')")
    ...         print(result.stdout)
    >>> asyncio.run(main())
    Hello!
"""

import io
from typing import Optional

import aiohttp

from .dto import CommandResult
from .client import SandboxError, ApiRequestError


class AsyncSandboxClient:
    """
    异步 Python Sandbox 客户端

    所有网络请求方法均为 async，支持 async with 上下文管理器。

    Attributes:
        base_url: Sandbox API 基础 URL (如 http://localhost:8080)
        api_key: API 认证密钥
    """

    def __init__(
        self,
        base_url: str,
        api_key: str,
        session: Optional[aiohttp.ClientSession] = None,
        timeout: aiohttp.ClientTimeout = None,
    ):
        """
        创建异步客户端实例

        Args:
            base_url: Sandbox API 基础 URL
            api_key: API 认证密钥
            session: 可选的自定义 aiohttp.ClientSession 实例
            timeout: 默认请求超时（upload/download 有各自的超时）
        """
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self._timeout = timeout or aiohttp.ClientTimeout(total=60)
        self._external_session = session is not None
        self._session: Optional[aiohttp.ClientSession] = session

    async def _get_session(self) -> aiohttp.ClientSession:
        """获取或创建 aiohttp session（懒初始化）"""
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession(
                headers={
                    "X-Api-Key": self.api_key,
                    "Content-Type": "application/json",
                },
                timeout=self._timeout,
            )
            self._external_session = False
        return self._session

    # ==================== 会话管理 ====================

    async def create_session(self) -> str:
        """
        创建新的沙箱会话

        Returns:
            会话 ID
        """
        session = await self._get_session()
        async with session.post(f"{self.base_url}/api/sandbox/session") as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return data["sessionId"]

    async def delete_session(self, session_id: str) -> None:
        """
        删除会话并清理容器

        Args:
            session_id: 会话 ID
        """
        session = await self._get_session()
        url = f"{self.base_url}/api/sandbox/session/{session_id}"
        async with session.delete(url) as resp:
            await self._raise_if_error(resp)

    # ==================== 代码执行 ====================

    async def exec_python(self, session_id: str, code: str) -> CommandResult:
        """
        在沙箱中执行 Python 代码

        Args:
            session_id: 会话 ID
            code: Python 源代码

        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "code": code}
        session = await self._get_session()
        async with session.post(
            f"{self.base_url}/api/sandbox/exec/python", json=payload
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return CommandResult(
                data["exitCode"], data.get("stdout", ""), data.get("stderr", "")
            )

    async def exec_shell(self, session_id: str, command: str) -> CommandResult:
        """
        在沙箱中执行 Shell 命令

        Args:
            session_id: 会话 ID
            command: Shell 命令

        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "command": command}
        session = await self._get_session()
        async with session.post(
            f"{self.base_url}/api/sandbox/exec/shell", json=payload
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return CommandResult(
                data["exitCode"], data.get("stdout", ""), data.get("stderr", "")
            )

    # ==================== pip 包管理 ====================

    async def pip_install(self, session_id: str, package_name: str) -> CommandResult:
        """
        安装 Python 包

        Args:
            session_id: 会话 ID
            package_name: 包名（支持版本约束）

        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "pkg": package_name}
        session = await self._get_session()
        async with session.post(
            f"{self.base_url}/api/sandbox/pip/install", json=payload
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return CommandResult(
                data["exitCode"], data.get("stdout", ""), data.get("stderr", "")
            )

    async def pip_uninstall(
        self, session_id: str, package_name: str
    ) -> CommandResult:
        """
        卸载 Python 包

        Args:
            session_id: 会话 ID
            package_name: 包名

        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "pkg": package_name}
        session = await self._get_session()
        async with session.post(
            f"{self.base_url}/api/sandbox/pip/uninstall", json=payload
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return CommandResult(
                data["exitCode"], data.get("stdout", ""), data.get("stderr", "")
            )

    async def pip_list(self, session_id: str) -> str:
        """
        列出已安装的 Python 包

        Args:
            session_id: 会话 ID

        Returns:
            安装包列表文本
        """
        session = await self._get_session()
        async with session.get(
            f"{self.base_url}/api/sandbox/pip/list",
            params={"sessionId": session_id},
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return data["packages"]

    # ==================== 文件操作 ====================

    async def write_file(
        self, session_id: str, container_path: str, content: str
    ) -> None:
        """
        向沙箱写入文件内容

        Args:
            session_id: 会话 ID
            container_path: 容器内目标路径
            content: 文件内容
        """
        payload = {
            "sessionId": session_id,
            "path": container_path,
            "content": content,
        }
        session = await self._get_session()
        async with session.post(
            f"{self.base_url}/api/sandbox/file/write", json=payload
        ) as resp:
            await self._raise_if_error(resp)

    async def read_file(self, session_id: str, container_path: str) -> str:
        """
        读取沙箱中的文件内容

        Args:
            session_id: 会话 ID
            container_path: 容器内文件路径

        Returns:
            文件内容字符串
        """
        session = await self._get_session()
        async with session.get(
            f"{self.base_url}/api/sandbox/file/read",
            params={"sessionId": session_id, "path": container_path},
        ) as resp:
            await self._raise_if_error(resp)
            data = await resp.json()
            return data["content"]

    async def upload_file(
        self, session_id: str, container_path: str, data: bytes
    ) -> None:
        """
        上传二进制文件到沙箱

        Args:
            session_id: 会话 ID
            container_path: 容器内目标路径
            data: 文件字节数据
        """
        import os

        url = f"{self.base_url}/api/sandbox/file/upload"
        
        # 使用 BytesIO 避免临时文件和阻塞 I/O
        file_obj = io.BytesIO(data)
        form = aiohttp.FormData()
        form.add_field(
            "file",
            file_obj,
            filename=os.path.basename(container_path),
        )
        form.add_field("sessionId", session_id)
        form.add_field("path", container_path)

        # 上传使用独立的 session，避免继承主 session 的 Content-Type
        upload_timeout = aiohttp.ClientTimeout(total=120)
        async with aiohttp.ClientSession(timeout=upload_timeout) as upload_session:
            upload_session.headers.update({"X-Api-Key": self.api_key})
            async with upload_session.post(url, data=form) as resp:
                await self._raise_if_error(resp)

    async def download_file(self, session_id: str, container_path: str) -> bytes:
        """
        下载沙箱中的文件

        Args:
            session_id: 会话 ID
            container_path: 容器内文件路径

        Returns:
            文件字节数据
        """
        url = f"{self.base_url}/api/sandbox/file/download"
        params = {"sessionId": session_id, "path": container_path}

        download_timeout = aiohttp.ClientTimeout(total=60)
        async with aiohttp.ClientSession(timeout=download_timeout) as s:
            s.headers.update({"X-Api-Key": self.api_key})
            async with s.get(url, params=params) as resp:
                await self._raise_if_error(resp)
                return await resp.read()

    # ==================== 健康检查 ====================

    async def is_health(self) -> bool:
        """
        检查沙箱服务是否可用

        Returns:
            True 如果服务正常运行
        """
        try:
            health_timeout = aiohttp.ClientTimeout(total=5)
            async with aiohttp.ClientSession(timeout=health_timeout) as s:
                async with s.get(f"{self.base_url}/health") as resp:
                    return resp.status == 200
        except Exception:
            return False

    # ==================== 内部方法 ====================

    @staticmethod
    async def _raise_if_error(resp: aiohttp.ClientResponse) -> None:
        """如果响应状态码表示错误则抛出异常"""
        if resp.status >= 400:
            try:
                error_data = await resp.json()
                message = error_data.get("message", await resp.text())
            except Exception:
                message = await resp.text()
            raise ApiRequestError(resp.status, message)

    async def close(self) -> None:
        """关闭 HTTP 会话（仅关闭内部创建的 session）"""
        if self._session and not self._external_session:
            await self._session.close()
            self._session = None

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.close()
