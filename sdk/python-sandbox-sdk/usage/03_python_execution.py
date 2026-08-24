"""
03_python_execution.py
======================
Python 代码执行场景：
- 基础语法：变量、控制流、函数、类
- 异常捕获（沙箱内异常不会冒泡到客户端）
- 多段代码在同一会话中累积状态
- 执行耗时较长的代码
"""
import time

from common import sandbox_session, print_section


def demo_basic_syntax() -> None:
    print_section("Demo A: 基础语法")
    with sandbox_session() as (client, sid):
        code = """
import json

def greet(name: str) -> str:
    return f"Hello, {name}!"

class Counter:
    def __init__(self):
        self.n = 0
    def inc(self):
        self.n += 1

c = Counter()
for i in range(3):
    c.inc()

result = {
    "greeting": greet("Sandbox"),
    "counter": c.n,
    "python": True,
}
print(json.dumps(result, ensure_ascii=False))
"""
        res = client.exec_python(sid, code)
        print(f"exit_code: {res.exit_code}")
        print(f"stdout: {res.stdout.strip()}")


def demo_exception() -> None:
    print_section("Demo B: 异常处理（不会冒泡到客户端）")
    with sandbox_session() as (client, sid):
        code = """
try:
    1 / 0
except ZeroDivisionError as e:
    print(f"caught: {e}")

# 异常被处理后，后续代码仍可正常执行
print("still alive")
"""
        res = client.exec_python(sid, code)
        print(f"exit_code: {res.exit_code}（应仍为 0）")
        print(f"stdout: {res.stdout.strip()}")


def demo_state_accumulation() -> None:
    print_section("Demo C: 多段代码累积状态")
    with sandbox_session() as (client, sid):
        for snippet in [
            "data = []",
            "data.append(1); data.append(2)",
            "print(sum(data))",
        ]:
            res = client.exec_python(sid, snippet)
            print(f"  >>> {snippet}")
            print(f"      {res.stdout.strip() or '(no output)'}")


def demo_long_running() -> None:
    print_section("Demo D: 长任务（时间统计）")
    with sandbox_session() as (client, sid):
        code = """
import time
start = time.time()
time.sleep(2)
print(f"elapsed: {time.time() - start:.2f}s")
"""
        t0 = time.time()
        res = client.exec_python(sid, code)
        wall = time.time() - t0
        print(f"客户端耗时: {wall:.2f}s")
        print(f"exit_code: {res.exit_code}")
        print(f"沙箱输出: {res.stdout.strip()}")


if __name__ == "__main__":
    demo_basic_syntax()
    demo_exception()
    demo_state_accumulation()
    demo_long_running()
