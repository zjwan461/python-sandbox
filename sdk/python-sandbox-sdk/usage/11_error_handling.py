"""
11_error_handling.py
=====================
错误处理全景：
- 鉴权失败（错误 API Key）
- 会话不存在 / 已过期
- 执行超时（沙箱内 hang 住 — 服务端默认同步等待）
- Shell 黑名单命令
- 超过 max-containers 限制
- 网络/连接错误

所有错误都以 ApiRequestError 或 Python 原生异常形式呈现。
"""
from python_sandbox_sdk import ApiRequestError, SandboxError

from common import create_client, sandbox_session, print_section


def demo_auth_error() -> None:
    print_section("Demo A: 鉴权失败（错误 API Key）")
    from common import DEFAULT_BASE_URL
    client = SandboxClient(DEFAULT_BASE_URL, "wrong-api-key")
    try:
        client.create_session()
        print("⚠️  预期应失败，但调用成功了")
    except ApiRequestError as e:
        print(f"✅ 捕获鉴权异常: HTTP {e.status_code} - {str(e)[:120]}")
    finally:
        client.close()


def demo_session_not_found() -> None:
    print_section("Demo B: 会话不存在")
    client = create_client()
    try:
        client.exec_python("non-existent-session-xxx", "print(1)")
    except ApiRequestError as e:
        print(f"✅ 捕获: HTTP {e.status_code} - {str(e)[:120]}")
    finally:
        client.close()


def demo_blacklist_via_shell() -> None:
    print_section("Demo C: Shell 黑名单命令")
    with sandbox_session() as (client, sid):
        for cmd in ["rm -rf /", "sudo cat /etc/shadow", "shutdown -h now"]:
            try:
                client.exec_shell(sid, cmd)
                print(f"  ⚠️  未拦截: {cmd}")
            except ApiRequestError as e:
                print(f"  ✅ 已拦截: {cmd}  -> HTTP {e.status_code}")


def demo_max_containers() -> None:
    print_section("Demo D: 超过 max-containers 限制")
    client = create_client()
    sids = []
    try:
        # 反复尝试创建直到失败
        while True:
            try:
                sids.append(client.create_session())
            except ApiRequestError as e:
                print(f"✅ 在第 {len(sids) + 1} 次创建时达到上限: {str(e)[:120]}")
                break
    finally:
        for sid in sids:
            try:
                client.delete_session(sid)
            except Exception:
                pass
        client.close()


def demo_network_error() -> None:
    print_section("Demo E: 网络错误（连接错误地址）")
    client = SandboxClient("http://localhost:1", "any")  # 端口不存在
    try:
        client.is_health()
        print("⚠️  预期连接失败")
    except SandboxError as e:
        print(f"✅ 捕获连接错误: {type(e).__name__}: {e}")
    except OSError as e:
        # requests 抛 ConnectionError，requests.RequestException 也可能
        print(f"✅ 捕获 OSError: {type(e).__name__}: {e}")
    finally:
        client.close()


def demo_python_exception_in_sandbox() -> None:
    print_section("Demo F: 沙箱内 Python 异常（非错误，但需识别）")
    with sandbox_session() as (client, sid):
        res = client.exec_python(sid, "raise ValueError('boom')")
        print(f"exit_code: {res.exit_code}（非 0，但 HTTP 仍 200）")
        print(f"stderr: {res.stderr.strip()[:200]}")


def demo_resource_cleanup_on_exception() -> None:
    print_section("Demo G: 客户端异常后资源仍被清理")
    try:
        with sandbox_session() as (client, sid):
            print(f"创建会话: {sid}")
            raise RuntimeError("客户端侧故意抛错")
    except RuntimeError as e:
        print(f"✅ 捕获: {e}")
    print("✅ 沙箱会话已被自动 delete（容器已销毁）")


if __name__ == "__main__":
    demo_auth_error()
    demo_session_not_found()
    demo_blacklist_via_shell()
    demo_python_exception_in_sandbox()
    demo_resource_cleanup_on_exception()
    # 下面两个 demo 涉及端口占用 / 长时间连接，留给用户按需启用：
    # demo_max_containers()
    # demo_network_error()
