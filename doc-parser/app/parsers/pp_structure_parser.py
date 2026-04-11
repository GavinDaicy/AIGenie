import json
import logging
import os
import shutil
import subprocess
import sys
import tempfile
from io import StringIO
from pathlib import Path
from typing import Dict, List, Optional, Tuple

import pandas as pd

from app.models.position import BlockPosition, BlockType, PositionsJson
from app.parsers.base import BaseParser, ParseOutput
from app.parsers.md_builder import MarkdownBuilder

logger = logging.getLogger(__name__)

_ENGINE_CLIENT = None  # 进程级单例（_EngineClient）

_SERVER_SCRIPT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "_pp_engine_server.py")


class _EngineClient:
    """PPStructureV3 进程外代理：通过 subprocess.Popen 启动独立引擎进程。

    使用 subprocess.Popen（fork+exec）而非 multiprocessing.Process，
    绕过 Python 守护进程不能创建子进程的限制，且 exec 后新进程完全干净无锁。
    通过 stdin/stdout JSON 行协议与引擎进程通信。
    """

    def __init__(self):
        logger.info("PPStructureV3 server starting (subprocess)...")
        self._proc = subprocess.Popen(
            [sys.executable, _SERVER_SCRIPT],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            bufsize=1,
            text=True,
        )
        ready = self._proc.stdout.readline().strip()
        if ready != "READY":
            self._proc.terminate()
            raise RuntimeError(f"PPStructureV3 server failed to start, got: '{ready}'")
        logger.info("PPStructureV3 ready (server PID=%s)", self._proc.pid)

    def predict(self, img_path: str) -> list:
        if self._proc.poll() is not None:
            raise RuntimeError("PPStructureV3 server process died unexpectedly")
        self._proc.stdin.write(img_path + "\n")
        self._proc.stdin.flush()
        line = self._proc.stdout.readline()
        if not line:
            raise RuntimeError("PPStructureV3 server closed stdout unexpectedly")
        resp = json.loads(line)
        if not resp.get("ok"):
            raise RuntimeError(f"PPStructureV3 server error: {resp.get('error')}")
        return resp["result"]

_TYPE_MAP: Dict[str, BlockType] = {
    "title": BlockType.heading,
    "doc_title": BlockType.heading,
    "paragraph_title": BlockType.heading,
    "text": BlockType.paragraph,
    "header": BlockType.paragraph,
    "footer": BlockType.paragraph,
    "abstract": BlockType.paragraph,
    "reference": BlockType.paragraph,
    "content": BlockType.paragraph,
    "table": BlockType.table,
    "figure": BlockType.image,
    "image": BlockType.image,
    "formula": BlockType.formula,
    "list": BlockType.list,
    "code": BlockType.code,
}


