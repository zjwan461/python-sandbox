"""
04_shell_execution.py
=====================
Shell 命令执行：
- 基础命令：ls / cat / echo / grep / pipe
- 触发黑名单（rm -rf /） → 演示 SandboxException
- 规避黑名单：在受限路径下 rm -rf 是允许的
"""
from python_sandbox_sdk import ApiRequestError

from common import sandbox_session, print_section


def demo_basic_commands() -> None:
    print_section("Demo A: 基础 Shell 命令")
    with sandbox_session() as (client, sid):
        commands = [
            "pwd",
            "ls -la /tmp",
            "echo 'hello' | tr 'a-z' 'A-Z'",
            "python --version",
            "df -h / | head -3",
        ]
        for cmd in commands:
            res = client.exec_shell(sid, cmd)
            print(f"$ {cmd}")
            print(f"  exit={res.exit_code}")
            if res.stdout:
                for line in res.stdout.rstrip().splitlines():
                    print(f"  | {line}")
            if res.stderr:
                for line in res.stderr.rstrip().splitlines():
                    print(f"  ! {line}")
            print()


def demo_blacklist() -> None:
    print_section("Demo B: 触发 Shell 黑名单（应被拦截）")
    with sandbox_session() as (client, sid):
        dangerous_cmds = [
            "rm -rf /",
            "sudo echo hi",
            "shutdown -h now",
        ]
        for cmd in dangerous_cmds:
            try:
                res = client.exec_shell(sid, cmd)
                print(f"  ⚠️  未被拦截（异常）: {cmd} -> exit={res.exit_code}")
            except ApiRequestError as e:
                # 后端会把黑名单命令转换为 400 错误
                print(f"  ✅ 已拦截: {cmd}")
                print(f"     HTTP {e.status_code} - {str(e)[:120]}")


def demo_safe_cleanup() -> None:
    print_section("Demo C: 安全路径下的清理（应允许）")
    with sandbox_session() as (client, sid):
        # 黑名单只保护 /、/etc、/root 等关键路径，/tmp 下是可清理的
        client.exec_shell(sid, "mkdir -p /tmp/demo && echo data > /tmp/demo/a.txt")
        before = client.exec_shell(sid, "ls /tmp/demo")
        print(f"清理前: {before.stdout.strip()}")

        res = client.exec_shell(sid, "rm -rf /tmp/demo")
        print(f"rm -rf /tmp/demo -> exit={res.exit_code}")

        after = client.exec_shell(sid, "ls /tmp/demo 2>&1 || echo gone")
        print(f"清理后: {after.stdout.strip()}")


if __name__ == "__main__":
    demo_basic_commands()
    demo_blacklist()
    demo_safe_cleanup()
