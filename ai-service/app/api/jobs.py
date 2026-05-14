from fastapi import APIRouter, Query

from app.services.job_explore_service import (
    get_categories,
    get_major_detail,
    get_majors_by_category,
    search_jobs,
)

router = APIRouter()


@router.get("/categories")
async def list_categories():
    """Return all available job categories."""
    categories = get_categories()
    return {"status": "success", "data": categories}


@router.get("/categories/{category}/majors")
async def list_majors_by_category(category: str):
    """Return majors belonging to a given category."""
    majors = get_majors_by_category(category)
    return {"status": "success", "data": majors}


@router.get("/majors/{major}")
async def get_major(major: str):
    """Return full detail for a specific major."""
    detail = get_major_detail(major)
    if detail is None:
        return {"status": "success", "data": []}
    return {"status": "success", "data": detail}


@router.get("/search")
async def search(q: str = Query(default="", max_length=200)):
    """Search jobs by keyword across all majors."""
    results = search_jobs(q)
    return {"status": "success", "data": results}
