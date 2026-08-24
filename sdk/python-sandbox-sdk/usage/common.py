"""
公共工具：统一读取配置 + 通用上下文管理器。
"""
import os
import sys
from contextlib import contextmanager

from python_sandbox_sdk import SandboxClient


# 默认连接信息（与 python-sandbox/.env.example 保持一致）
DEFAULT_BASE_URL = "http://localhost:8080"
DEFAULT_API_KEY = "sandbox-secret-key"


def get_config() -> tuple[str, str]:
    """从环境变量读取 base_url 和 api_key，提供合理默认值。"""
    base_url = os.environ.get("SANDBOX_BASE_URL", DEFAULT_BASE_URL)
    api_key = os.environ.get("SANDBOX_API_KEY", DEFAULT_API_KEY)
    return base_url, api_key


def create_client() -> SandboxClient:
    """根据环境变量创建客户端实例。"""
    base_url, api_key = get_config()
    return SandboxClient(base_url, api_key)


@contextmanager
def sandbox_session():
    """
    自动管理会话生命周期的上下文管理器：
    - 进入时创建会话
    - 退出时无论是否异常都尝试关闭会话

    用法：
        with sandbox_session() as (client, session_id):
            client.exec_python(session_id, "print('hi')")
    """
    client = create_client()
    session_id = None
    try:
        session_id = client.create_session()
        yield client, session_id
    finally:
        if session_id is not None:
            try:
                client.delete_session(session_id)
            except Exception as exc:  # noqa: BLE001
                print(f"[cleanup] failed to delete session {session_id}: {exc}",
                      file=sys.stderr)
        client.close()


def print_section(title: str) -> None:
    """打印分节标题，让样例输出更易读。"""
    bar = "=" * 60
    print(f"\n{bar}\n{title}\n{bar}")
