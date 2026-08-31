import json
from pathlib import Path


# 复用和生成脚本完全一致的校验逻辑
def validate_sample_item(item: dict) -> tuple[bool, str]:
    required_keys = ("instruction", "input", "output")
    for k in required_keys:
        if k not in item:
            return False, f"缺失字段 {k}"
        v = item[k]
        if not isinstance(v, str) or len(v.strip()) == 0:
            return False, f"字段 {k} 为空或者非字符串"

    allowed_labels = {"DANGEROUS", "SAFE"}
    output_label = item["output"].strip()
    if output_label not in allowed_labels:
        return False, f"非法output标签: {output_label}, 允许值 {allowed_labels}"

    code_input = item["input"].strip()
    if len(code_input) < 10:
        return False, f"input代码片段过短，length={len(code_input)}"

    return True, "ok"


def check_jsonl(file_path: Path, bad_out: Path):
    print(f"\n==== Checking {file_path} ====")
    total = 0
    valid = 0
    bad_items = []

    if not file_path.exists():
        print(f"file not exist: {file_path}")
        return total, valid

    with open(file_path, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            line = line.strip()
            if not line:
                continue
            total += 1
            try:
                item = json.loads(line)
            except Exception as e:
                bad_items.append(
                    {"line_no": idx + 1, "error": "json parse error", "raw": line}
                )
                continue

            ok, msg = validate_sample_item(item)
            if ok:
                valid += 1
            else:
                bad_items.append({"line_no": idx + 1, "error": msg, "item": item})

    # 写出坏样本
    with open(bad_out, "a", encoding="utf-8") as fw:
        for b in bad_items:
            fw.write(json.dumps(b, ensure_ascii=False) + "\n")

    invalid = total - valid
    print(f"total={total}, valid={valid}, invalid={invalid}")
    return total, valid


def main():
    datasets_dir = Path("./datasets")
    bad_file = datasets_dir / "bad_samples.jsonl"
    # 清空旧坏样本
    if bad_file.exists():
        bad_file.unlink()

    files = [
        datasets_dir / "raw_generated.jsonl",
        datasets_dir / "train.jsonl",
        datasets_dir / "val.jsonl",
        datasets_dir / "test.jsonl",
    ]
    for fp in files:
        check_jsonl(fp, bad_file)

    print(f"\nBad samples dumped to: {bad_file}")


if __name__ == "__main__":
    main()
