"""
06_text_file_ops.py
===================
文本文件读写（write_file / read_file）：
- 写 JSON 配置并读回解析
- 写 CSV 数据并用 pandas 读取分析
- 写日志并模拟追加
- 中文 / 特殊字符测试
"""
import json

from common import sandbox_session, print_section


def demo_json_roundtrip() -> None:
    print_section("Demo A: JSON 文件读写")
    with sandbox_session() as (client, sid):
        config = {
            "service": "python-sandbox",
            "version": "1.0.0",
            "features": ["exec", "pip", "files"],
            "limits": {"max_containers": 10, "timeout_ms": 86400000},
        }
        client.write_file(sid, "/tmp/config.json", json.dumps(config, indent=2, ensure_ascii=False))
        print("✅ 已写入 /tmp/config.json")

        # 在沙箱内解析
        code = """
import json
with open('/tmp/config.json', encoding='utf-8') as f:
    cfg = json.load(f)
print('features count:', len(cfg['features']))
print('max_containers:', cfg['limits']['max_containers'])
"""
        res = client.exec_python(sid, code)
        print(res.stdout.strip())

        # 用 read_file 直接读回
        content = client.read_file(sid, "/tmp/config.json")
        print(f"\n读回文件长度: {len(content)} 字符")


def demo_csv_with_pandas() -> None:
    print_section("Demo B: CSV 文件 + pandas")
    with sandbox_session() as (client, sid):
        client.pip_install(sid, "pandas")

        csv_data = "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai\nCarol,28,Shenzhen\n"
        client.write_file(sid, "/tmp/people.csv", csv_data)

        code = """
import pandas as pd
df = pd.read_csv('/tmp/people.csv')
print(df.to_string(index=False))
print('---')
print('平均年龄:', df['age'].mean())
"""
        res = client.exec_python(sid, code)
        print(res.stdout.strip())


def demo_chinese_and_special() -> None:
    print_section("Demo C: 中文与特殊字符")
    with sandbox_session() as (client, sid):
        content = """中文测试 ✅
Emoji: 🚀🐳🎉
特殊符号: <> & " ' \\n
JSON-like: {"key": "value with \"quotes\""}
"""
        client.write_file(sid, "/tmp/utf8.txt", content)
        readback = client.read_file(sid, "/tmp/utf8.txt")
        assert readback == content, "内容不一致！"
        print("✅ 中文 / Emoji / 特殊字符 round-trip 成功")
        print(readback)


def demo_log_append() -> None:
    print_section("Demo D: 日志文件追加")
    with sandbox_session() as (client, sid):
        # 初次写入
        client.write_file(sid, "/tmp/app.log", "[INFO] start\n")
        # 通过 shell 模拟追加
        for i in range(3):
            client.exec_shell(sid, f"echo '[INFO] event {i}' >> /tmp/app.log")
        log = client.read_file(sid, "/tmp/app.log")
        print("日志内容：")
        print(log.rstrip())


if __name__ == "__main__":
    demo_json_roundtrip()
    demo_csv_with_pandas()
    demo_chinese_and_special()
    demo_log_append()