class PPStructureParser(BaseParser):
    """
    基于 PaddleOCR PP-StructureV3 的文档解析器（全 CPU，无需 GPU）。

    支持中文 PDF 和图片。PP-Structure bbox 已是左上角坐标系，直接使用，无需转换。
    PDF 文件先通过 pymupdf 逐页转为临时 PNG，再送入引擎解析。
    """

    def _get_engine(self) -> _EngineClient:
        """返回 PPStructureV3 引擎客户端（进程级单例，spawn 子进程模式）。"""
        global _ENGINE_CLIENT
        if _ENGINE_CLIENT is None:
            _ENGINE_CLIENT = _EngineClient()
        return _ENGINE_CLIENT

    def parse(self, file_path: str, job_id: str) -> ParseOutput:
        logger.info("PPStructureParser.parse start: file=%s job_id=%s", file_path, job_id)

        ext = Path(file_path).suffix.lower()
        pages_data = self._load_pages(file_path, ext)

        engine = self._get_engine()

        pages_results: List[Tuple[int, list]] = []
        page_dims: Dict[int, Tuple[Optional[float], Optional[float]]] = {}
        for page_no, img_path, _pw, _ph in pages_data:
            try:
                raw = engine.predict(img_path)
                if raw:
                    res_obj = raw[0]
                    elements = self._normalize_blocks(res_obj.get('parsing_res_list', []))
                    img_w = float(res_obj.get('width') or _pw or 0) or None
                    img_h = float(res_obj.get('height') or _ph or 0) or None
                else:
                    elements = []
                    img_w = float(_pw) if _pw else None
                    img_h = float(_ph) if _ph else None
            except Exception as exc:
                logger.warning("PPStructure engine error on page %d: %s", page_no, exc)
                elements = []
                img_w = float(_pw) if _pw else None
                img_h = float(_ph) if _ph else None
            pages_results.append((page_no, elements))
            page_dims[page_no] = (img_w, img_h)
            logger.info(
                "PPStructureParser page done: page=%d/%d blocks=%d job_id=%s",
                page_no, len(pages_data), len(elements), job_id,
            )

        self._cleanup_temp_pages(pages_data, ext)

        blocks = self._extract_blocks(pages_results, job_id)

        filename = Path(file_path).name
        source_ext = ext.lstrip(".")
        total_pages = len(pages_data)

        first_page_no = pages_data[0][0] if pages_data else 1
        page_width, page_height = page_dims.get(first_page_no, (None, None))

        positions = PositionsJson(
            source_file=filename,
            source_type=source_ext,
            parser="ppstructure",
            total_pages=total_pages,
            page_width=page_width,
            page_height=page_height,
            coordinate_system="top-left",
            blocks=blocks,
        )

        markdown = MarkdownBuilder().build(blocks)
        logger.info(
            "PPStructureParser.parse done: job_id=%s blocks=%d pages=%d",
            job_id, len(blocks), total_pages,
        )
        return ParseOutput(markdown=markdown, positions=positions, blocks=blocks)

    # ------------------------------------------------------------------
    # 内部辅助方法
    # ------------------------------------------------------------------

    def _load_pages(
        self, file_path: str, ext: str
    ) -> List[Tuple[int, str, Optional[float], Optional[float]]]:
        """返回 [(page_no, img_path, width, height), ...]。PDF 转图片，图片直接返回。"""
        if ext == ".pdf":
            return self._pdf_to_images(file_path)
        return [(1, file_path, None, None)]

    def _pdf_to_images(
        self, pdf_path: str
    ) -> List[Tuple[int, str, float, float]]:
        """将 PDF 每页转为临时 PNG（2x 缩放提升 OCR 质量），返回 [(page_no, img_path, w, h)]。"""
        try:
            import fitz
        except ImportError:
            raise RuntimeError(
                "pymupdf is required for PDF parsing with PPStructureParser. "
                "Install: pip install pymupdf"
            )

        pages = []
        doc = fitz.open(pdf_path)
        tmp_dir = tempfile.mkdtemp(prefix="ppstructure_")

        for i, page in enumerate(doc):
            mat = fitz.Matrix(1.5, 1.5)
            pix = page.get_pixmap(matrix=mat)
            img_path = os.path.join(tmp_dir, f"page_{i + 1}.png")
            pix.save(img_path)
            pages.append((i + 1, img_path, float(page.rect.width), float(page.rect.height)))

        doc.close()
        return pages

    def _cleanup_temp_pages(
        self,
        pages_data: List[Tuple[int, str, Optional[float], Optional[float]]],
        ext: str,
    ) -> None:
        """清理 PDF 转换时生成的临时图片目录。"""
        if ext != ".pdf":
            return
        dirs_seen: set = set()
        for _, img_path, _, _ in pages_data:
            d = os.path.dirname(img_path)
            if d and "ppstructure_" in d:
                dirs_seen.add(d)
        for d in dirs_seen:
            shutil.rmtree(d, ignore_errors=True)

    def _extract_blocks(
        self,
        pages_results: List[Tuple[int, list]],
        job_id: str,
    ) -> List[BlockPosition]:
        """遍历所有页的解析结果，提取 BlockPosition 列表，block_id 全局递增。"""
        blocks: List[BlockPosition] = []
        counter = 0
        img_counter = 0
        heading_stack: List[Tuple[int, str]] = []

        for page_no, elements in pages_results:
            if not isinstance(elements, list):
                continue

            for elem in elements:
                if not isinstance(elem, dict):
                    continue

                elem_type = str(elem.get("type", "") or "text").lower()
                block_type = _TYPE_MAP.get(elem_type, BlockType.paragraph)

                bbox_raw = elem.get("bbox", [0.0, 0.0, 0.0, 0.0])
                bbox = [float(v) for v in (list(bbox_raw) + [0.0, 0.0, 0.0, 0.0])[:4]]

                text = ""
                image_path = None
                heading_level = None

                if block_type == BlockType.heading:
                    text = self._extract_text(elem)
                    if elem_type == "doc_title":
                        heading_level = 1
                    else:
                        heading_level = self._infer_heading_level(text)
                elif block_type in (BlockType.paragraph, BlockType.list, BlockType.formula):
                    text = self._extract_text(elem)
                elif block_type == BlockType.table:
                    html = self._extract_table_html(elem)
                    text = self._table_html_to_md(html)
                elif block_type == BlockType.image:
                    text = self._extract_text(elem) or ""
                    img_counter += 1
                    image_path = f"images/{job_id}/img-{img_counter:04d}.png"

                if not text.strip() and block_type != BlockType.image:
                    continue

                heading_path = " > ".join(h[1] for h in heading_stack) or None

                counter += 1
                block = BlockPosition(
                    block_id=f"b-{counter:04d}",
                    type=block_type,
                    level=heading_level,
                    text=text,
                    page=page_no,
                    bbox=bbox,
                    heading_path=heading_path,
                    image_path=image_path,
                )
                blocks.append(block)

                if block_type == BlockType.heading:
                    level = heading_level or 1
                    while heading_stack and heading_stack[-1][0] >= level:
                        heading_stack.pop()
                    heading_stack.append((level, text))

        return blocks

    def _extract_text(self, elem: dict) -> str:
        """从 element 中提取文本，兼容多种 PP-StructureV3 result 格式。"""
        direct = elem.get("text")
        if direct:
            return str(direct)

        res = elem.get("res")
        if res is None:
            return ""

        if isinstance(res, list):
            parts = []
            for line in res:
                if isinstance(line, dict):
                    parts.append(str(line.get("text", "")))
                elif isinstance(line, (list, tuple)) and len(line) >= 1:
                    parts.append(str(line[0]))
                else:
                    parts.append(str(line))
            return " ".join(p for p in parts if p.strip())

        if isinstance(res, dict):
            for key in ("text", "content", "rec_res"):
                val = res.get(key)
                if val:
                    if isinstance(val, list):
                        return " ".join(
                            str(v[0]) if isinstance(v, (list, tuple)) else str(v)
                            for v in val
                        )
                    return str(val)

        if isinstance(res, str):
            return res

        return ""

    def _normalize_blocks(self, blocks: list) -> list:
        """将 LayoutBlock 对象转换为统一 dict 格式，dict 格式直接透传。"""
        result = []
        for blk in blocks:
            if isinstance(blk, dict):
                result.append(blk)
            elif hasattr(blk, 'label'):
                result.append({
                    "type": str(blk.label),
                    "bbox": list(blk.bbox) if blk.bbox else [0.0, 0.0, 0.0, 0.0],
                    "text": str(blk.content or ""),
                })
        return result

    def _extract_table_html(self, elem: dict) -> str:
        """从 table element 提取 HTML 字符串。"""
        direct = elem.get("text", "")
        if direct and "<" in direct:
            return direct
        res = elem.get("res", {})
        if isinstance(res, dict):
            return str(res.get("html", "") or res.get("content", ""))
        return str(direct)

    def _table_html_to_md(self, html: str) -> str:
        """HTML 表格 → Markdown（pandas.read_html）；解析失败时回退为 HTML 代码块。"""
        if not html.strip():
            return ""
        try:
            dfs = pd.read_html(StringIO(html))
            if dfs:
                md = dfs[0].to_markdown(index=False)
                return md or ""
        except Exception as exc:
            logger.debug("_table_html_to_md fallback: %s", exc)
        return f"```html\n{html}\n```"

    def _infer_heading_level(self, text: str) -> int:
        """从文本内容推断标题级别（1~3），默认返回 1。"""
        import re
        if re.match(r"^第[一二三四五六七八九十百千]+[章节篇]", text):
            return 1
        if re.match(r"^\d+\.\d+\.\d+", text):
            return 3
        if re.match(r"^\d+\.\d+", text):
            return 2
        if re.match(r"^\d+[、．.]|^[一二三四五六七八九十]+[、．.]", text):
            return 2
        return 1
