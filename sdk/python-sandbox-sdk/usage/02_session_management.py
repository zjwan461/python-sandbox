"""
02_session_management.py
========================
会话管理：
- 演示多个会话的并行创建与独立销毁
- 演示「复用同一会话」以保持状态（pip 装包、文件写入后再读取）
- 演示 try/finally 确保即使执行失败也能正确清理
"""
from common import create_client, print_section


def demo_independent_sessions() -> None:
    """多个会话彼此隔离。"""
    print_section("Demo A: 多个独立会话")
    client = create_client()
    try:
        ids = [client.create_session() for _ in range(3)]
        print(f"创建了 3 个会话: {ids}")

        # 每个会话独立跑一段代码，互不影响
        for sid, payload in zip(ids, ["a=1", "b=2", "c=3"]):
            res = client.exec_python(sid, payload)
            print(f"  [{sid[:20]}...] {payload!r} -> {res.stdout.strip()}")

        # 全部清理
        for sid in ids:
            client.delete_session(sid)
        print("✅ 3 个会话已全部关闭")
    finally:
        client.close()


def demo_session_reuse() -> None:
    """复用同一会话，状态在多次执行之间保持。"""
    print_section("Demo B: 复用同一会话（状态保持）")
    client = create_client()
    sid = None
    try:
        sid = client.create_session()

        # 第一次执行：定义变量
        res = client.exec_python(sid, "x = 42")
        assert res.success, "第一次执行失败"
        print("✅ 定义变量 x = 42")

        # 第二次执行：使用变量
        res = client.exec_python(sid, "print(f'x is {x}, x*2 = {x*2}')")
        print(res.stdout.strip())

        # 第三次执行：修改变量
        res = client.exec_python(sid, "x += 8; print(f'x now = {x}')")
        print(res.stdout.strip())
    finally:
        if sid:
            client.delete_session(sid)
            print(f"✅ 会话 {sid} 已关闭")
        client.close()


def demo_cleanup_on_error() -> None:
    """即使执行报错，会话也能被正确清理。"""
    print_section("Demo C: 异常路径下也能清理")
    client = create_client()
    sid = None
    try:
        sid = client.create_session()
        # 故意制造一个 NameError
        res = client.exec_python(sid, "print(undefined_variable)")
        print(f"exit_code = {res.exit_code}（预期非 0）")
        print(f"stderr: {res.stderr.strip()}")
    finally:
        if sid:
            client.delete_session(sid)
            print(f"✅ 即使出错，会话 {sid} 仍被正确关闭")
        client.close()


if __name__ == "__main__":
    demo_independent_sessions()
    demo_session_reuse()
    demo_cleanup_on_error()
