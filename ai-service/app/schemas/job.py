from pydantic import BaseModel


class JobTag(BaseModel):
    job_title: str
    tags: list[str]
    typical_companies: list[str] = []
    salary_range_p50: str = ""
    difficulty: str = ""
    description: str = ""


class MajorJobMapping(BaseModel):
    major: str
    category: str
    primary_jobs: list[JobTag]
    extended_jobs: list[JobTag]


class JobSearchResult(BaseModel):
    job_title: str
    major: str
    category: str
    tags: list[str]
    description: str
    confidence: float
