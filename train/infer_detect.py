import torch
from peft import PeftModel
from transformers import AutoModelForCausalLM, AutoTokenizer

BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
LORA_ADAPTER = "./code-danger-lora"

tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)
tokenizer.pad_token = tokenizer.eos_token

base_model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    torch_dtype=torch.bfloat16,
    device_map="auto",
    trust_remote_code=True
)
model = PeftModel.from_pretrained(base_model, LORA_ADAPTER)

PROMPT_TPL = """判断下面代码片段是否包含危险违规系统操作，危险操作包括删除系统核心文件、修改系统关键配置、执行恶意系统命令、创建恶意后门等。只输出DANGEROUS或者SAFE，不要输出多余内容。
###代码片段：
{code}
###输出：
"""

def detect(code_snippet: str) -> str:
    prompt = PROMPT_TPL.format(code=code_snippet)
    inputs = tokenizer(prompt, return_tensors="pt").to("cuda")
    outputs = model.generate(
        **inputs,
        temperature=0.0,
        top_p=1.0,
        max_new_tokens=10,
        pad_token_id=tokenizer.eos_token_id
    )
    out_text = tokenizer.decode(outputs[0], skip_special_tokens=True)
    pred = out_text.split("###输出：")[-1].strip()
    return "DANGEROUS" if "DANGEROUS" in pred else "SAFE"

if __name__ == "__main__":
    test_code = """import os
os.remove("/etc/shadow")
"""
    res = detect(test_code)
    print(f"result: {res}")
