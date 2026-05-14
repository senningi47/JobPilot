import random

from fastapi import APIRouter, Query

from app.services.company_service import get_company, search_companies

router = APIRouter()


@router.get("/search")
async def search(q: str = Query(default="", max_length=200)):
    """Search companies by keyword (name or English name)."""
    results = search_companies(q)
    if not results:
        return {"status": "success", "data": [], "message": "No companies found"}
    return {"status": "success", "data": results}


@router.get("/{name}")
async def get_company_detail(name: str):
    """Get full company intelligence by name. 5% random error chance."""
    if random.random() < 0.05:
        return {
            "status": "error",
            "data": None,
            "message": "服务暂时不可用，请稍后重试",
        }

    company = get_company(name)
    if company is None:
        return {
            "status": "success",
            "data": None,
            "message": "未找到该公司信息",
        }
    return {"status": "success", "data": company}
