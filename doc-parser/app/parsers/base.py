from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from typing import List
from app.models.position import BlockPosition, PositionsJson


@dataclass
class ParseOutput:
    markdown: str
    positions: PositionsJson
    blocks: List[BlockPosition] = field(default_factory=list)


class BaseParser(ABC):
    """文档解析器抽象基类（I2 阶段实现 DoclingParser / ExcelParser，I3 阶段实现 MinerUParser）。"""

    @abstractmethod
    def parse(self, file_path: str, job_id: str) -> ParseOutput:
        """
        解析文档文件。

        :param file_path: 本地文件路径
        :param job_id:    当前任务 ID（用于图片等子文件的存储路径）
        :return: ParseOutput（含 markdown 文本和 positions 元数据）
        """
