"""
07_binary_file_ops.py
=====================
二进制文件上传下载（upload_file / download_file）：
- 上传本地图片到沙箱
- 让沙箱内 Python 处理图片（生成缩略图）
- 下载处理后的图片到本地
- 上传 / 下载纯二进制数据（如 numpy array 序列化）
"""
import os

import numpy as np

from common import sandbox_session, print_section


def upload_local_image(client, sid: str, local_path: str, container_path: str) -> None:
    with open(local_path, "rb") as f:
        client.upload_file(sid, container_path, f.read())
    print(f"✅ 上传 {local_path} -> {container_path}")


def demo_image_thumbnail() -> None:
    print_section("Demo A: 上传图片 -> 生成缩略图 -> 下载")
    with sandbox_session() as (client, sid):
        # 1) 本地构造一张 PNG（无需外部素材）
        from PIL import Image  # type: ignore
    # 注：PIL 在 sandbox 外不一定装了，所以这里本地用 numpy 生成一张占位图
    img_array = (np.random.rand(256, 256, 3) * 255).astype("uint8")
    local_src = "/tmp/demo_src.png"
    try:
        from PIL import Image as PILImage  # type: ignore
        PILImage.fromarray(img_array).save(local_src)
    except ImportError:
        # 退路：直接写最小 PNG（不依赖 PIL）
        local_src = "/tmp/demo_src.bin"
        img_array.tofile(local_src)

    with sandbox_session() as (client, sid):
        # 2) 上传到沙箱
        with open(local_src, "rb") as f:
            client.upload_file(sid, "/data/input.bin", f.read())
        print(f"✅ 上传源文件 -> /data/input.bin ({os.path.getsize(local_src)} bytes)")

        # 3) 在沙箱中处理（沙箱内一般装有 Pillow）
        client.pip_install(sid, "pillow")
        client.pip_install(sid, "numpy")
        code = """
from PIL import Image
import numpy as np
img = np.fromfile('/data/input.bin', dtype=np.uint8)
# 尝试按 PNG 解码
try:
    import io
    pil = Image.open(io.BytesIO(img.tobytes()))
    print('detected image:', pil.size, pil.mode)
    # 生成缩略图
    pil.thumbnail((64, 64))
    pil.save('/data/thumb.png')
    print('thumbnail saved')
except Exception as e:
    print('not a valid image, fallback to raw copy:', e)
    with open('/data/thumb.bin', 'wb') as f:
        f.write(open('/data/input.bin', 'rb').read())
"""
        res = client.exec_python(sid, code)
        print(res.stdout.strip())

        # 4) 下载回本地
        local_dst = "/tmp/demo_thumb.bin"
        for path, label in [("/data/thumb.png", local_dst), ("/data/thumb.bin", local_dst)]:
            try:
                data = client.download_file(sid, path)
                with open(local_dst, "wb") as f:
                    f.write(data)
                print(f"✅ 下载 {path} -> {local_dst} ({len(data)} bytes)")
                break
            except Exception as e:
                print(f"  skip {path}: {e}")


def demo_binary_roundtrip() -> None:
    print_section("Demo B: numpy 数组序列化")
    with sandbox_session() as (client, sid):
        # 本地构造一个数组并序列化为 bytes
        arr = np.arange(1000, dtype=np.float64).reshape(20, 50)
        buf = arr.tobytes()
        client.upload_file(sid, "/data/array.bin", buf)
        print(f"✅ 上传 numpy array ({arr.shape}, {arr.dtype}) -> /data/array.bin")

        # 沙箱端解析回来
        client.pip_install(sid, "numpy")
        code = """
import numpy as np
with open('/data/array.bin', 'rb') as f:
    buf = f.read()
arr = np.frombuffer(buf, dtype=np.float64).reshape(20, 50)
print('shape:', arr.shape, 'sum:', arr.sum(), 'mean:', arr.mean())
"""
        res = client.exec_python(sid, code)
        print(res.stdout.strip())


if __name__ == "__main__":
    demo_image_thumbnail()
    demo_binary_roundtrip()
