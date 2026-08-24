"""
数据传输对象定义
"""
from dataclasses import dataclass
from typing import Optional


@dataclass
class CommandResult:
    """命令执行结果"""
    exit_code: int
    stdout: str = ""
    stderr: str = ""

    @property
    def success(self) -> bool:
        """是否执行成功"""
        return self.exit_code == 0

    @property
    def combined_output(self) -> str:
        """合并输出"""
        if not self.stderr:
            return self.stdout
        return f"{self.stdout}\n{self.stderr}"


@dataclass
class SessionResponse:
    """会话响应"""
    session_id: str
    message: Optional[str] = None
