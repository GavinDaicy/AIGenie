"""
I2-B7 ExcelParser 单元测试。
使用 pandas + openpyxl 在内存中创建测试文件，无需预置 fixture 文件。
"""
import io
import os
import tempfile

import pytest
import pandas as pd

from app.models.position import BlockType
from app.parsers.excel_parser import ExcelParser


def _make_xlsx(sheets: dict) -> str:
    """在临时目录创建 xlsx 文件，返回文件路径。sheets={sheet_name: DataFrame}"""
    tmp = tempfile.NamedTemporaryFile(suffix=".xlsx", delete=False)
    tmp.close()
    with pd.ExcelWriter(tmp.name, engine="openpyxl") as writer:
        for name, df in sheets.items():
            df.to_excel(writer, sheet_name=name, index=False)
    return tmp.name


def _make_csv(df: pd.DataFrame) -> str:
    tmp = tempfile.NamedTemporaryFile(suffix=".csv", delete=False, mode="w", encoding="utf-8")
    df.to_csv(tmp, index=False)
    tmp.close()
    return tmp.name


class TestExcelParser:
    def test_single_sheet_produces_one_block(self, tmp_path):
        df = pd.DataFrame({"A": [1, 2], "B": [3, 4]})
        path = _make_xlsx({"Sheet1": df})
        try:
            output = ExcelParser().parse(path, "job-test")
        finally:
            os.unlink(path)

        assert len(output.blocks) == 1
        assert output.blocks[0].type == BlockType.table
        assert output.positions.parser == "excel"

    def test_multi_sheet_produces_multiple_blocks(self, tmp_path):
        sheets = {
            "Revenue": pd.DataFrame({"Q": ["Q1", "Q2"], "Val": [100, 200]}),
            "Cost": pd.DataFrame({"Q": ["Q1", "Q2"], "Val": [50, 80]}),
            "Profit": pd.DataFrame({"Q": ["Q1", "Q2"], "Val": [50, 120]}),
        }
        path = _make_xlsx(sheets)
        try:
            output = ExcelParser().parse(path, "job-multi")
        finally:
            os.unlink(path)

        assert len(output.blocks) == 3
        sheet_names = [b.heading_path for b in output.blocks]
        assert "Revenue" in sheet_names
        assert "Cost" in sheet_names
        assert "Profit" in sheet_names

    def test_page_number_equals_sheet_index(self, tmp_path):
        sheets = {
            "A": pd.DataFrame({"x": [1]}),
            "B": pd.DataFrame({"x": [2]}),
        }
        path = _make_xlsx(sheets)
        try:
            output = ExcelParser().parse(path, "job-page")
        finally:
            os.unlink(path)

        pages = [b.page for b in output.blocks]
        assert pages == [1, 2]

    def test_bbox_is_zero_placeholder(self, tmp_path):
        df = pd.DataFrame({"col": [1, 2, 3]})
        path = _make_xlsx({"Sheet1": df})
        try:
            output = ExcelParser().parse(path, "job-bbox")
        finally:
            os.unlink(path)

        for block in output.blocks:
            assert block.bbox == [0.0, 0.0, 0.0, 0.0]

    def test_block_text_is_markdown_table(self, tmp_path):
        df = pd.DataFrame({"Name": ["Alice", "Bob"], "Score": [95, 87]})
        path = _make_xlsx({"Data": df})
        try:
            output = ExcelParser().parse(path, "job-md")
        finally:
            os.unlink(path)

        text = output.blocks[0].text
        assert "|" in text
        assert "Name" in text

    def test_csv_file_parses_correctly(self, tmp_path):
        df = pd.DataFrame({"Product": ["X", "Y"], "Price": [10.5, 20.0]})
        path = _make_csv(df)
        try:
            output = ExcelParser().parse(path, "job-csv")
        finally:
            os.unlink(path)

        assert len(output.blocks) == 1
        assert output.blocks[0].type == BlockType.table
        assert output.positions.source_type == "csv"

    def test_markdown_contains_block_anchor(self, tmp_path):
        df = pd.DataFrame({"col": [1]})
        path = _make_xlsx({"Sheet1": df})
        try:
            output = ExcelParser().parse(path, "job-anchor")
        finally:
            os.unlink(path)

        assert "<!-- @block:b-0001 -->" in output.markdown

    def test_total_pages_equals_sheet_count(self, tmp_path):
        sheets = {f"S{i}": pd.DataFrame({"v": [i]}) for i in range(1, 5)}
        path = _make_xlsx(sheets)
        try:
            output = ExcelParser().parse(path, "job-pages")
        finally:
            os.unlink(path)

        assert output.positions.total_pages == 4

    def test_empty_sheet_is_skipped(self, tmp_path):
        sheets = {
            "Empty": pd.DataFrame(),
            "Data": pd.DataFrame({"col": [1]}),
        }
        path = _make_xlsx(sheets)
        try:
            output = ExcelParser().parse(path, "job-empty")
        finally:
            os.unlink(path)

        assert len(output.blocks) == 1
        assert output.blocks[0].heading_path == "Data"

    def test_block_ids_are_sequential(self, tmp_path):
        sheets = {f"S{i}": pd.DataFrame({"v": [i]}) for i in range(1, 4)}
        path = _make_xlsx(sheets)
        try:
            output = ExcelParser().parse(path, "job-ids")
        finally:
            os.unlink(path)

        ids = [b.block_id for b in output.blocks]
        assert ids == ["b-0001", "b-0002", "b-0003"]
