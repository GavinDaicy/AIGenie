from typing import List
from app.models.position import BlockPosition, BlockType


class MarkdownBuilder:
    """将 BlockPosition 列表拼装为带 block 锚点的 Markdown 文本。"""

    def build(self, blocks: List[BlockPosition]) -> str:
        parts = []
        for block in blocks:
            parts.append(f"<!-- @block:{block.block_id} -->")
            parts.append(self._render_block(block))
            parts.append("")
        return "\n".join(parts)

    def _render_block(self, block: BlockPosition) -> str:
        if block.type == BlockType.heading:
            level = block.level or 1
            return "#" * level + " " + block.text
        if block.type == BlockType.table:
            return block.text
        if block.type == BlockType.image:
            caption = block.text or ""
            path = block.image_path or ""
            return f"![{caption}]({path})"
        if block.type == BlockType.code:
            return f"```\n{block.text}\n```"
        if block.type == BlockType.formula:
            return f"$$\n{block.text}\n$$"
        return block.text
