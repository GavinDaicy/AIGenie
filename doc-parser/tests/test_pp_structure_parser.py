"""
I3-B6 PP-StructureV3 Parser 单元测试（无需安装 paddlepaddle/paddleocr）。

所有单元测试通过 Mock _get_engine() 注入伪引擎，避免真实模型依赖。
集成测试用 @pytest.mark.skipif 保护，仅在已安装 paddleocr 环境执行。
"""
import os
import tempfile
from io import StringIO
from typing import List, Tuple
from unittest.mock import MagicMock, patch

import pandas as pd
import pytest

from app.models.position import BlockPosition, BlockType
from app.parsers.pp_structure_parser import PPStructureParser

# ------------------------------------------------------------------
# 集成测试守卫
# ------------------------------------------------------------------

try:
    import paddleocr  # noqa: F401
    HAS_PPSTRUCTURE = True
except ImportError:
    HAS_PPSTRUCTURE = False


# ------------------------------------------------------------------
# 辅助工具
# ------------------------------------------------------------------

def _make_elem(
    elem_type: str,
    text: str = "",
    bbox: List[float] = None,
    html: str = "",
) -> dict:
    """构造 PP-StructureV3 风格的 element dict。"""
    elem: dict = {"type": elem_type, "bbox": bbox or [10.0, 20.0, 200.0, 50.0]}
    if elem_type == "table":
        elem["res"] = {"html": html}
    else:
        elem["res"] = [{"text": text}] if text else []
        if text:
            elem["text"] = text
    return elem


class FakeEngine:
    """按页序依次返回预设 element 列表的伪引擎（兼容 paddleocr 3.4.x predict() API）。"""

    def __init__(self, pages: List[List[dict]]):
        self._pages = pages
        self._call_count = 0

    def predict(self, img_path: str) -> list:
        idx = self._call_count % len(self._pages)
        self._call_count += 1
        elements = self._pages[idx]
        return [{"parsing_res_list": elements, "width": 800.0, "height": 600.0}]


def _make_parser_with_engine(engine) -> PPStructureParser:
    """创建 PPStructureParser 并注入伪引擎。"""
    parser = PPStructureParser()
    parser._get_engine = lambda: engine
    return parser


def _run_parse_with_fake_image(parser: PPStructureParser, elements: List[dict]) -> "ParseOutput":
    """用临时 1×1 PNG 文件运行 parse()，引擎返回预设 elements。"""
    engine = FakeEngine([elements])
    parser._get_engine = lambda: engine

    with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as f:
        f.write(
            b"\x89PNG\r\n\x1a\n\x00\x00\x00\rIHDR\x00\x00\x00\x01"
            b"\x00\x00\x00\x01\x08\x02\x00\x00\x00\x90wS\xde\x00\x00"
            b"\x00\x0cIDATx\x9cc\xf8\x0f\x00\x00\x01\x01\x00\x05\x18"
            b"\xd8N\x00\x00\x00\x00IEND\xaeB`\x82"
        )
        tmp_path = f.name

    try:
        output = parser.parse(tmp_path, "job-test")
    finally:
        os.unlink(tmp_path)

    return output


# ==================================================================
# 一、类型映射测试
# ==================================================================

