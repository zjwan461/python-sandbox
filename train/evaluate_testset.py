# evaluate_testset.py
"""
独立测试集评估脚本
使用时机：训练完成，LoRA合并导出完整模型 MERGED_MODEL_DIR 之后运行
不参与训练流程，仅用于最终离线评估；test集全程不参与训练、调参
"""

import json
import time
from typing import Dict, List
from datasets import load_dataset
from transformers import AutoModelForCausalLM, AutoTokenizer
import torch
from tqdm import tqdm

# ====================== 配置区 ======================
MERGED_MODEL_DIR = "./output/jarvis-coder"
TEST_DATA_PATH = "./datasets/test.jsonl"
OUTPUT_ERROR_FILE = "./output/test_error_samples.jsonl"
MAX_SEQ_LENGTH = 1536
MAX_NEW_TOKENS = 64
TEMPERATURE = 0.0  # 评估必须0，关闭随机性
SHOW_ERROR_COUNT = 5  # 控制台最多打印前N条错误样本
DEVICE = "cuda"
# ====================================================


def main():
    print("=" * 70)
    print(f"🔍 开始执行测试集离线评估脚本")
    print(f"📌 待评估模型路径: {MERGED_MODEL_DIR}")
    print(f"📌 测试集文件: {TEST_DATA_PATH}")
    print(f"📌 错误样本输出路径: {OUTPUT_ERROR_FILE}")
    print("=" * 70)

    # 1.加载tokenizer
    print("\n[1/4] 加载 tokenizer ...")
    tokenizer = AutoTokenizer.from_pretrained(MERGED_MODEL_DIR, trust_remote_code=True)
    tokenizer.pad_token = tokenizer.eos_token
    tokenizer.padding_side = "left"
    print("✅ tokenizer 加载完成")

    # 2.加载完整合并后的模型
    print("\n[2/4] 加载合并后完整模型到GPU ...")
    model = AutoModelForCausalLM.from_pretrained(
        MERGED_MODEL_DIR,
        dtype=torch.bfloat16,
        device_map="auto",
        trust_remote_code=True,
    )
    model.eval()
    print("✅ 模型加载完成，进入eval推理模式")

    # 3.加载测试数据集
    print("\n[3/4] 读取测试数据集 ...")
    dataset = load_dataset("json", data_files=TEST_DATA_PATH, split="train")
    total_samples = len(dataset)
    print(f"✅ 测试集总样本量：{total_samples}")

    def build_prompt(sample: Dict) -> str:
        prompt = f"""{sample['instruction']}
###代码片段：
{sample['input']}
###输出：
"""
        return prompt

    y_true: List[str] = []
    y_pred: List[str] = []
    error_samples: List[Dict] = []
    total_infer_time = 0.0
    danger_total = 0
    danger_correct = 0
    safe_total = 0
    safe_correct = 0

    print("\n[4/4] 开始逐条推理评估 ...")
    pbar = tqdm(dataset, desc="评估进度", total=total_samples)

    for idx, item in enumerate(pbar):
        prompt = build_prompt(item)
        true_answer = item["output"].strip()

        # 区分样本类型（你的输出一般是 "DANGEROUS"）
        is_truth_danger = true_answer == "DANGEROUS"

        inputs = tokenizer(
            prompt, return_tensors="pt", truncation=True, max_length=MAX_SEQ_LENGTH
        ).to(DEVICE)

        t0 = time.perf_counter()
        try:
            with torch.no_grad():
                outputs = model.generate(
                    **inputs,
                    max_new_tokens=MAX_NEW_TOKENS,
                    temperature=TEMPERATURE,
                    top_p=1.0,
                    do_sample=False,
                )
            infer_cost = time.perf_counter() - t0
            total_infer_time += infer_cost

            gen_text = tokenizer.decode(
                outputs[0][len(inputs["input_ids"][0]) :], skip_special_tokens=True
            ).strip()

        except Exception as e:
            print(f"\n⚠️ 第{idx}条样本推理异常，跳过: {str(e)}")
            gen_text = "[INFER_ERROR]"
            infer_cost = 0
        pbar.set_postfix({"单条耗时": f"{infer_cost:.3f}s"})

        y_true.append(true_answer)
        y_pred.append(gen_text)

        # 分别统计危险、安全样本
        if is_truth_danger:
            danger_total += 1
            if gen_text == true_answer:
                danger_correct += 1
        else:
            safe_total += 1
            if gen_text == true_answer:
                safe_correct += 1

        if gen_text != true_answer:
            error_samples.append(
                {
                    "index": idx,
                    "instruction": item["instruction"],
                    "code_snippet": item["input"],
                    "ground_truth": true_answer,
                    "predict": gen_text,
                }
            )

    # ===================== 输出汇总报告 =====================
    print("\n" + "=" * 70)
    print("📊 【测试集最终评估报告】")
    print("=" * 70)
    total = len(y_true)
    correct = sum([1 for t, p in zip(y_true, y_pred) if t == p])
    acc = correct / total if total > 0 else 0.0
    avg_infer_ms = (total_infer_time / total) * 1000 if total > 0 else 0

    print(f"总样本数:              {total}")
    print(f"预测正确样本:          {correct}")
    print(f"整体准确率 Accuracy:   {acc:.4f} ({correct}/{total})")
    print(f"平均单条推理耗时:      {avg_infer_ms:.2f} ms")
    print(f"\n---分类型统计---")
    print(
        f"危险操作样本总数: {danger_total}，识别正确: {danger_correct}，召回率:{danger_correct/danger_total:.4f}"
        if danger_total > 0
        else "危险样本数为0"
    )
    print(
        f"安全代码样本总数: {safe_total}，识别正确: {safe_correct}，准确率:{safe_correct/safe_total:.4f}"
        if safe_total > 0
        else "安全样本数为0"
    )
    print(f"\n错误样本数量：{len(error_samples)}")

    # 控制台打印前 N 个错误样例
    print(f"\n控制台展示前 {min(SHOW_ERROR_COUNT, len(error_samples))} 条错误案例：")
    for err in error_samples[:SHOW_ERROR_COUNT]:
        print("-" * 50)
        print(f"样本序号: {err['index']}")
        print(f"真实标签: {err['ground_truth']}")
        print(f"模型预测: {err['predict']}")
        print(f"代码片段:\n{err['code_snippet'][:300]}...")

    # 持久化全部错误样本
    with open(OUTPUT_ERROR_FILE, "w", encoding="utf-8") as fw:
        for s in error_samples:
            fw.write(json.dumps(s, ensure_ascii=False) + "\n")
    print(f"\n✅ 全部错误样本已写入文件: {OUTPUT_ERROR_FILE}")
    print("\n💡提示：分析错误样本，把代表性case补充进训练/val数据集迭代优化模型。")
    print("=" * 70)


if __name__ == "__main__":
    main()
