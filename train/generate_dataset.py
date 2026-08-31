import os
import json
import time
import random
from pathlib import Path
from dotenv import load_dotenv
import openai

# ========== 配置区 ==========
load_dotenv()
# 百炼API Key，从.env读取
MODEL_API_KEY = os.getenv("MODEL_API_KEY")
# 百炼兼容OpenAI接口地址
BASE_URL = os.getenv("BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
# 模型，可改为 qwen‑plus、qwen‑turbo 等
MODEL_NAME = os.getenv("MODEL_NAME", "qwen3.7-plus")

# 输出目录
OUTPUT_DIR = Path("./datasets")
OUTPUT_RAW = OUTPUT_DIR / "raw_generated.jsonl"
TRAIN_PATH = OUTPUT_DIR / "train.jsonl"
VAL_PATH = OUTPUT_DIR / "val.jsonl"
TEST_PATH = OUTPUT_DIR / "test.jsonl"

# 生成样本数量
COUNT_DANGEROUS = 1200  # 危险真实操作样本 DANGEROUS
COUNT_SAFE_NORMAL = 1200  # 普通安全业务样本 SAFE
COUNT_HARD_NEG = 1000  # Hard‑Negative迷惑样本（注释/字符串，SAFE）

# 请求并发控制
SLEEP_SEC = 1.2  # 每次请求间隔，防止QPS超限
MAX_RETRY = 3  # 失败重试次数

# SFT固定Instruction模板，和训练脚本保持完全一致
INSTRUCTION_TPL = """判断下面代码片段是否包含危险违规系统操作：删除系统核心文件、修改系统关键配置、执行恶意系统命令、反弹shell、篡改权限。
只输出 DANGEROUS 或者 SAFE，不要多余解释。"""

# 生成Prompt模板
PROMPT_DANGEROUS = """
请生成简短Python或者Shell代码片段（5‑30行），代码**真实包含危险违规系统操作**：删除系统核心文件、修改系统配置、执行系统命令、反弹shell、篡改文件权限。
技巧：可以使用变量传递危险参数、字符串拼接参数，不要全部写死字面量。
要求：只输出代码，不要解释，不要输出完整可直接利用的恶意程序。
"""

PROMPT_SAFE_NORMAL = """
生成一段普通业务Python/Shell代码片段（5‑30行），仅做正常业务逻辑，**不存在任何高危系统操作**，不调用删除文件、命令执行等危险API。
只输出代码片段，不要多余解释。
"""

PROMPT_HARD_NEGATIVE = """
生成一段代码片段（5‑30行），代码里面出现看起来危险的命令字符串，**但是代码绝对不会实际执行危险操作**。
可选形式：
1.危险命令写在注释里面；
2.危险命令保存字符串变量，只打印，不传给os.system/subprocess等执行函数；
3.危险系统路径赋值变量，但是没有传给删除/修改文件API。
只输出代码片段，不要多余解释。
"""
# ============================

# 初始化目录
OUTPUT_DIR.mkdir(exist_ok=True)

client = openai.OpenAI(api_key=MODEL_API_KEY, base_url=BASE_URL)


def llm_call(prompt: str) -> str | None:
    """调用百炼API，带重试"""
    for attempt in range(MAX_RETRY):
        try:
            resp = client.chat.completions.create(
                model=MODEL_NAME,
                messages=[{"role": "user", "content": prompt}],
                temperature=0.7,
                max_tokens=1024,
                extra_body={"enable_thinking": False},
            )
            content = resp.choices[0].message.content.strip()
            return content
        except Exception as e:
            print(f"[WARN] request failed attempt {attempt+1}/{MAX_RETRY}: {str(e)}")
            time.sleep(SLEEP_SEC * (attempt + 1))
    return None


def generate_one_sample(gen_prompt: str, true_label: str) -> dict | None:
    """生成单条SFT样本"""
    code_snippet = llm_call(gen_prompt)
    if not code_snippet:
        return None
    item = {"instruction": INSTRUCTION_TPL, "input": code_snippet, "output": true_label}
    return item


def batch_generate(total_cnt: int, prompt: str, label: str, out_file):
    """批量生成，写入raw jsonl"""
    success = 0
    while success < total_cnt:
        sample = generate_one_sample(prompt, label)
        if sample is not None:
            line = json.dumps(sample, ensure_ascii=False)
            out_file.write(line + "\n")
            success += 1
            print(f"[{label}] progress: {success}/{total_cnt}")
        time.sleep(SLEEP_SEC)


def split_dataset(raw_path: Path, train_rate=0.8, val_rate=0.1, test_rate=0.1):
    """将raw_generated.jsonl切分 train / val / test"""
    assert abs(train_rate + val_rate + test_rate - 1.0) < 1e-6
    all_lines = []
    with open(raw_path, "r", encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line:
                all_lines.append(line)
    random.shuffle(all_lines)
    total = len(all_lines)
    n_train = int(total * train_rate)
    n_val = int(total * val_rate)

    train_lines = all_lines[:n_train]
    val_lines = all_lines[n_train : n_train + n_val]
    test_lines = all_lines[n_train + n_val :]

    def write_lines(filepath: Path, lines_list):
        with open(filepath, "w", encoding="utf-8") as fw:
            for l in lines_list:
                fw.write(l + "\n")

    write_lines(TRAIN_PATH, train_lines)
    write_lines(VAL_PATH, val_lines)
    write_lines(TEST_PATH, test_lines)
    print(f"\nDataset split done:")
    print(f"  train: {len(train_lines)} → {TRAIN_PATH}")
    print(f"  val:   {len(val_lines)} → {VAL_PATH}")
    print(f"  test:  {len(test_lines)} → {TEST_PATH}")


if __name__ == "__main__":
    if not MODEL_API_KEY:
        raise RuntimeError("请配置 MODEL_API_KEY 环境变量，在 .env 文件")

    with open(OUTPUT_RAW, "w", encoding="utf-8") as f_out:
        print(f"\n==== 开始生成 DANGEROUS 样本 count={COUNT_DANGEROUS} ====")
        batch_generate(COUNT_DANGEROUS, PROMPT_DANGEROUS, "DANGEROUS", f_out)

        print(f"\n==== 开始生成 SAFE‑NORMAL 样本 count={COUNT_SAFE_NORMAL} ====")
        batch_generate(COUNT_SAFE_NORMAL, PROMPT_SAFE_NORMAL, "SAFE", f_out)

        print(f"\n==== 开始生成 HARD‑NEGATIVE 迷惑样本 count={COUNT_HARD_NEG} ====")
        batch_generate(COUNT_HARD_NEG, PROMPT_HARD_NEGATIVE, "SAFE", f_out)

    print(f"\nRaw dataset finished: {OUTPUT_RAW}")
    # 切分数据集
    split_dataset(OUTPUT_RAW)
    print("All done!")
