"""
09_long_running.py
==================
长任务 / 资源受限场景下的稳健写法：
- 使用上下文管理器确保退出时一定清理
- 在 try/except 中分别处理：业务异常、SDK 异常、其他异常
- 演示进度回报（沙箱内 print -> 客户端 stdout 流式观察）
"""
import time

from python_sandbox_sdk import ApiRequestError

from common import sandbox_session, print_section


def demo_long_compute() -> None:
    print_section("Demo A: 长时间 CPU 任务")
    with sandbox_session() as (client, sid):
        code = """
import time
# 模拟一个跑 6 秒、每步汇报进度的任务
for i in range(6):
    print(f'step {i+1}/6 done', flush=True)
    time.sleep(1)
print('all done')
"""
        t0 = time.time()
        res = client.exec_python(sid, code)
        wall = time.time() - t0
        print(f"\n客户端耗时: {wall:.1f}s")
        print("沙箱分步输出:")
        print(res.stdout.strip())


def demo_with_cleanup_safety() -> None:
    print_section("Demo B: 即便中途抛异常也要清理会话")
    client_factory = None  # 仅用于演示，实际直接用 sandbox_session
    try:
        with sandbox_session() as (client, sid):
            print(f"会话: {sid}")
            # 故意触发沙箱内 NameError
            client.exec_python(sid, "print(undefined)")
            print("⚠️  上面一行应当打印 NameError，但脚本继续运行")
            # 这里再制造一个 SDK 异常
            raise ApiRequestError(500, "人为触发的客户端异常")
    except ApiRequestError as e:
        print(f"✅ 捕获到 SDK 异常: {e}")
    print("✅ 退出 with 块后，会话已被自动 delete")


def demo_manual_session_with_progress() -> None:
    print_section("Demo C: 手动管理会话 + 进度文件")
    from common import create_client
    client = create_client()
    sid = None
    try:
        sid = client.create_session()
        # 启动一个「假后台任务」，写到 progress.txt
        client.exec_python(
            sid,
            """
import threading, time
def work():
    for i in range(5):
        with open('/tmp/progress.txt', 'w') as f:
            f.write(f'{i+1}/5')
        time.sleep(0.5)
    with open('/tmp/progress.txt', 'w') as f:
        f.write('done')
t = threading.Thread(target=work)
t.start()
""",
        )
        # 业务侧每 0.5s 拉一次进度
        for _ in range(15):
            try:
                p = client.read_file(sid, "/tmp/progress.txt")
                print(f"  progress: {p}")
                if p == "done":
                    break
            except Exception:
                pass
            time.sleep(0.5)
    finally:
        if sid:
            client.delete_session(sid)
            print("✅ 会话已清理")
        client.close()


if __name__ == "__main__":
    demo_long_compute()
    demo_with_cleanup_safety()
    demo_manual_session_with_progress()
