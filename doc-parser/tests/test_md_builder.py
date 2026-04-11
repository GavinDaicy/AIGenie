"""
I2-B7 MarkdownBuilder 单元测试（无外部依赖）。
"""
import pytest
from app.models.position import BlockPosition, BlockType
from app.parsers.md_builder import MarkdownBuilder


def _make_block(block_id, btype, text, level=None, image_path=None):
    return BlockPosition(
        block_id=block_id,
        type=btype,
        text=text,
        level=level,
        page=1,
        bbox=[0.0, 0.0, 100.0, 20.0],
        image_path=image_path,
    )


class TestMarkdownBuilder:
    def test_heading_h1(self):
        blocks = [_make_block("b-0001", BlockType.heading, "Introduction", level=1)]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0001 -->" in md
        assert "# Introduction" in md

    def test_heading_h2(self):
        blocks = [_make_block("b-0001", BlockType.heading, "Section 1", level=2)]
        md = MarkdownBuilder().build(blocks)
        assert "## Section 1" in md

    def test_heading_h3(self):
        blocks = [_make_block("b-0001", BlockType.heading, "Sub", level=3)]
        md = MarkdownBuilder().build(blocks)
        assert "### Sub" in md

    def test_heading_default_level_is_1(self):
        """heading 未指定 level 时默认输出 H1。"""
        blocks = [_make_block("b-0001", BlockType.heading, "Title")]
        md = MarkdownBuilder().build(blocks)
        assert "# Title" in md

    def test_paragraph(self):
        blocks = [_make_block("b-0001", BlockType.paragraph, "Some text here.")]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0001 -->" in md
        assert "Some text here." in md

    def test_table(self):
        table_text = "| A | B |\n|---|---|\n| 1 | 2 |"
        blocks = [_make_block("b-0001", BlockType.table, table_text)]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0001 -->" in md
        assert "| A | B |" in md

    def test_image(self):
        blocks = [_make_block("b-0001", BlockType.image, "Figure 1", image_path="images/job/img-0001.png")]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0001 -->" in md
        assert "![Figure 1](images/job/img-0001.png)" in md

    def test_code_block(self):
        blocks = [_make_block("b-0001", BlockType.code, "print('hello')")]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0001 -->" in md
        assert "```" in md
        assert "print('hello')" in md

    def test_formula_block(self):
        blocks = [_make_block("b-0001", BlockType.formula, r"E=mc^2")]
        md = MarkdownBuilder().build(blocks)
        assert "$$" in md
        assert r"E=mc^2" in md

    def test_list_block(self):
        blocks = [_make_block("b-0001", BlockType.list, "- Item A")]
        md = MarkdownBuilder().build(blocks)
        assert "- Item A" in md

    def test_anchor_order(self):
        """锚点按 block 顺序出现。"""
        blocks = [
            _make_block("b-0001", BlockType.heading, "Chapter 1", level=1),
            _make_block("b-0002", BlockType.paragraph, "Content."),
            _make_block("b-0003", BlockType.table, "| X |\n|---|\n| 1 |"),
        ]
        md = MarkdownBuilder().build(blocks)
        pos_1 = md.index("b-0001")
        pos_2 = md.index("b-0002")
        pos_3 = md.index("b-0003")
        assert pos_1 < pos_2 < pos_3

    def test_anchor_format(self):
        """锚点格式严格符合 <!-- @block:{block_id} -->。"""
        blocks = [_make_block("b-0042", BlockType.paragraph, "text")]
        md = MarkdownBuilder().build(blocks)
        assert "<!-- @block:b-0042 -->" in md

    def test_empty_blocks_returns_empty_string(self):
        md = MarkdownBuilder().build([])
        assert md.strip() == ""

    def test_multiple_headings_with_paragraphs(self):
        blocks = [
            _make_block("b-0001", BlockType.heading, "Title", level=1),
            _make_block("b-0002", BlockType.paragraph, "Intro."),
            _make_block("b-0003", BlockType.heading, "Section", level=2),
            _make_block("b-0004", BlockType.paragraph, "Detail."),
        ]
        md = MarkdownBuilder().build(blocks)
        assert "# Title" in md
        assert "## Section" in md
        assert "Intro." in md
        assert "Detail." in md
