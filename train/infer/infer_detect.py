import gc
import logging
import time

import torch
from transformers import AutoModelForCausalLM, AutoTokenizer
from dotenv import load_dotenv
import os

load_dotenv()

logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

BASE_MODEL = os.getenv("BASE_MODEL", "zjwan461/jarvis-coder")

_model = None
_tokenizer = None

PROMPT_TPL = """判断下面代码片段是否包含危险违规系统操作，危险操作包括删除系统核心文件、修改系统关键配置、执行恶意系统命令、创建恶意后门等。只输出DANGEROUS或者SAFE，不要输出多余内容。
###代码片段：
{code}
###输出：
"""


def get_device():
    return "cuda" if torch.cuda.is_available() else "cpu"


def init_model():
    global _model, _tokenizer
    if _model is not None:
        return
    logger.info("正在加载模型...")
    start = time.time()
    _tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)
    _tokenizer.pad_token = _tokenizer.eos_token
    _model = AutoModelForCausalLM.from_pretrained(
        BASE_MODEL, dtype=torch.bfloat16, device_map="auto", trust_remote_code=True
    )
    logger.info(f"模型加载完成, 耗时: {time.time() - start:.1f}s")


def release_model():
    global _model, _tokenizer
    if _model is None:
        return
    logger.info("正在释放模型资源...")
    del _model
    del _tokenizer
    _model = None
    _tokenizer = None
    gc.collect()
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    logger.info("模型资源已释放")


def detect(code_snippet: str) -> str:
    start_time = time.time()
    prompt = PROMPT_TPL.format(code=code_snippet)
    inputs = _tokenizer(prompt, return_tensors="pt").to(get_device())
    outputs = _model.generate(
        **inputs,
        temperature=0.0,
        top_p=1.0,
        do_sample=False,
        max_new_tokens=10,
        pad_token_id=_tokenizer.eos_token_id,
    )
    out_text = _tokenizer.decode(outputs[0], skip_special_tokens=True)
    pred = out_text.split("###输出：")[-1].strip()
    elapsed_time = time.time() - start_time
    logger.info(f"detect 耗时: {elapsed_time:.3f}s")
    return pred


if __name__ == "__main__":
    init_model()
    test_code = """import os
os.remove("/etc/shadow")
"""
    res = detect(test_code)
    logger.info(f"result: {res}")
    release_model()
