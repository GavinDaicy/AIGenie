"""PPStructureV3 独立引擎服务脚本。

由 PPStructureParser 通过 subprocess.Popen 启动。
使用 stdin/stdout JSON 行协议与父进程通信。
stderr 继承父进程，引擎加载日志正常出现在 Celery worker 日志中。
"""
import json
import os
import sys

os.environ["PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK"] = "True"
os.environ["OMP_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["FLAGS_num_threads"] = "1"


def _serialize(raw: list) -> list:
    result = []
    for item in raw:
        if isinstance(item, dict):
            parsing_res_raw = item.get("parsing_res_list", []) or []
            width = item.get("width")
            height = item.get("height")
        else:
            parsing_res_raw = getattr(item, "parsing_res_list", []) or []
            width = getattr(item, "width", None)
            height = getattr(item, "height", None)

        blocks = []
        for blk in parsing_res_raw:
            if isinstance(blk, dict):
                blocks.append(blk)
            elif hasattr(blk, "label"):
                blocks.append({
                    "type": str(blk.label),
                    "bbox": list(blk.bbox) if blk.bbox else [0.0, 0.0, 0.0, 0.0],
                    "text": str(blk.content or ""),
                })

        result.append({
            "parsing_res_list": blocks,
            "width": float(width) if width is not None else None,
            "height": float(height) if height is not None else None,
        })
    return result


def main():
    from paddleocr import PPStructureV3
    engine = PPStructureV3(lang="ch")

    sys.stdout.write("READY\n")
    sys.stdout.flush()

    for line in sys.stdin:
        img_path = line.rstrip("\n")
        if not img_path:
            continue
        try:
            raw = list(engine.predict(img_path))
            result = _serialize(raw)
            sys.stdout.write(json.dumps({"ok": True, "result": result}) + "\n")
        except Exception as exc:
            sys.stdout.write(json.dumps({"ok": False, "error": str(exc)}) + "\n")
        sys.stdout.flush()


if __name__ == "__main__":
    main()
