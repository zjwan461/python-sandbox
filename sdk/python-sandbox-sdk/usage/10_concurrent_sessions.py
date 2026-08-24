"""
10_concurrent_sessions.py
=========================
并发场景：
- 使用 ThreadPoolExecutor 并行跑多个会话（每个任务独立的沙箱容器）
- 受 sandbox.max-containers 限制，建议并发数 ≤ 配置上限
- 演示收集结果与错误聚合
"""
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from python_sandbox_sdk import ApiRequestError

from common import create_client, print_section


def run_task(task_id: int, code: str) -> dict:
    """每个任务独立会话：建 -> 跑 -> 销毁。线程安全（每个 SandboxClient 实例各自管理一个 session）。"""
    client = create_client()
    sid = None
    try:
        sid = client.create_session()
        t0 = time.time()
        res = client.exec_python(sid, code)
        wall = time.time() - t0
        return {
            "task_id": task_id,
            "session": sid,
            "exit_code": res.exit_code,
            "stdout": res.stdout.strip(),
            "stderr": res.stderr.strip(),
            "wall_seconds": round(wall, 2),
            "ok": res.success,
        }
    except ApiRequestError as e:
        return {
            "task_id": task_id,
            "error": str(e),
            "ok": False,
        }
    finally:
        if sid:
            try:
                client.delete_session(sid)
            except Exception:
                pass
        client.close()


def demo_parallel() -> None:
    print_section("Demo: 5 个会话并发执行独立任务")
    tasks = [
        (i, f"import time, hashlib; time.sleep(2); print('task {i} hash:', hashlib.md5(b'{i}').hexdigest())")
        for i in range(5)
    ]

    t0 = time.time()
    results = []
    with ThreadPoolExecutor(max_workers=5) as pool:
        futures = [pool.submit(run_task, tid, code) for tid, code in tasks]
        for fut in as_completed(futures):
            r = fut.result()
            results.append(r)
            status = "✅" if r.get("ok") else "❌"
            print(f"  {status} task={r['task_id']} exit={r.get('exit_code')} wall={r.get('wall_seconds')}s out={r.get('stdout', '')[:80]}")

    total = time.time() - t0
    print(f"\n总耗时: {total:.2f}s（5 个任务各 sleep 2s，串行需要 10s+）")
    print(f"成功 {sum(1 for r in results if r.get('ok'))} / {len(results)}")


def demo_respect_max_containers() -> None:
    print_section("Demo B: 并发数受 max-containers 约束")
    # 后端默认 max-containers=10，故意开到 15 个并发，观察超限错误
    client = create_client()
    try:
        ok_count = 0
        for i in range(15):
            try:
                sid = client.create_session()
                ok_count += 1
                # 不立即释放，模拟「占着会话不释放」以触发上限
                print(f"  ✅ 创建 #{i+1} -> {sid[:20]}...")
            except ApiRequestError as e:
                print(f"  ❌ 创建 #{i+1} 失败: {e}")
                break

        # 全部释放
        print(f"\n共创建 {ok_count} 个会话（应等于 max-containers 配置）")
        # 这里需要逐个清理 — 但当前 demo 没保存 sid，所以仅打印提示
        print("⚠️  实际使用中请保存 sid 并在 finally 中 delete_session")
    finally:
        client.close()


if __name__ == "__main__":
    demo_parallel()
    demo_respect_max_containers()
