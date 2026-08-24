"""Setuptools configuration for compatibility."""
from setuptools import setup, find_packages

# This file is kept for legacy tools that still use setup.py
setup(
    name="python-sandbox-sdk",
    version="1.0.0",
    packages=find_packages(where="."),
    package_dir={"": "."},
    install_requires=["requests>=2.31.0"],
)
