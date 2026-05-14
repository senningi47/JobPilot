import json
import os
import random

from app.schemas.resume import ResumeParseResponse, ResumeScoreResponse

_MOCK_PARSE_DATA: dict | None = None
_MOCK_SCORE_DATA: dict | None = None

SUPPORTED_TYPES = {".pdf", ".docx"}


def _load_parse_data() -> dict:
    """Load mock resume parse responses from JSON file."""
    global _MOCK_PARSE_DATA
    if _MOCK_PARSE_DATA is not None:
        return _MOCK_PARSE_DATA

    data_path = os.path.join(
        os.path.dirname(__file__), "mock_data", "resume_responses.json"
    )
    with open(data_path, encoding="utf-8") as f:
        _MOCK_PARSE_DATA = json.load(f)
    return _MOCK_PARSE_DATA


def _load_score_data() -> dict:
    """Load mock resume score responses from JSON file."""
    global _MOCK_SCORE_DATA
    if _MOCK_SCORE_DATA is not None:
        return _MOCK_SCORE_DATA

    data_path = os.path.join(
        os.path.dirname(__file__), "mock_data", "resume_scores.json"
    )
    with open(data_path, encoding="utf-8") as f:
        _MOCK_SCORE_DATA = json.load(f)
    return _MOCK_SCORE_DATA


def _get_file_extension(filename: str) -> str:
    """Extract and normalize file extension (e.g. '.pdf')."""
    _, ext = os.path.splitext(filename)
    return ext.lower()


def parse_resume(filename: str) -> ResumeParseResponse:
    """Mock resume parsing: returns an error response for unsupported types,
    a 5% random error, or the success mock data."""
    ext = _get_file_extension(filename)
    if ext not in SUPPORTED_TYPES:
        return ResumeParseResponse(
            status="error",
            message=f"不支持的文件类型：{ext}，请上传 PDF 或 DOCX 格式的简历",
        )

    data = _load_parse_data()

    if random.random() < 0.05:
        entry = data["error"]
        return ResumeParseResponse(
            status=entry["status"],
            message=entry["message"],
        )

    entry = data["success"]
    return ResumeParseResponse(
        status=entry["status"],
        data=entry["data"],
    )


def analyze_resume(target_position: str) -> ResumeScoreResponse:
    """Mock resume scoring: 5% random error, otherwise returns success mock data."""
    data = _load_score_data()

    if random.random() < 0.05:
        entry = data["error"]
        return ResumeScoreResponse(
            status=entry["status"],
            message=entry["message"],
        )

    entry = data["success"]
    return ResumeScoreResponse(
        status=entry["status"],
        data=entry["data"],
    )
