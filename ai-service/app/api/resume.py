from fastapi import APIRouter, UploadFile, File, Form

from app.schemas.resume import ResumeParseResponse, ResumeScoreResponse
from app.services.resume_service import parse_resume, analyze_resume

router = APIRouter()


@router.post("/upload", response_model=ResumeParseResponse)
async def upload_resume(file: UploadFile = File(...)):
    """上传并解析简历文件（PDF / DOCX）"""
    return parse_resume(file.filename)


@router.post("/analyze", response_model=ResumeScoreResponse)
async def post_analyze_resume(target_position: str = Form(...)):
    """针对目标岗位分析简历匹配度"""
    return analyze_resume(target_position)