class TestTypeMapping:
    def test_title_maps_to_heading(self):
        parser = PPStructureParser()
        elems = [_make_elem("title", "第一章 概述")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.heading

    def test_text_maps_to_paragraph(self):
        parser = PPStructureParser()
        elems = [_make_elem("text", "这是正文内容")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.paragraph

    def test_table_maps_to_table(self):
        parser = PPStructureParser()
        html = "<table><tr><th>A</th><th>B</th></tr><tr><td>1</td><td>2</td></tr></table>"
        elems = [_make_elem("table", html=html)]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.table

    def test_figure_maps_to_image(self):
        parser = PPStructureParser()
        elems = [_make_elem("figure", "图1-1 示意图")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.image

    def test_formula_maps_to_formula(self):
        parser = PPStructureParser()
        elems = [_make_elem("formula", r"E = mc^2")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.formula

    def test_list_maps_to_list(self):
        parser = PPStructureParser()
        elems = [_make_elem("list", "• 条目一")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.list

    def test_unknown_type_falls_back_to_paragraph(self):
        parser = PPStructureParser()
        elems = [{"type": "unknown_type", "bbox": [0, 0, 100, 20], "text": "未知块"}]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.paragraph


# ==================================================================
# 二、bbox 直接使用（无坐标转换）
# ==================================================================

class TestBboxDirectUse:
    def test_bbox_preserved_exactly(self):
        parser = PPStructureParser()
        raw_bbox = [72.5, 130.0, 540.0, 160.0]
        elems = [{"type": "text", "bbox": raw_bbox, "text": "测试文本"}]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].bbox == raw_bbox

    def test_bbox_all_zeros_when_missing(self):
        parser = PPStructureParser()
        elems = [{"type": "text", "text": "无坐标文本"}]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].bbox == [0.0, 0.0, 0.0, 0.0]

    def test_bbox_no_y_flip(self):
        """PP-Structure 坐标无需翻转：y0 始终 < y1（左上角原点）。"""
        parser = PPStructureParser()
        elems = [{"type": "text", "bbox": [10.0, 50.0, 300.0, 80.0], "text": "ok"}]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        x0, y0, x1, y1 = blocks[0].bbox
        assert y0 < y1, "top-left 坐标系下 y0 应小于 y1"


# ==================================================================
# 三、多页 block_id 全局递增
# ==================================================================

class TestBlockIdGlobalIncrement:
    def test_single_page_ids(self):
        parser = PPStructureParser()
        elems = [_make_elem("text", f"段落{i}") for i in range(3)]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert [b.block_id for b in blocks] == ["b-0001", "b-0002", "b-0003"]

    def test_multi_page_ids_are_global(self):
        parser = PPStructureParser()
        page1 = [_make_elem("text", "第一页第一段"), _make_elem("text", "第一页第二段")]
        page2 = [_make_elem("text", "第二页第一段")]
        blocks = parser._extract_blocks([(1, page1), (2, page2)], "job-x")
        assert [b.block_id for b in blocks] == ["b-0001", "b-0002", "b-0003"]

    def test_page_number_stored_correctly(self):
        parser = PPStructureParser()
        page1 = [_make_elem("text", "p1")]
        page2 = [_make_elem("text", "p2")]
        blocks = parser._extract_blocks([(1, page1), (2, page2)], "job-x")
        assert blocks[0].page == 1
        assert blocks[1].page == 2


# ==================================================================
# 四、heading_path 标题栈逻辑
# ==================================================================

class TestHeadingPath:
    def test_paragraph_under_heading_gets_path(self):
        parser = PPStructureParser()
        elems = [
            _make_elem("title", "第一章 概述"),
            _make_elem("text", "正文内容"),
        ]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].type == BlockType.heading
        assert blocks[1].heading_path == "第一章 概述"

    def test_heading_path_updates_on_new_heading(self):
        parser = PPStructureParser()
        elems = [
            _make_elem("title", "第一章"),
            _make_elem("title", "第二章"),
            _make_elem("text", "第二章内容"),
        ]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[2].heading_path == "第二章"

    def test_nested_heading_path(self):
        parser = PPStructureParser()
        elems = [
            {"type": "title", "bbox": [0, 0, 100, 20], "text": "第一章"},
            {"type": "title", "bbox": [0, 0, 100, 20], "res": [{"text": "1.1 小节"}]},
            _make_elem("text", "小节内容"),
        ]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        # 标题 level 推断：第一章=1，1.1=2 → 嵌套
        para = blocks[-1]
        assert para.heading_path is not None

    def test_first_block_heading_path_is_none(self):
        parser = PPStructureParser()
        elems = [_make_elem("text", "开篇文字，无标题")]
        blocks = parser._extract_blocks([(1, elems)], "job-x")
        assert blocks[0].heading_path is None


# ==================================================================
# 五、_table_html_to_md 测试
# ==================================================================

class TestTableHtmlToMd:
    def setup_method(self):
        self.parser = PPStructureParser()

    def test_simple_table_converts_to_markdown(self):
        html = "<table><tr><th>姓名</th><th>年龄</th></tr><tr><td>张三</td><td>25</td></tr></table>"
        md = self.parser._table_html_to_md(html)
        assert "|" in md
        assert "张三" in md

    def test_table_without_header(self):
        html = "<table><tr><td>A</td><td>B</td></tr><tr><td>1</td><td>2</td></tr></table>"
        md = self.parser._table_html_to_md(html)
        assert "|" in md

    def test_invalid_html_falls_back_to_code_block(self):
        bad_html = "<not-a-table>garbage content</not-a-table>"
        md = self.parser._table_html_to_md(bad_html)
        assert "```html" in md or "```" in md

    def test_empty_html_returns_empty_string(self):
        assert self.parser._table_html_to_md("") == ""
        assert self.parser._table_html_to_md("   ") == ""

    def test_multi_column_table(self):
        html = (
            "<table>"
            "<tr><th>指标</th><th>Q1</th><th>Q2</th><th>Q3</th></tr>"
            "<tr><td>营收</td><td>100</td><td>120</td><td>130</td></tr>"
            "</table>"
        )
        md = self.parser._table_html_to_md(html)
        assert "营收" in md
        assert md.count("|") >= 4

    def test_real_html_without_thead(self):
        html = (
            "<html><body><table>"
            "<tbody><tr><td>x</td><td>y</td></tr></tbody>"
            "</table></body></html>"
        )
        md = self.parser._table_html_to_md(html)
        assert "x" in md or "```" in md


# ==================================================================
# 六、_extract_text 兼容多种 result 格式
# ==================================================================

class TestExtractText:
    def setup_method(self):
        self.parser = PPStructureParser()

    def test_direct_text_field(self):
        elem = {"type": "text", "text": "直接文本", "bbox": []}
        assert self.parser._extract_text(elem) == "直接文本"

    def test_res_list_of_dicts(self):
        elem = {"type": "text", "res": [{"text": "行一"}, {"text": "行二"}], "bbox": []}
        result = self.parser._extract_text(elem)
        assert "行一" in result and "行二" in result

    def test_res_dict_with_text_key(self):
        elem = {"type": "text", "res": {"text": "字典文本"}, "bbox": []}
        assert self.parser._extract_text(elem) == "字典文本"

    def test_res_string(self):
        elem = {"type": "text", "res": "字符串结果", "bbox": []}
        assert self.parser._extract_text(elem) == "字符串结果"

    def test_empty_res_returns_empty(self):
        elem = {"type": "text", "res": [], "bbox": []}
        assert self.parser._extract_text(elem) == ""

    def test_no_res_returns_empty(self):
        elem = {"type": "text", "bbox": []}
        assert self.parser._extract_text(elem) == ""


# ==================================================================
# 七、parse() 端到端（伪引擎 + 临时图片）
# ==================================================================

class TestParseEndToEnd:
    def test_parse_image_with_fake_engine(self):
        elements = [
            _make_elem("title", "测试标题"),
            _make_elem("text", "测试正文"),
        ]
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, elements)

        assert output.positions.parser == "ppstructure"
        assert output.positions.coordinate_system == "top-left"
        assert len(output.blocks) == 2
        assert output.blocks[0].type == BlockType.heading
        assert output.blocks[1].type == BlockType.paragraph

    def test_parse_returns_markdown_with_anchors(self):
        elements = [_make_elem("text", "内容段落")]
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, elements)
        assert "<!-- @block:" in output.markdown
        assert "内容段落" in output.markdown

    def test_parse_image_block_gets_image_path(self):
        elements = [_make_elem("figure", "图表说明")]
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, elements)
        assert len(output.blocks) == 1
        assert output.blocks[0].type == BlockType.image
        assert output.blocks[0].image_path == "images/job-test/img-0001.png"

    def test_parse_empty_elements_returns_empty_blocks(self):
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, [])
        assert output.blocks == []
        assert output.markdown == ""

    def test_parse_table_element_converts_html(self):
        html = "<table><tr><th>Col</th></tr><tr><td>Val</td></tr></table>"
        elements = [_make_elem("table", html=html)]
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, elements)
        assert output.blocks[0].type == BlockType.table
        assert "|" in output.blocks[0].text

    def test_parse_positions_json_structure(self):
        elements = [_make_elem("text", "段落")]
        parser = PPStructureParser()
        output = _run_parse_with_fake_image(parser, elements)
        pos = output.positions
        assert pos.total_pages == 1
        assert len(pos.blocks) == 1
        assert pos.blocks[0].block_id == "b-0001"


