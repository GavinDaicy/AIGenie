import re
from pathlib import Path
from app.parsers.base import BaseParser


def select_parser(filename: str, language_hint: str = "auto") -> str:
    """
    根据文件扩展名和语言提示选择解析引擎。
    返回值：'mineru' | 'docling' | 'excel'

    language_hint 为 'auto' 时：检测文件名是否含中文字符，自动判断。
    """
    ext = Path(filename).suffix.lower()
    lang = _resolve_language(filename, language_hint)

    if ext in (".docx", ".doc", ".pptx", ".ppt", ".html", ".htm", ".md", ".adoc"):
        return "docling"

    if ext in (".xlsx", ".xls", ".csv"):
        return "excel"

    if ext == ".pdf":
        return "mineru" if lang in ("zh", "auto") else "docling"

    if ext in (".png", ".jpg", ".jpeg", ".tiff", ".bmp", ".webp"):
        return "mineru" if lang in ("zh", "auto") else "docling"

    return "docling"


def _resolve_language(filename: str, language_hint: str) -> str:
    if language_hint != "auto":
        return language_hint
    if re.search(r"[\u4e00-\u9fff\u3400-\u4dbf]", filename):
        return "zh"
    return "auto"


def create_parser(engine_name: str) -> BaseParser:
    """
    解析器工厂方法。
    I2 阶段接入真实实现，当前仅注册占位检查。
    """
    if engine_name == "docling":
        from app.parsers.docling_parser import DoclingParser
        return DoclingParser()
    if engine_name == "excel":
        from app.parsers.excel_parser import ExcelParser
        return ExcelParser()
    if engine_name == "mineru":
        try:
            from app.parsers.mineru_parser import MinerUParser
            return MinerUParser()
        except ImportError:
            from app.parsers.docling_parser import DoclingParser
            return DoclingParser()
    raise ValueError(f"Unknown parser engine: {engine_name}")
