"""
08_data_analysis.py
===================
数据分析实战：
- 拉取远程 CSV 数据（requests）
- 用 pandas 清洗分析
- 生成 matplotlib 图表并下载回本地查看
"""
from common import sandbox_session, print_section


def demo_remote_csv_analysis() -> None:
    print_section("Demo: 远程 CSV 数据分析 + 图表导出")
    with sandbox_session() as (client, sid):
        # 1) 装包
        print(">>> 安装依赖")
        for pkg in ("requests", "pandas", "matplotlib"):
            r = client.pip_install(sid, pkg)
            assert r.success, f"pip install {pkg} failed: {r.stderr}"
            print(f"  ✅ {pkg}")

        # 2) 拉数据 + 分析 + 画图
        code = r"""
import io
import requests
import pandas as pd
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt

# 1) 拉一份公开 CSV：iris 数据集
url = 'https://raw.githubusercontent.com/mwaskom/seaborn-data/master/iris.csv'
resp = requests.get(url, timeout=10)
print('GET', url, '->', resp.status_code, len(resp.content), 'bytes')

# 2) 解析
df = pd.read_csv(io.StringIO(resp.text))
print('shape:', df.shape)
print(df.head(3).to_string(index=False))

# 3) 简单聚合
print('\n按 species 平均值：')
print(df.groupby('species').mean(numeric_only=True).round(2).to_string())

# 4) 画图
fig, ax = plt.subplots(figsize=(6, 4))
for species, sub in df.groupby('species'):
    ax.scatter(sub['sepal_length'], sub['petal_length'], label=species, alpha=0.7)
ax.set_xlabel('sepal_length'); ax.set_ylabel('petal_length')
ax.set_title('Iris Sepal vs Petal Length')
ax.legend(); ax.grid(alpha=0.3)
fig.tight_layout()
fig.savefig('/tmp/iris_scatter.png', dpi=100)
print('chart saved ->', '/tmp/iris_scatter.png')

# 5) 同步把聚合结果写成 JSON 方便客户端读取
import json
agg = df.groupby('species').mean(numeric_only=True).round(2).to_dict(orient='index')
with open('/tmp/iris_agg.json', 'w') as f:
    json.dump(agg, f, indent=2)
print('agg json saved')
"""
        res = client.exec_python(sid, code)
        print("\n=== 沙箱输出 ===")
        print(res.stdout)
        if not res.success:
            print("stderr:", res.stderr)
            return

        # 3) 下载图表到本地
        png_bytes = client.download_file(sid, "/tmp/iris_scatter.png")
        local_png = "/tmp/iris_scatter.png"
        with open(local_png, "wb") as f:
            f.write(png_bytes)
        print(f"\n✅ 图表已下载 -> {local_png} ({len(png_bytes)} bytes)")

        # 4) 读回 JSON 聚合结果
        agg_text = client.read_file(sid, "/tmp/iris_agg.json")
        print("\n=== 聚合结果 ===")
        print(agg_text)


if __name__ == "__main__":
    demo_remote_csv_analysis()
