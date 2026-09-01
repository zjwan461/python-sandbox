import torch
import os
from datasets import load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
    TrainingArguments,  # 新增导入
)
from peft import LoraConfig, PeftModel
from trl import SFTTrainer, SFTConfig
from dotenv import load_dotenv

load_dotenv()

# ====================== 配置区 ======================
# BASE_MODEL = "deepseek-ai/deepseek-coder-1.3b-instruct"
BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"
TRAIN_DATA_PATH = "./datasets/train.jsonl"
VAL_DATA_PATH = "./datasets/val.jsonl"
OUTPUT_DIR = "./output/code-danger-lora"
MERGED_MODEL_DIR = "./output/code-danger-merged-full"  # 合并之后完整模型输出目录
"""
GPU 型号           显存   MAX_SEQ_LENGTH  BATCH_SIZE  GRAD_ACCUM  总有效 Batch    状态备注
RTX 5060Ti 16G     16GB      1536         2           8         16           ✅舒适; 1.5B宽松; 跑7B建议BATCH_SIZE=1 GRAD_ACCUM=16,开启梯度检查点
RTX 5070Ti 24G     24GB      1536-2048    4           4         16           ✅舒适; 1.5B可拉到seq=2048; 7B模型BATCH_SIZE=2 GRAD_ACCUM=8
RTX 5080 16G       16GB      1536-2048    2-4         4-8       16-24        ✅舒适; seq拉到2048可跑; 7B建议BATCH_SIZE=1+梯度检查点
RTX 5080 Super 24G 24GB      2048         4-6         4         16-24        ✅舒适; 1.5B/7B QLoRA都友好
RTX 5090D 32G      32GB      2048-4096    8           4         32           ✅非常宽松; 1.5B大胆开大seq; 可直接跑7B-14B QLoRA
RTX4090 /4090D 24G 24GB      2048         6           4         24           ✅舒适; 当前你正在使用; 7B模型BATCH_SIZE=2 GRAD_ACCUM=8
RTX4080 Super 16G  16GB      1536-2048    2-4         4-8       16-24        ✅舒适; 7B模型务必开启gradient_checkpointing
RTX4080 16G        16GB      1536-2048    2-4         4-8       16-24        ✅舒适; 同4080 Super
RTX4070Ti-Super 16G16GB      1536         2-4         4-8       16           ✅同5060Ti-16G; 跑7B偏紧,必须开梯度检查点
RTX4070Ti 12G      12GB      1024-1280    2           8         16           ⚠️偏紧; 建议开启gradient_checkpointing; 不适合7B
RTX4070 Super 12G  12GB      1024-1280    2           8         16           ⚠️偏紧; 开启梯度检查点; 只推荐1.5B及以下
RTX4070 12G        12GB      1024-1280    2           8         16           ⚠️偏紧; 开启梯度检查点; 不建议7B
RTX4060Ti 16G      16GB      1536         2-4         4-8       16           ✅1.5B舒适; 7B需要BATCH_SIZE=1+梯度检查点
RTX4060 8G         8G        1024         2           4         8            ⚠️偏紧; 强制开启gradient_checkpointing; 仅限1.5B以内
RTX3090 /3090Ti 24G24GB      2048         4-6         4         16-24        ✅舒适; 注意: 安培卡bf16硬件性能弱,训练速度会慢很多; 优先fp16
RTX3080Ti 12G      12GB      1024-1280    2           8         16           ⚠️偏紧; 开启梯度检查点; 安培卡bf16性能差
A10 24G            24GB      2048-4096    4-6         4         16-24        ✅专业卡; bf16原生性能优秀,7B QLoRA友好
L20 48G            48GB      4096         12-16       2         24-32        ✅工作站; 可以直接7B/14B QLoRA微调
A100 40G/80G       40/80GB   4096         16-32       2         32-64        ✅旗舰工作站; 大seq、14B-34B QLoRA
"""

MAX_SEQ_LENGTH = 1536
BATCH_SIZE = 4
GRAD_ACCUM = 4
EPOCHS = 3
LR = 2e-4
DO_MERGE_AFTER_TRAIN = True  # 训练结束后是否自动合并LoRA到基座

