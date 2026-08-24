"""
05_pip_packages.py
==================
pip 包管理：
- 安装（指定版本 / 不指定版本）
- 列出已安装包
- 卸载
- 验证：装包后能否 import
"""
from common import sandbox_session, print_section


def demo_install_and_use() -> None:
    print_section("Demo A: 安装 requests 并使用")
    with sandbox_session() as (client, sid):
        # 1) 安装
        print(">>> pip install requests")
        res = client.pip_install(sid, "requests==2.31.0")
        print(f"exit={res.exit_code}")
        for line in res.stdout.splitlines()[-3:]:
            print(f"  {line}")

        # 2) 验证可被 import
        code = """
import requests
print('requests version:', requests.__version__)
print('GET https://httpbin.org/get ->', requests.get('https://httpbin.org/get', timeout=5).status_code)
"""
        res = client.exec_python(sid, code)
        print(f"\n>>> 验证 import: exit={res.exit_code}")
        print(res.stdout.strip())


def demo_list_packages() -> None:
    print_section("Demo B: 列出已安装包")
    with sandbox_session() as (client, sid):
        client.pip_install(sid, "pyyaml")
        client.pip_install(sid, "python-dateutil")
        packages = client.pip_list(sid)
        print("已安装的包：")
        for line in packages.splitlines():
            if any(k in line.lower() for k in ("requests", "yaml", "dateutil")):
                print(f"  ⭐ {line}")
            else:
                print(f"     {line}")


def demo_uninstall() -> None:
    print_section("Demo C: 卸载包")
    with sandbox_session() as (client, sid):
        client.pip_install(sid, "pyyaml")
        # 先确认存在
        res = client.exec_python(sid, "import yaml; print(yaml.__version__)")
        print(f"装包后: {res.stdout.strip()}")

        # 卸载
        res = client.pip_uninstall(sid, "pyyaml")
        print(f"\n>>> pip uninstall pyyaml: exit={res.exit_code}")

        # 再尝试 import 应失败
        res = client.exec_python(sid, "import yaml")
        print(f"\n卸载后 import: exit={res.exit_code}")
        print(f"stderr: {res.stderr.strip()}")


def demo_with_version_constraint() -> None:
    print_section("Demo D: 复杂版本约束")
    with sandbox_session() as (client, sid):
        # pip 支持的语法都可用
        constraints = [
            "numpy>=1.20",
            "pandas<2.0",
            "scikit-learn~=1.3",
        ]
        for c in constraints:
            print(f">>> pip install {c}")
            res = client.pip_install(sid, c)
            print(f"   exit={res.exit_code}, last line: {res.stdout.strip().splitlines()[-1] if res.stdout else ''}")


if __name__ == "__main__":
    demo_install_and_use()
    demo_list_packages()
    demo_uninstall()
    demo_with_version_constraint()
