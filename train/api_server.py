from fastapi import FastAPI
from pydantic import BaseModel
import os
import torch
from vllm import LLM, SamplingParams

# 如果没有 GPU，强制使用 CPU 模式
if not torch.cuda.is_available():
    os.environ["VLLM_USE_CUDA"] = "0"
    print("警告: 未检测到 GPU，将使用 CPU 模式（推理速度会非常慢）")
else:
    print(f"检测到 GPU: {torch.cuda.get_device_name(0)}")

app = FastAPI(title="Code Danger Detect Service")
JARVIS_CODER = "zjwan461/jarvis-coder"

llm = LLM(
    model=JARVIS_CODER,
    dtype="bfloat16"
)

sampling_params = SamplingParams(temperature=0.0, top_p=1.0, max_tokens=10, stop=["\n"])

PROMPT_TPL = """判断下面代码片段是否包含危险违规系统操作，危险操作包括删除系统核心文件、修改系统关键配置、执行恶意系统命令、创建恶意后门等。只输出DANGEROUS或者SAFE，不要输出多余内容。
###代码片段：
{code}
###输出：
"""


class DetectReq(BaseModel):
    code: str


@app.post("/detect")
def detect(req: DetectReq):
    prompt = PROMPT_TPL.format(code=req.code)
    # outputs = llm.generate(prompt, sampling_params, lora_request=lora_id)
    outputs = llm.generate(prompt, sampling_params)
    pred = outputs[0].outputs[0].text.strip()
    label = "DANGEROUS" if "DANGEROUS" in pred else "SAFE"
    return {"label": label, "raw_output": pred}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=8000)