# 4bit NF4 量化配置
bnb_config = BitsAndBytesConfig(
    load_in_4bit=True,
    bnb_4bit_quant_type="nf4",
    bnb_4bit_compute_dtype=torch.bfloat16,
    bnb_4bit_use_double_quant=True,
)

# LoRA配置
lora_config = LoraConfig(
    r=8,
    lora_alpha=16,
    target_modules=["q_proj", "v_proj"],
    lora_dropout=0.05,
    bias="none",
    task_type="CAUSAL_LM",
)

print("开始加载tokenizer")
tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)


# Prompt模板,严格固定
def format_prompt(sample):
    prompt = f"""{sample['instruction']}
###代码片段：
{sample['input']}
###输出：
{sample['output']}"""
    return {"text": prompt}


def tokenizer_format_prompt(sample):
    prompt_text = format_prompt(sample)["text"]
    out = tokenizer(
        prompt_text,
        truncation=True,  # 超过就截断
        padding="max_length",  # 不足强制pad到MAX_SEQ_LENGTH
        max_length=MAX_SEQ_LENGTH,
    )
    out["labels"] = out["input_ids"].copy()
    return out


print("开始加载数据集")
# ====================== 加载数据与模型 ======================
dataset = load_dataset(
    "json", data_files={"train": TRAIN_DATA_PATH, "val": VAL_DATA_PATH}
)

train_ds = dataset["train"].map(
    tokenizer_format_prompt, batched=False
)  # 如果开启batched, 必须用tokenizer_format_prompt
val_ds = dataset["val"].map(tokenizer_format_prompt, batched=False)

model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    quantization_config=bnb_config,
    device_map="auto",
    trust_remote_code=True,
    dtype=torch.bfloat16,
)
model.config.use_cache = False

# -------------------------- 修复部分 --------------------------
# 通用训练参数放到 TrainingArguments
training_args = TrainingArguments(
    output_dir=OUTPUT_DIR,
    overwrite_output_dir=True,
    per_device_train_batch_size=BATCH_SIZE,
    gradient_accumulation_steps=GRAD_ACCUM,
    learning_rate=LR,
    num_train_epochs=EPOCHS,
    logging_steps=10,
    eval_strategy="epoch",
    save_strategy="epoch",
    bf16=True,
    optim="paged_adamw_8bit",
    report_to="none",
    # OOM时打开
    # gradient_checkpointing=True,
)


trainer = SFTTrainer(
    model=model,
    train_dataset=train_ds,
    eval_dataset=val_ds,
    peft_config=lora_config,
    args=training_args,  # TrainingArguments实例
)
# --------------------------------------------------------------


def merge_lora_to_base(
    base_model_name: str, lora_adapter_dir: str, output_merged_dir: str
):
    """
    将LoRA Adapter合并到原始基座模型,导出完整可独立运行模型
    :param base_model_name: 基座模型名称/本地路径
    :param lora_adapter_dir: trainer.save_model输出的lora适配器目录
    :param output_merged_dir: 合并完成完整模型输出路径
    """
    print("\n========== 开始合并 LoRA Adapter 到基座模型 ==========")
    # 合并阶段不能使用4bit量化; 加载原始基座为bfloat16
    base_model = AutoModelForCausalLM.from_pretrained(
        base_model_name,
        dtype=torch.bfloat16,
        device_map="auto",
        trust_remote_code=True,
    )
    # 加载LoRA adapter
    lora_model = PeftModel.from_pretrained(base_model, lora_adapter_dir)
    # 执行合并并且卸载peft包装,得到原生transformers模型
    merged_model = lora_model.merge_and_unload()
    # 保存完整模型 + tokenizer
    merged_model.save_pretrained(output_merged_dir, safe_serialization=True)
    tokenizer.save_pretrained(output_merged_dir)
    print(f"✅ 合并完成,完整模型已保存至：{output_merged_dir}")
    return merged_model


if __name__ == "__main__":
    trainer.train()
    trainer.save_model(OUTPUT_DIR)
    print(f"LoRA适配器已保存至 {OUTPUT_DIR}")

    # 可选：训练结束执行合并
    if DO_MERGE_AFTER_TRAIN:
        merge_lora_to_base(
            base_model_name=BASE_MODEL,
            lora_adapter_dir=OUTPUT_DIR,
            output_merged_dir=MERGED_MODEL_DIR,
        )
