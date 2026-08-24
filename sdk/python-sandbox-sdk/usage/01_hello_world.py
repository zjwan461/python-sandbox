"""
01_hello_world.py
=================
最简入门示例：
1. 健康检查（不需要 API Key）
2. 创建沙箱会话
3. 执行一段 Python 代码
4. 删除会话

适合第一次接触 SDK 的同学快速跑通链路。
"""
from common import create_client, print_section


def main() -> None:
    print_section("Step 1: 健康检查（无需鉴权）")
    client = create_client()
    healthy = client.is_health()
    print(f"服务健康状态: {healthy}")
    if not healthy:
        raise SystemExit("❌ Sandbox 服务未启动，请先 docker-compose up -d")

    try:
        print_section("Step 2: 创建沙箱会话")
        session_id = client.create_session()
        print(f"✅ 会话已创建: {session_id}")

        print_section("Step 3: 执行 Python 代码")
        code = "print('Hello from sandbox!'); import sys; print('Python', sys.version.split()[0])"
        result = client.exec_python(session_id, code)
        print(f"exit_code: {result.exit_code}")
        print(f"stdout:\n{result.stdout.rstrip()}")
        if result.stderr:
            print(f"stderr:\n{result.stderr.rstrip()}")
    finally:
        print_section("Step 4: 清理会话")
        client.delete_session(session_id)
        print(f"✅ 会话已关闭: {session_id}")
        client.close()


if __name__ == "__main__":
    main()
