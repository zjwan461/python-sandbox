import logging
import os
import threading
import time
from contextlib import asynccontextmanager

from dotenv import load_dotenv
from fastapi import FastAPI
from pydantic import BaseModel
from vllm import LLM, SamplingParams

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

BASE_MODEL = os.getenv("BASE_MODEL", "zjwan461/jarvis-coder")
# 输出仅需 DANGEROUS/SAFE(已由 max_tokens=10 限制), 上下文主要消耗在输入的待检测代码上,
# 1024 足以覆盖常规代码片段; 若需检测超长代码可通过环境变量上调
MAX_MODEL_LEN = int(os.getenv("MAX_MODEL_LEN", "1024"))
GPU_MEMORY_UTILIZATION = float(os.getenv("GPU_MEMORY_UTILIZATION", "0.85"))

PROMPT_TPL = """判断下面代码片段是否包含危险违规系统操作，危险操作包括删除系统核心文件、修改系统关键配置、执行恶意系统命令、创建恶意后门等。只输出DANGEROUS或者SAFE，不要输出多余内容。
###代码片段：
{code}
###输出：
"""

_llm = None
_generate_lock = threading.Lock()

_sampling_params = SamplingParams(
    temperature=0.0,
    top_p=1.0,
    max_tokens=10,
)


def init_model():
    global _llm
    if _llm is not None:
        return
    logger.info("正在加载模型(vLLM)...")
    start = time.time()
    _llm = LLM(
        model=BASE_MODEL,
        dtype="bfloat16",
        trust_remote_code=True,
        max_model_len=MAX_MODEL_LEN,
        gpu_memory_utilization=GPU_MEMORY_UTILIZATION,
    )
    logger.info(f"模型加载完成, 耗时: {time.time() - start:.1f}s")


def release_model():
    global _llm
    if _llm is None:
        return
    logger.info("正在释放模型资源...")
    del _llm
    _llm = None
    logger.info("模型资源已释放")


def detect(code_snippet: str) -> str:
    global _llm
    if _llm is None:
        raise RuntimeError("模型尚未初始化, 请先调用 init_model()")
    start_time = time.time()
    prompt = PROMPT_TPL.format(code=code_snippet)
    with _generate_lock:
        outputs = _llm.generate([prompt], _sampling_params, use_tqdm=False)
    pred = outputs[0].outputs[0].text.strip()
    elapsed_time = time.time() - start_time
    logger.info(f"detect 耗时: {elapsed_time:.3f}s")
    return pred


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("服务启动, 开始加载模型...")
    init_model()
    yield
    logger.info("服务关闭, 开始释放资源...")
    release_model()


app = FastAPI(title="Code Danger Detect Service (vLLM)", lifespan=lifespan)


class DetectReq(BaseModel):
    code: str


@app.post("/detect")
def detect_endpoint(req: DetectReq):
    pred = detect(req.code)
    label = "DANGEROUS" if "DANGEROUS" in pred else "SAFE"
    return {"label": label, "raw_output": pred}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
