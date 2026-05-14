from fastapi import APIRouter, UploadFile, File
from pydantic import BaseModel

router = APIRouter()


class ResumeAnalysisResult(BaseModel):
    overall_match: float
    highlights: list[str]
    weaknesses: list[str]
    suggestions: list[str]


@router.post("/upload")
async def upload_resume(file: UploadFile = File(...)):
    """上传并解析简历文件"""
    # TODO: 实现 PDF/DOCX 解析 → LLM 结构化
    return {
        "filename": file.filename,
        "status": "uploaded",
        "message": "[TODO] 简历解析功能待实现",
    }


@router.post("/analyze", response_model=ResumeAnalysisResult)
async def analyze_resume(resume_id: str, target_position: str):
    """针对目标岗位分析简历匹配度"""
    # TODO: 接入强力模型进行简历 vs JD 逐条对比
    return ResumeAnalysisResult(
        overall_match=0.0,
        highlights=[],
        weaknesses=[],
        suggestions=["[TODO] 简历分析功能待实现"],
    )
