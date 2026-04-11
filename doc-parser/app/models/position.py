from enum import Enum
from typing import Optional, List
from pydantic import BaseModel


class BlockType(str, Enum):
    heading = "heading"
    paragraph = "paragraph"
    table = "table"
    image = "image"
    list = "list"
    code = "code"
    formula = "formula"


class BlockPosition(BaseModel):
    block_id: str
    type: BlockType
    level: Optional[int] = None          # heading 时有效（1~6）
    text: str
    page: int
    bbox: List[float]                    # [x0, y0, x1, y1]，左上角原点
    heading_path: Optional[str] = None   # 所属标题层级路径
    image_path: Optional[str] = None     # image 类型时的相对路径


class PositionsJson(BaseModel):
    source_file: str
    source_type: str                     # pdf / docx / pptx / xlsx / image
    parser: str                          # mineru / docling / excel
    total_pages: int
    page_width: Optional[float] = None
    page_height: Optional[float] = None
    coordinate_system: str = "top-left"
    blocks: List[BlockPosition] = []
