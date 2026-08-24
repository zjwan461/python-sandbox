"""Setuptools configuration for python-sandbox-sdk.

本文件同时作为：
1. 兼容 setup.py 的安装入口（`pip install .`）
2. 长描述来源 — PyPI 包页面会展示 README.md 的渲染结果
"""
import os
from pathlib import Path

from setuptools import setup, find_packages


def _read_readme() -> str:
    """读取 README.md 作为 long_description。

    README.md 位于 sdk/python-sandbox-sdk/README.md，
    通过相对路径回溯到仓库根再定位的方式不可靠，因此固定使用当前文件同级目录。
    """
    here = Path(__file__).resolve().parent
    readme_path = here / "README.md"
    if readme_path.exists():
        return readme_path.read_text(encoding="utf-8")
    # 兜底：仅返回一行标题，避免发布到 PyPI 时缺失描述
    return "python-sandbox-sdk: Python SDK for Python Sandbox API"


setup(
    name="python-sandbox-sdk",
    version="1.0.0",
    description="Python SDK for Python Sandbox API - Run Python code, shell commands, and manage packages in Docker containers",
    long_description=_read_readme(),
    long_description_content_type="text/markdown",
    author_email="826935261@qq.com",
    url="https://github.com/zjwan461/python-sandbox",
    project_urls={
        "Repository": "https://github.com/zjwan461/python-sandbox",
        "Documentation": "https://github.com/zjwan461/python-sandbox#readme",
        "Issues": "https://github.com/zjwan461/python-sandbox/issues",
        "Changelog": "https://github.com/zjwan461/python-sandbox/blob/main/CHANGELOG.md",
        "Usage Examples": "https://github.com/zjwan461/python-sandbox/tree/main/sdk/python-sandbox-sdk/usage",
    },
    license="MIT",
    packages=find_packages(where="."),
    package_dir={"": "."},
    python_requires=">=3.8",
    install_requires=["requests>=2.31.0"],
    classifiers=[
        "Development Status :: 4 - Beta",
        "Intended Audience :: Developers",
        "License :: OSI Approved :: MIT License",
        "Programming Language :: Python :: 3",
        "Programming Language :: Python :: 3.8",
        "Programming Language :: Python :: 3.9",
        "Programming Language :: Python :: 3.10",
        "Programming Language :: Python :: 3.11",
        "Programming Language :: Python :: 3.12",
        "Topic :: Software Development :: Libraries :: Python Modules",
        "Topic :: System :: Sandboxing",
    ],
    keywords="sandbox docker python security shell",
)
