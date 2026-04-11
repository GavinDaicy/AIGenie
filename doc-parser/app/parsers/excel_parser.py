import logging
from pathlib import Path
from typing import Dict

from app.models.position import BlockPosition, BlockType, PositionsJson
from app.parsers.base import BaseParser, ParseOutput

logger = logging.getLogger(__name__)


class ExcelParser(BaseParser):
    """
    基于 pandas 的表格文件解析器，支持 .xlsx / .xls / .csv。
    每个 Sheet（或 CSV 整体）作为一个 table 类型 block。
    Excel 无坐标概念，bbox 统一设为 [0, 0, 0, 0]。
    """

    def parse(self, file_path: str, job_id: str) -> ParseOutput:
        import pandas as pd

        filename = Path(file_path).name
        ext = Path(file_path).suffix.lower()

        logger.info("ExcelParser.parse start: file=%s job_id=%s", file_path, job_id)

        sheets: Dict[str, "pd.DataFrame"] = self._read_file(file_path, ext, pd)

        blocks = []
        counter = 0
        for sheet_idx, (sheet_name, df) in enumerate(sheets.items(), start=1):
            if df is None or df.empty:
                continue

            md_table = self._df_to_markdown(df)
            counter += 1
            block = BlockPosition(
                block_id=f"b-{counter:04d}",
                type=BlockType.table,
                text=md_table,
                page=sheet_idx,
                bbox=[0.0, 0.0, 0.0, 0.0],
                heading_path=sheet_name,
            )
            blocks.append(block)

        from app.parsers.md_builder import MarkdownBuilder
        markdown = MarkdownBuilder().build(blocks)

        source_type = "csv" if ext == ".csv" else "xlsx"
        positions = PositionsJson(
            source_file=filename,
            source_type=source_type,
            parser="excel",
            total_pages=len(blocks),
            page_width=None,
            page_height=None,
            coordinate_system="top-left",
            blocks=blocks,
        )

        logger.info("ExcelParser.parse done: job_id=%s sheets=%d", job_id, len(blocks))
        return ParseOutput(markdown=markdown, positions=positions, blocks=blocks)

    def _read_file(self, file_path: str, ext: str, pd) -> Dict:
        if ext == ".csv":
            try:
                df = pd.read_csv(file_path)
            except Exception as exc:
                logger.warning("CSV read error, fallback to empty: %s", exc)
                df = pd.DataFrame()
            return {"Sheet1": df}
        try:
            sheets = pd.read_excel(file_path, sheet_name=None)
            return dict(sheets)
        except Exception as exc:
            logger.warning("Excel read error: %s", exc)
            return {}

    def _df_to_markdown(self, df) -> str:
        try:
            return df.to_markdown(index=False)
        except Exception:
            return df.to_string(index=False)
