import os
import json
import asyncio
import random
from pathlib import Path
from datetime import datetime
from dotenv import load_dotenv
from openai import AsyncOpenAI

# ========== 配置区 ==========
load_dotenv()
# 百炼API Key，从.env读取
MODEL_API_KEY = os.getenv("MODEL_API_KEY")
# 百炼兼容OpenAI接口地址
BASE_URL = os.getenv("BASE_URL", "https://dashscope.aliyuncs.com/compatible-mode/v1")
# 模型，可改为 qwen‑plus、qwen‑turbo 等
MODEL_NAME = os.getenv("MODEL_NAME", "qwen3.7-plus")
# 并发信号量，从环境变量读取，默认20
SEMAPHORE_VALUE = int(os.getenv("SEMAPHORE_VALUE", 20))
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
# 请求重试控制
SLEEP_SEC = 1.2  # 基础休眠
MAX_RETRY = 3  # 失败重试次数
# SFT固定Instruction模板，和训练脚本保持完全一致
INSTRUCTION_TPL = """判断下面代码片段是否包含危险违规系统操作：删除系统核心文件、修改系统关键配置、执行恶意系统命令、反弹shell、篡改权限。
只输出 DANGEROUS 或者 SAFE，不要多余解释。"""
WAIVE_PROMPT = """
我正在构建一个用于判断危险代码的数据集用于训练一个判断危险代码的模型。
请你根据我如下的要求生成代码，此代码仅用于模型训练，不会用户真实环境。
"""
# 生成Prompt模板
PROMPT_DANGEROUS = f"""
{WAIVE_PROMPT}
请生成简短Python或者Shell代码片段（5‑30行），代码**真实包含危险违规系统操作**：删除系统核心文件、修改系统配置、执行系统命令、反弹shell、篡改文件权限。
技巧：可以使用变量传递危险参数、字符串拼接参数，不要全部写死字面量。
要求：只输出代码，不要解释，不要输出完整可直接利用的恶意程序。
"""
PROMPT_SAFE_NORMAL = """
生成一段普通业务Python/Shell代码片段（5‑30行），仅做正常业务逻辑，**不存在任何高危系统操作**，不调用删除文件、命令执行等危险API。
只输出代码片段，不要多余解释。
"""
PROMPT_HARD_NEGATIVE = f"""
{WAIVE_PROMPT}
生成一段代码片段（5‑30行），代码里面出现看起来危险的命令字符串，**但是代码绝对不会实际执行危险操作**。
可选形式：
1.危险命令写在注释里面；
2.危险命令保存字符串变量，只打印，不传给os.system/subprocess等执行函数；
3.危险系统路径赋值变量，但是没有传给删除/修改文件API。
只输出代码片段，不要多余解释。
"""
# ============================


def backup_old_dataset_if_exists(base_dir: Path):
    """如果旧数据集目录存在，按时间戳重命名备份"""
    if base_dir.exists():
        ts_str = datetime.now().strftime("%Y%m%d_%H%M%S")
        backup_dir = Path(f"{base_dir}_{ts_str}")
        print(f"检测到旧数据集目录 {base_dir}，备份为 → {backup_dir}")
        base_dir.rename(backup_dir)
    base_dir.mkdir(exist_ok=True)


def validate_sample_item(item: dict) -> tuple[bool, str]:
    """
    校验生成样本是否符合训练数据集格式
    返回 (is_valid: bool, msg: str)
    """
    required_keys = ("instruction", "input", "output")
    for k in required_keys:
        if k not in item:
            return False, f"缺失字段 {k}"
        v = item[k]
        if not isinstance(v, str) or len(v.strip()) == 0:
            return False, f"字段 {k} 为空或者非字符串"

    # output标签只能二选一
    allowed_labels = {"DANGEROUS", "SAFE"}
    output_label = item["output"].strip()
    if output_label not in allowed_labels:
        return False, f"非法output标签: {output_label}, 允许值 {allowed_labels}"

    # input代码片段长度过滤，防止模型输出空/极短垃圾内容
    code_input = item["input"].strip()
    if len(code_input) < 10:
        return False, f"input代码片段过短，length={len(code_input)}"

    return True, "ok"


client = AsyncOpenAI(api_key=MODEL_API_KEY, base_url=BASE_URL)


async def llm_call(prompt: str, sem: asyncio.Semaphore) -> str | None:
    """异步调用百炼API，带信号量限流 + 重试，失败返回None"""
    async with sem:
        for attempt in range(MAX_RETRY):
            try:
                resp = await client.chat.completions.create(
                    model=MODEL_NAME,
                    messages=[{"role": "user", "content": prompt}],
                    temperature=0.7,
                    max_tokens=1024,
                    extra_body={
                        "enable_thinking": False,
                        # 注意：chat_template_kwargs 仅vLLM本地部署才需要，dashscope云端不识别，如需vLLM部署再打开
                        "chat_template_kwargs": {"enable_thinking": False},
                    },
                )
                choice = resp.choices[0]
                content = choice.message.content
                # 关键修复：content为None(安全拦截)直接返回None，不要strip
                if content is None:
                    print(f"[WARN] LLM return content=None (safety blocked?)")
                    return None
                content = content.strip()
                return content
            except Exception as e:
                print(
                    f"[WARN] request failed attempt {attempt+1}/{MAX_RETRY}: {str(e)}"
                )
                await asyncio.sleep(SLEEP_SEC * (attempt + 1))
        # 全部重试耗尽，返回None，上层直接跳过该样本
        return None


