"""
I2-B7 / I3-B6 路由策略单元测试（无外部依赖）。

ppstructure 可用性通过 monkeypatch 控制，测试不依赖真实 paddleocr 安装。
"""
import pytest
import app.parsers.router as router_mod
from app.parsers.router import select_parser


@pytest.fixture(autouse=True)
def reset_ppstructure_cache():
    """每个测试前重置 _PPSTRUCTURE_AVAILABLE 缓存，防止测试间状态污染。"""
    original = router_mod._PPSTRUCTURE_AVAILABLE
    yield
    router_mod._PPSTRUCTURE_AVAILABLE = original


@pytest.fixture()
def ppstructure_available(monkeypatch):
    """模拟 paddleocr 可用。"""
    monkeypatch.setattr(router_mod, "_check_ppstructure", lambda: True)
    monkeypatch.setattr(router_mod, "_PPSTRUCTURE_AVAILABLE", True)


@pytest.fixture()
def ppstructure_unavailable(monkeypatch):
    """模拟 paddleocr 不可用（ImportError）。"""
    monkeypatch.setattr(router_mod, "_check_ppstructure", lambda: False)
    monkeypatch.setattr(router_mod, "_PPSTRUCTURE_AVAILABLE", False)


class TestSelectParser:
    # ---------- Office / Markup → Docling（不受 ppstructure 可用性影响）----------

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

    # ---------- PDF 路由（ppstructure 可用）----------

    def test_pdf_auto_hint_routes_to_ppstructure(self, ppstructure_available):
        assert select_parser("doc.pdf", "auto") == "ppstructure"

    def test_pdf_zh_hint_routes_to_ppstructure(self, ppstructure_available):
        assert select_parser("doc.pdf", "zh") == "ppstructure"

    def test_pdf_en_hint_routes_to_ppstructure(self, ppstructure_available):
        assert select_parser("doc.pdf", "en") == "ppstructure"

    def test_pdf_chinese_filename_routes_to_ppstructure(self, ppstructure_available):
        """文件名含中文字符，自动检测语言为 zh，路由到 ppstructure。"""
        assert select_parser("财务报告2024.pdf") == "ppstructure"

    def test_pdf_english_filename_with_auto_routes_to_ppstructure(self, ppstructure_available):
        """英文文件名 + auto 提示，auto 判断为非中文，仍走 ppstructure（auto 分支）。"""
        assert select_parser("annual_report.pdf") == "ppstructure"

    # ---------- 图片路由（ppstructure 可用）----------

    def test_png_auto_routes_to_ppstructure(self, ppstructure_available):
        assert select_parser("scan.png", "auto") == "ppstructure"

    def test_jpg_zh_routes_to_ppstructure(self, ppstructure_available):
        assert select_parser("scan.jpg", "zh") == "ppstructure"

    def test_png_en_routes_to_docling(self):
        assert select_parser("diagram.png", "en") == "docling"

    def test_tiff_en_routes_to_docling(self):
        assert select_parser("photo.tiff", "en") == "docling"

    # ---------- ppstructure 不可用 → 自动降级 docling ----------

    def test_pdf_zh_fallback_to_docling_when_ppstructure_unavailable(self, ppstructure_unavailable):
        """paddleocr 不可用时，中文 PDF 自动降级到 docling。"""
        assert select_parser("doc.pdf", "zh") == "docling"

    def test_pdf_auto_fallback_to_docling_when_ppstructure_unavailable(self, ppstructure_unavailable):
        assert select_parser("doc.pdf", "auto") == "docling"

    def test_image_zh_fallback_to_docling_when_ppstructure_unavailable(self, ppstructure_unavailable):
        assert select_parser("scan.png", "zh") == "docling"

    def test_chinese_filename_fallback_to_docling_when_ppstructure_unavailable(self, ppstructure_unavailable):
        assert select_parser("财务报告.png") == "docling"

    # ---------- 未知扩展名 → Docling 兜底 ----------

    def test_unknown_ext_routes_to_docling(self):
        assert select_parser("file.xyz") == "docling"

    def test_no_ext_routes_to_docling(self):
        assert select_parser("noextension") == "docling"

    # ---------- 大小写不敏感 ----------

    def test_uppercase_extension(self):
        assert select_parser("doc.DOCX") == "docling"

    def test_mixed_case_pdf_en(self, ppstructure_available):
        assert select_parser("DOC.PDF", "en") == "ppstructure"
