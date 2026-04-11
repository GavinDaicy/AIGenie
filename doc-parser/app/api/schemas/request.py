from typing import Optional
from pydantic import BaseModel, HttpUrl, field_validator


class _ParseCommonFields(BaseModel):
    language: str = "auto"
    parser: str = "auto"
    callback_url: Optional[str] = None

    @field_validator("language")
    @classmethod
    def validate_language(cls, v: str) -> str:
        if v not in ("zh", "en", "auto"):
            raise ValueError("language must be 'zh', 'en', or 'auto'")
        return v

    @field_validator("parser")
    @classmethod
    def validate_parser(cls, v: str) -> str:
        if v not in ("auto", "mineru", "docling", "excel"):
            raise ValueError("parser must be 'auto', 'mineru', 'docling', or 'excel'")
        return v


class ParseUrlRequest(_ParseCommonFields):
    file_url: str
    filename: str
