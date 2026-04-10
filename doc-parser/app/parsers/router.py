import re
from pathlib import Path
from app.parsers.base import BaseParser

_PPSTRUCTURE_AVAILABLE: bool | None = None


def _check_ppstructure() -> bool:
    """运行时检测 paddleocr 是否可用；结果缓存到模块级变量，避免重复 import。"""
    global _PPSTRUCTURE_AVAILABLE
    if _PPSTRUCTURE_AVAILABLE is None:
        try:
            import paddleocr  # noqa: F401
            _PPSTRUCTURE_AVAILABLE = True
        except ImportError:
            _PPSTRUCTURE_AVAILABLE = False
    return _PPSTRUCTURE_AVAILABLE


def select_parser(filename: str, language_hint: str = "auto") -> str:
    """
    根据文件扩展名和语言提示选择解析引擎。
    返回值：'ppstructure' | 'docling' | 'excel'

    - PDF（所有语言）→ ppstructure（paddleocr 不可用时自动降级为 docling）
    - 中文图片 → ppstructure（paddleocr 不可用时自动降级为 docling）
    - Word / PPT / HTML / Markdown / 英文图片 → docling
    - Excel / CSV → excel
    - language_hint 为 'auto' 时检测文件名是否含中文字符自动判断
    """
    ext = Path(filename).suffix.lower()
    lang = _resolve_language(filename, language_hint)

    if ext in (".docx", ".doc", ".pptx", ".ppt", ".html", ".htm", ".md", ".adoc"):
        return "docling"

    if ext in (".xlsx", ".xls", ".csv"):
        return "excel"

    if ext == ".pdf":
        return "ppstructure" if _check_ppstructure() else "docling"

    if ext in (".png", ".jpg", ".jpeg", ".tiff", ".bmp", ".webp"):
        if lang in ("zh", "auto"):
            return "ppstructure" if _check_ppstructure() else "docling"
        return "docling"

    return "docling"


def _resolve_language(filename: str, language_hint: str) -> str:
    if language_hint != "auto":
        return language_hint
    if re.search(r"[\u4e00-\u9fff\u3400-\u4dbf]", filename):
        return "zh"
    return "auto"


def create_parser(engine_name: str) -> BaseParser:
    """解析器工厂方法。ppstructure 不可用时自动降级到 docling。"""
    if engine_name == "docling":
        from app.parsers.docling_parser import DoclingParser
        return DoclingParser()
    if engine_name == "excel":
        from app.parsers.excel_parser import ExcelParser
        return ExcelParser()
    if engine_name == "ppstructure":
        try:
            from app.parsers.pp_structure_parser import PPStructureParser
            return PPStructureParser()
        except ImportError:
            from app.parsers.docling_parser import DoclingParser
            return DoclingParser()
    raise ValueError(f"Unknown parser engine: {engine_name}")