# ==================================================================
# 八、_infer_heading_level 标题级别推断
# ==================================================================

class TestInferHeadingLevel:
    def setup_method(self):
        self.parser = PPStructureParser()

    def test_chapter_heading_is_level_1(self):
        assert self.parser._infer_heading_level("第一章 财务概览") == 1

    def test_three_level_numbering_is_level_3(self):
        assert self.parser._infer_heading_level("1.2.3 详细说明") == 3

    def test_two_level_numbering_is_level_2(self):
        assert self.parser._infer_heading_level("1.2 小节标题") == 2

    def test_default_level_is_1(self):
        assert self.parser._infer_heading_level("普通标题") == 1

    def test_chinese_numbered_heading(self):
        level = self.parser._infer_heading_level("一、总体目标")
        assert level == 2


# ==================================================================
# 九、集成测试（需要真实 paddleocr 环境）
# ==================================================================

@pytest.mark.skipif(not HAS_PPSTRUCTURE, reason="paddleocr not installed")
class TestPPStructureParserIntegration:
    def test_engine_can_be_instantiated(self):
        parser = PPStructureParser()
        engine = parser._get_engine()
        assert engine is not None

    def test_parse_real_image(self, tmp_path):
        from PIL import Image
        img = Image.new("RGB", (400, 200), color=(255, 255, 255))
        img_path = str(tmp_path / "test.png")
        img.save(img_path)

        parser = PPStructureParser()
        output = parser.parse(img_path, "job-integ")
        assert output.positions.parser == "ppstructure"
        assert output.positions.coordinate_system == "top-left"
        assert isinstance(output.blocks, list)
