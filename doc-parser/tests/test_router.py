"""
I2-B7 路由策略单元测试（无外部依赖）。
"""
import pytest
from app.parsers.router import select_parser


class TestSelectParser:
    # ---------- Office / Markup → Docling ----------
    def test_docx_routes_to_docling(self):
        assert select_parser("report.docx") == "docling"

    def test_doc_routes_to_docling(self):
        assert select_parser("report.doc") == "docling"

    def test_pptx_routes_to_docling(self):
        assert select_parser("slides.pptx") == "docling"

    def test_ppt_routes_to_docling(self):
        assert select_parser("slides.ppt") == "docling"

    def test_html_routes_to_docling(self):
        assert select_parser("page.html") == "docling"

    def test_htm_routes_to_docling(self):
        assert select_parser("page.htm") == "docling"

    def test_md_routes_to_docling(self):
        assert select_parser("readme.md") == "docling"

    def test_adoc_routes_to_docling(self):
        assert select_parser("doc.adoc") == "docling"

    # ---------- Excel / CSV → Excel Parser ----------
    def test_xlsx_routes_to_excel(self):
        assert select_parser("data.xlsx") == "excel"

    def test_xls_routes_to_excel(self):
        assert select_parser("data.xls") == "excel"

    def test_csv_routes_to_excel(self):
        assert select_parser("data.csv") == "excel"

    # ---------- PDF 路由 ----------
    def test_pdf_auto_hint_routes_to_mineru(self):
        assert select_parser("doc.pdf", "auto") == "mineru"

    def test_pdf_zh_hint_routes_to_mineru(self):
        assert select_parser("doc.pdf", "zh") == "mineru"

    def test_pdf_en_hint_routes_to_docling(self):
        assert select_parser("doc.pdf", "en") == "docling"

    def test_pdf_chinese_filename_routes_to_mineru(self):
        """文件名含中文字符，自动检测语言为 zh，路由到 mineru。"""
        assert select_parser("财务报告2024.pdf") == "mineru"

    def test_pdf_english_filename_with_auto_routes_to_mineru(self):
        """英文文件名 + auto 提示，auto 判断为非中文，仍走 mineru（auto 分支）。"""
        assert select_parser("annual_report.pdf") == "mineru"

    # ---------- 图片路由 ----------
    def test_png_auto_routes_to_mineru(self):
        assert select_parser("scan.png", "auto") == "mineru"

    def test_jpg_zh_routes_to_mineru(self):
        assert select_parser("scan.jpg", "zh") == "mineru"

    def test_png_en_routes_to_docling(self):
        assert select_parser("diagram.png", "en") == "docling"

    def test_tiff_en_routes_to_docling(self):
        assert select_parser("photo.tiff", "en") == "docling"

    # ---------- 未知扩展名 → Docling 兜底 ----------
    def test_unknown_ext_routes_to_docling(self):
        assert select_parser("file.xyz") == "docling"

    def test_no_ext_routes_to_docling(self):
        assert select_parser("noextension") == "docling"

    # ---------- 大小写不敏感 ----------
    def test_uppercase_extension(self):
        assert select_parser("doc.DOCX") == "docling"

    def test_mixed_case_pdf_en(self):
        assert select_parser("DOC.PDF", "en") == "docling"