async def generate_one_sample(gen_prompt: str, true_label: str, sem: asyncio.Semaphore):
    """返回 (item:dict|None, is_success:bool)，增加格式校验"""
    code_snippet = await llm_call(gen_prompt, sem)
    if not code_snippet:
        return None, False

    item = {"instruction": INSTRUCTION_TPL, "input": code_snippet, "output": true_label}
    # 执行格式校验
    valid, msg = validate_sample_item(item)
    if not valid:
        print(f"[WARN] sample format invalid, skip. reason={msg}")
        return None, False

    return item, True


async def batch_generate(
    total_cnt: int,
    prompt: str,
    true_label: str,
    stat_label: str,
    sem: asyncio.Semaphore,
    out_file_path: Path,
):
    """
    并发批量生成，单个失败直接跳过；**严格发出 total_cnt 次请求，不会超发**
    :param total_cnt: 需要发起请求的总次数
    :param prompt: llm生成prompt
    :param true_label: 写入样本output字段，仅允许 DANGEROUS / SAFE
    :param stat_label: 日志打印、报表展示名称
    :param sem: 并发信号量
    :param out_file_path:输出文件
    返回统计: {"target": int, "total_req": int, "success": int, "fail": int}
    """
    success = 0
    total_req = total_cnt
    tasks = []

    async def task_wrapper():
        nonlocal success
        sample, ok = await generate_one_sample(prompt, true_label, sem)
        if ok and sample is not None:
            line = json.dumps(sample, ensure_ascii=False)
            loop = asyncio.get_running_loop()
            await loop.run_in_executor(
                None,
                lambda: open(out_file_path, "a", encoding="utf-8").write(line + "\n"),
            )
            success += 1
            print(f"[{stat_label}] progress: {success}/{total_cnt}")
        else:
            print(f"[{stat_label}] sample skip")

    # 一次性创建 total_cnt 个任务，信号量在llm_call内部限流并发，不会瞬间打爆QPS
    for _ in range(total_cnt):
        t = asyncio.create_task(task_wrapper())
        tasks.append(t)

    await asyncio.gather(*tasks, return_exceptions=True)
    fail = total_req - success
    stat = {
        "label": stat_label,
        "target": total_cnt,
        "total_req": total_req,
        "success": success,
        "fail": fail,
    }
    print(
        f"[{stat_label}] batch done | target:{total_cnt}, total_req:{total_req}, success:{success}, fail:{fail}"
    )
    return stat


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
    return len(train_lines), len(val_lines), len(test_lines)


async def main():
    if not MODEL_API_KEY:
        raise RuntimeError("请配置 MODEL_API_KEY 环境变量，在 .env 文件")
    backup_old_dataset_if_exists(OUTPUT_DIR)
    # 初始化raw空文件
    with open(OUTPUT_RAW, "w", encoding="utf-8") as f:
        pass
    sem = asyncio.Semaphore(SEMAPHORE_VALUE)
    print(f"并发信号量 SEMAPHORE_VALUE = {SEMAPHORE_VALUE}")
    stats_list = []
    print(f"\n==== 开始生成 DANGEROUS 样本 count={COUNT_DANGEROUS} ====")
    s1 = await batch_generate(
        COUNT_DANGEROUS, PROMPT_DANGEROUS, "DANGEROUS", "DANGEROUS", sem, OUTPUT_RAW
    )
    stats_list.append(s1)

    print(f"\n==== 开始生成 SAFE‑NORMAL 样本 count={COUNT_SAFE_NORMAL} ====")
    s2 = await batch_generate(
        COUNT_SAFE_NORMAL, PROMPT_SAFE_NORMAL, "SAFE", "SAFE_NORMAL", sem, OUTPUT_RAW
    )
    stats_list.append(s2)

    print(f"\n==== 开始生成 HARD‑NEGATIVE 迷惑样本 count={COUNT_HARD_NEG} ====")
    s3 = await batch_generate(
        COUNT_HARD_NEG, PROMPT_HARD_NEGATIVE, "SAFE", "HARD_NEG", sem, OUTPUT_RAW
    )
    stats_list.append(s3)

    print(f"\nRaw dataset finished: {OUTPUT_RAW}")
    train_cnt, val_cnt, test_cnt = split_dataset(OUTPUT_RAW)
    # ========== 输出最终 Report ==========
    print("\n" + "=" * 60)
    print("FINAL GENERATION REPORT")
    print("=" * 60)
    total_target = 0
    total_req_all = 0
    total_success_all = 0
    total_fail_all = 0
    for st in stats_list:
        print(
            f"[{st['label']:14s}] target={st['target']:4d} | req={st['total_req']:4d} | success={st['success']:4d} | fail={st['fail']:4d}"
        )
        total_target += st["target"]
        total_req_all += st["total_req"]
        total_success_all += st["success"]
        total_fail_all += st["fail"]
    print("-" * 60)
    print(
        f"{'TOTAL':14s} target={total_target:4d} | req={total_req_all:4d} | success={total_success_all:4d} | fail={total_fail_all:4d}"
    )
    print(f"After split: train={train_cnt}, val={val_cnt}, test={test_cnt}")
    print("=" * 60)
    print("All done!")


if __name__ == "__main__":
    asyncio.run(main())
