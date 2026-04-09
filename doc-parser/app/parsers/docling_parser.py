import logging
import os
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from app.models.position import BlockPosition, BlockType, PositionsJson
from app.parsers.base import BaseParser, ParseOutput

logger = logging.getLogger(__name__)

_DEFAULT_WIDTH = 595.3
_DEFAULT_HEIGHT = 841.9


class DoclingParser(BaseParser):
    """
    基于 Docling 的文档解析器，支持 PDF / DOCX / PPTX / HTML。
    输出含 block 锚点的 Markdown 和 top-left 坐标系的 positions.json。
    """

    def _get_converter(self):
        from docling.document_converter import DocumentConverter, PdfFormatOption
        from docling.datamodel.base_models import InputFormat
        from docling.datamodel.pipeline_options import PdfPipelineOptions

        pdf_opts = PdfPipelineOptions()
        pdf_opts.do_ocr = False  # I2 CPU 版不需要 OCR，避免 EasyOCR 模型下载

        return DocumentConverter(
            format_options={
                InputFormat.PDF: PdfFormatOption(pipeline_options=pdf_opts),
            }
        )

    def parse(self, file_path: str, job_id: str) -> ParseOutput:
        logger.info("DoclingParser.parse start: file=%s job_id=%s", file_path, job_id)

        converter = self._get_converter()
        conv_result = converter.convert(file_path)
        doc = conv_result.document

        page_sizes = self._extract_page_sizes(doc)
        blocks = self._extract_blocks(doc, page_sizes, job_id)

        print("blocks:", blocks)

        from app.parsers.md_builder import MarkdownBuilder
        markdown = MarkdownBuilder().build(blocks)

        filename = Path(file_path).name
        ext = Path(file_path).suffix.lower().lstrip(".")

        first_size = next(iter(page_sizes.values()), (_DEFAULT_WIDTH, _DEFAULT_HEIGHT))
        total_pages = max(page_sizes.keys(), default=1)

        positions = PositionsJson(
            source_file=filename,
            source_type=ext,
            parser="docling",
            total_pages=total_pages,
            page_width=first_size[0],
            page_height=first_size[1],
            coordinate_system="top-left",
            blocks=blocks,
        )

        logger.info(
            "DoclingParser.parse done: job_id=%s blocks=%d pages=%d",
            job_id, len(blocks), total_pages,
        )
        return ParseOutput(markdown=markdown, positions=positions, blocks=blocks)

    # ------------------------------------------------------------------
    # 内部辅助方法
    # ------------------------------------------------------------------

    def _extract_page_sizes(self, doc) -> Dict[int, Tuple[float, float]]:
        """返回 {page_no: (width, height)} 映射（page_no 从 1 开始）。"""
        sizes: Dict[int, Tuple[float, float]] = {}
        try:
            for key, page in doc.pages.items():
                pno = int(key)
                if page.size:
                    sizes[pno] = (float(page.size.width), float(page.size.height))
        except Exception as exc:
            logger.debug("_extract_page_sizes error: %s", exc)
        return sizes

    def _extract_blocks(self, doc, page_sizes: Dict[int, Tuple[float, float]], job_id: str) -> List[BlockPosition]:
        blocks: List[BlockPosition] = []
        counter = 0
        heading_stack: List[Tuple[int, str]] = []  # [(level, text), ...]
        img_counter = 0

        for item, depth in doc.iterate_items():
            print("item: ", item)
            print("depth:", depth)
            label = getattr(item, "label", None)
            if label is None:
                continue

            label_val = str(label.value) if hasattr(label, "value") else str(label)

            # 跳过页眉/页脚/脚注/复选框等非主体内容
            if label_val in (
                "page_header", "page_footer", "footnote",
                "checkbox_selected", "checkbox_unselected",
                "key_value_region", "document_index",
            ):
                continue

            block_type, text, heading_level, image_path = self._map_item(
                item, label_val, depth, job_id, img_counter
            )
            if block_type is None:
                continue
            if block_type == BlockType.image:
                img_counter += 1
            if not text.strip() and block_type not in (BlockType.image,):
                continue

            page_no, bbox = self._extract_position(item, page_sizes)
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

    def _map_item(
        self, item, label_val: str, depth: int, job_id: str, img_counter: int
    ) -> Tuple[Optional[BlockType], str, Optional[int], Optional[str]]:
        """
        将 Docling item 映射为 (BlockType, text, heading_level, image_path)。
        返回 (None, ...) 表示跳过该 item。
        """
        if label_val in ("title",):
            text = getattr(item, "text", "") or ""
            return BlockType.heading, text, 1, None

        if label_val == "section_header":
            text = getattr(item, "text", "") or ""
            level = self._resolve_heading_level(item, depth)
            return BlockType.heading, text, level, None

        if label_val == "text":
            text = getattr(item, "text", "") or ""
            return BlockType.paragraph, text, None, None

        if label_val == "list_item":
            text = getattr(item, "text", "") or ""
            if text and not text.startswith(("- ", "* ", "• ")):
                text = "- " + text
            return BlockType.list, text, None, None

        if label_val == "table":
            try:
                text = item.export_to_markdown()
            except Exception:
                text = getattr(item, "text", "") or ""
            return BlockType.table, text, None, None

        if label_val in ("picture", "figure"):
            caption = self._extract_caption(item)
            image_path = f"images/{job_id}/img-{img_counter + 1:04d}.png"
            return BlockType.image, caption, None, image_path

        if label_val == "code":
            text = getattr(item, "text", "") or ""
            return BlockType.code, text, None, None

        if label_val == "formula":
            text = getattr(item, "text", "") or ""
            return BlockType.formula, text, None, None

        if label_val == "caption":
            return None, "", None, None

        text = getattr(item, "text", "") or ""
        if text.strip():
            return BlockType.paragraph, text, None, None
        return None, "", None, None

    def _extract_position(
        self, item, page_sizes: Dict[int, Tuple[float, float]]
    ) -> Tuple[int, List[float]]:
        """返回 (page_no, [x0, y0, x1, y1]) ，坐标系为 top-left。"""
        prov_list = getattr(item, "prov", None) or []
        if not prov_list:
            return 1, [0.0, 0.0, 0.0, 0.0]

        prov = prov_list[0]
        page_no = int(getattr(prov, "page_no", 1))
        page_w, page_h = page_sizes.get(page_no, (_DEFAULT_WIDTH, _DEFAULT_HEIGHT))

        raw_bbox = getattr(prov, "bbox", None)
        if raw_bbox is None:
            return page_no, [0.0, 0.0, 0.0, 0.0]

        # 尝试使用 Docling 内置转换方法
        try:
            converted = raw_bbox.to_top_left_origin(page_h)
            bbox = [
                float(converted.l),
                float(converted.t),
                float(converted.r),
                float(converted.b),
            ]
            # 确保 y0 < y1（top-left 坐标系）
            if bbox[1] > bbox[3]:
                bbox[1], bbox[3] = bbox[3], bbox[1]
            return page_no, bbox
        except Exception:
            pass

        # 手动转换：Docling PDF 坐标（底部原点，y 向上）→ 顶部原点（y 向下）
        try:
            l = float(raw_bbox.l)
            t = float(raw_bbox.t)
            r = float(raw_bbox.r)
            b = float(raw_bbox.b)
            coord_origin = str(getattr(raw_bbox, "coord_origin", "BOTTOMLEFT")).upper()
            if "BOTTOM" in coord_origin:
                # PDF 底部原点：t=顶边(大y), b=底边(小y)
                y0 = page_h - t
                y1 = page_h - b
            else:
                # 已是顶部原点
                y0 = t
                y1 = b
            if y0 > y1:
                y0, y1 = y1, y0
            return page_no, [l, y0, r, y1]
        except Exception as exc:
            logger.debug("bbox conversion error: %s", exc)
            return page_no, [0.0, 0.0, 0.0, 0.0]

    def _resolve_heading_level(self, item, depth: int) -> int:
        """推断标题级别（1~6）。"""
        # 优先使用 item 自带的 level 属性
        for attr in ("level", "header_level", "heading_level"):
            val = getattr(item, attr, None)
            if val is not None:
                try:
                    return max(1, min(6, int(val)))
                except (TypeError, ValueError):
                    pass
        # 用 iterate_items 深度推断，depth=0 → H1，最深 H6
        return max(1, min(6, depth + 1))

    def _extract_caption(self, item) -> str:
        """提取图片 caption 文字。"""
        try:
            captions = getattr(item, "captions", None) or []
            if captions:
                cap_ref = captions[0]
                if hasattr(cap_ref, "resolve"):
                    resolved = cap_ref.resolve(item._parent_doc if hasattr(item, "_parent_doc") else None)
                    return getattr(resolved, "text", "") or ""
                return getattr(cap_ref, "text", "") or str(cap_ref)
        except Exception:
            pass
        return getattr(item, "text", "") or ""
