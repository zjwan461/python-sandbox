import torch
from datasets import load_dataset
from transformers import (
    AutoModelForCausalLM,
    AutoTokenizer,
    BitsAndBytesConfig,
)
from peft import LoraConfig
from trl import SFTTrainer, SFTConfig

# ====================== 配置区 ======================
# BASE_MODEL = "deepseek-ai/deepseek-coder-1.3b-instruct"
BASE_MODEL = "Qwen/Qwen2.5-Coder-1.5B-Instruct"

TRAIN_DATA_PATH = "./datasets/train.jsonl"
VAL_DATA_PATH = "./datasets/val.jsonl"
OUTPUT_DIR = "./output/code-danger-lora"

MAX_SEQ_LENGTH = 1536
BATCH_SIZE = 4
GRAD_ACCUM = 4
EPOCHS = 3
LR = 2e-4

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

# Prompt模板，严格固定
def format_prompt(sample):
    prompt = f"""{sample['instruction']}
###代码片段：
{sample['input']}
###输出：
{sample['output']}"""
    return {"text": prompt}

# ====================== 加载数据与模型 ======================
dataset = load_dataset("json", data_files={"train": TRAIN_DATA_PATH, "val": VAL_DATA_PATH})
train_ds = dataset["train"].map(format_prompt)
val_ds = dataset["val"].map(format_prompt)

tokenizer = AutoTokenizer.from_pretrained(BASE_MODEL, trust_remote_code=True)
tokenizer.pad_token = tokenizer.eos_token
tokenizer.padding_side = "right"

model = AutoModelForCausalLM.from_pretrained(
    BASE_MODEL,
    quantization_config=bnb_config,
    device_map="auto",
    trust_remote_code=True,
    torch_dtype=torch.bfloat16,
)
model.config.use_cache = False

# TRL v1.x 使用 SFTConfig，不再使用TrainingArguments
sft_config = SFTConfig(
    output_dir=OUTPUT_DIR,
    per_device_train_batch_size=BATCH_SIZE,
    gradient_accumulation_steps=GRAD_ACCUM,
    learning_rate=LR,
    num_train_epochs=EPOCHS,
    logging_steps=10,
    evaluation_strategy="epoch",
    save_strategy="epoch",
    fp16=False,
    bf16=True,
    optim="paged_adamw_8bit",
    report_to="none",
    max_seq_length=MAX_SEQ_LENGTH,
    dataset_text_field="text",
)

trainer = SFTTrainer(
    model=model,
    train_dataset=train_ds,
    eval_dataset=val_ds,
    peft_config=lora_config,
    tokenizer=tokenizer,
    args=sft_config,
)

if __name__ == "__main__":
    trainer.train()
    trainer.save_model(OUTPUT_DIR)
    print(f"LoRA适配器已保存至 {OUTPUT_DIR}")
