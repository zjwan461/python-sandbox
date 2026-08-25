"""
Python Sandbox SDK
==================

提供简洁的 API 客户端以与 Python Sandbox 服务交互。

使用示例：
    >>> from python_sandbox_sdk import SandboxClient
    >>> client = SandboxClient("http://localhost:8080", "your-api-key")
    >>> session_id = client.create_session()
    >>> result = client.exec_python(session_id, "print('Hello!')")
    >>> print(result.stdout)
    Hello!
"""

import requests

from .dto import CommandResult


class SandboxError(Exception):
    """SDK 异常基类"""
    pass


class ApiKeyMissingError(SandboxError):
    """API Key 缺失错误"""
    pass


class ApiRequestError(SandboxError):
    """API 请求错误"""
    def __init__(self, status_code: int, message: str):
        self.status_code = status_code
        super().__init__(f"HTTP {status_code}: {message}")


class SandboxClient:
    """
    Python Sandbox 客户端
    
    Attributes:
        base_url: Sandbox API 基础 URL (如 http://localhost:8080)
        api_key: API 认证密钥
    """
    
    def __init__(self, base_url: str, api_key: str):
        """
        创建客户端实例
        
        Args:
            base_url: Sandbox API 基础 URL
            api_key: API 认证密钥
        """
        self.base_url = base_url.rstrip("/")
        self.api_key = api_key
        self._session = requests.Session()
        self._session.headers.update({
            "X-Api-Key": api_key,
            "Content-Type": "application/json"
        })
    
    # ==================== 会话管理 ====================
    
    def create_session(self) -> str:
        """
        创建新的沙箱会话
        
        Returns:
            会话 ID
        """
        resp = self._session.post(f"{self.base_url}/api/sandbox/session")
        self._raise_if_error(resp)
        return resp.json()["sessionId"]
    
    def delete_session(self, session_id: str) -> None:
        """
        删除会话并清理容器
        
        Args:
            session_id: 会话 ID
        """
        url = f"{self.base_url}/api/sandbox/session/{session_id}"
        resp = self._session.delete(url)
        self._raise_if_error(resp)
    
    # ==================== 代码执行 ====================
    
    def exec_python(self, session_id: str, code: str) -> CommandResult:
        """
        在沙箱中执行 Python 代码
        
        Args:
            session_id: 会话 ID
            code: Python 源代码
            
        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "code": code}
        resp = self._session.post(
            f"{self.base_url}/api/sandbox/exec/python",
            json=payload
        )
        self._raise_if_error(resp)
        data = resp.json()
        return CommandResult(data["exitCode"], data.get("stdout", ""), data.get("stderr", ""))
    
    def exec_shell(self, session_id: str, command: str) -> CommandResult:
        """
        在沙箱中执行 Shell 命令
        
        Args:
            session_id: 会话 ID
            command: Shell 命令
            
        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "command": command}
        resp = self._session.post(
            f"{self.base_url}/api/sandbox/exec/shell",
            json=payload
        )
        self._raise_if_error(resp)
        data = resp.json()
        return CommandResult(data["exitCode"], data.get("stdout", ""), data.get("stderr", ""))
    
    # ==================== pip 包管理 ====================
    
    def pip_install(self, session_id: str, package_name: str) -> CommandResult:
        """
        安装 Python 包
        
        Args:
            session_id: 会话 ID
            package_name: 包名（支持版本约束）
            
        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "pkg": package_name}
        resp = self._session.post(
            f"{self.base_url}/api/sandbox/pip/install",
            json=payload
        )
        self._raise_if_error(resp)
        data = resp.json()
        return CommandResult(data["exitCode"], data.get("stdout", ""), data.get("stderr", ""))
    
    def pip_uninstall(self, session_id: str, package_name: str) -> CommandResult:
        """
        卸载 Python 包
        
        Args:
            session_id: 会话 ID
            package_name: 包名
            
        Returns:
            命令执行结果
        """
        payload = {"sessionId": session_id, "pkg": package_name}
        resp = self._session.post(
            f"{self.base_url}/api/sandbox/pip/uninstall",
            json=payload
        )
        self._raise_if_error(resp)
        data = resp.json()
        return CommandResult(data["exitCode"], data.get("stdout", ""), data.get("stderr", ""))
    
    def pip_list(self, session_id: str) -> str:
        """
        列出已安装的 Python 包
        
        Args:
            session_id: 会话 ID
            
        Returns:
            安装包列表文本
        """
        resp = self._session.get(
            f"{self.base_url}/api/sandbox/pip/list",
            params={"sessionId": session_id}
        )
        self._raise_if_error(resp)
        return resp.json()["packages"]
    
    # ==================== 文件操作 ====================
    
    def write_file(self, session_id: str, container_path: str, content: str) -> None:
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
            "content": content
        }
        resp = self._session.post(
            f"{self.base_url}/api/sandbox/file/write",
            json=payload
        )
        self._raise_if_error(resp)
    
    def read_file(self, session_id: str, container_path: str) -> str:
        """
        读取沙箱中的文件内容
        
        Args:
            session_id: 会话 ID
            container_path: 容器内文件路径
            
        Returns:
            文件内容字符串
        """
        resp = self._session.get(
            f"{self.base_url}/api/sandbox/file/read",
            params={"sessionId": session_id, "path": container_path}
        )
        self._raise_if_error(resp)
        return resp.json()["content"]
    
    def upload_file(self, session_id: str, container_path: str, data: bytes) -> None:
        """
        上传二进制文件到沙箱
        
        Args:
            session_id: 会话 ID
            container_path: 容器内目标路径
            data: 文件字节数据
        """
        import tempfile
        import os
        
        # 写入临时文件
        fd, tmp_path = tempfile.mkstemp(prefix="sandbox_upload_")
        try:
            # os.fdopen 接管了 fd 的所有权，with 块结束时会自动关闭 fd
            with os.fdopen(fd, 'wb') as f:
                f.write(data)
            
            with open(tmp_path, 'rb') as f:
                files = {'file': (os.path.basename(container_path), f)}
                data_params = {'sessionId': session_id, 'path': container_path}
                
                # 发送 multipart 请求（requests 会自动设置正确的 Content-Type）
                url = f"{self.base_url}/api/sandbox/file/upload"
                # 使用新的 session 避免继承主 session 的 Content-Type: application/json
                with requests.Session() as upload_session:
                    upload_session.headers.update({"X-Api-Key": self.api_key})
                    resp = upload_session.post(url, files=files, data=data_params, timeout=120)
                    self._raise_if_error(resp)
        finally:
            if os.path.exists(tmp_path):
                os.unlink(tmp_path)
    
    def download_file(self, session_id: str, container_path: str) -> bytes:
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
        
        # 使用新的 session 避免继承主 session 的 Content-Type: application/json
        with requests.Session() as s:
            s.headers.update({"X-Api-Key": self.api_key})
            resp = s.get(url, params=params, timeout=60)
            self._raise_if_error(resp)
            return resp.content
    
    # ==================== 健康检查 ====================
    
    def is_health(self) -> bool:
        """
        检查沙箱服务是否可用
        
        Returns:
            True 如果服务正常运行
        """
        try:
            resp = requests.get(f"{self.base_url}/health", timeout=5)
            return resp.status_code == 200
        except Exception:
            return False
    
    # ==================== 内部方法 ====================
    
    @staticmethod
    def _raise_if_error(resp: requests.Response) -> None:
        """如果响应状态码表示错误则抛出异常"""
        if resp.status_code >= 400:
            try:
                error_data = resp.json()
                message = error_data.get("message", resp.text)
            except Exception:
                message = resp.text
            raise ApiRequestError(resp.status_code, message)
    
    def close(self) -> None:
        """关闭 HTTP 会话"""
        self._session.close()
    
    def __enter__(self):
        return self
    
    def __exit__(self, exc_type, exc_val, exc_tb):
        self.close()
