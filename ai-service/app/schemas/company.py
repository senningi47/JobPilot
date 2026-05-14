from pydantic import BaseModel


class CompanyBasicInfo(BaseModel):
    location: str
    founded: str
    scale: str
    funding_stage: str
    industry: str
    website: str
    apply_url: str


class SalaryComponents(BaseModel):
    base: str
    bonus: str
    equity: str


class CompanySalaryData(BaseModel):
    p25: int
    p50: int
    p75: int
    currency: str
    components: SalaryComponents
    benefits: list[str]


class ReviewDimension(BaseModel):
    name: str
    score: float
    summary: str


class CompanyReviewSummary(BaseModel):
    dimensions: list[ReviewDimension]
    overall_score: float


class CompanyTimelineEvent(BaseModel):
    date: str
    type: str
    title: str
    description: str


class CompanyIntel(BaseModel):
    name: str
    name_en: str
    basic_info: CompanyBasicInfo
    salary_data: CompanySalaryData
    review_summary: CompanyReviewSummary
    timeline: list[CompanyTimelineEvent]
    recent_news: list[str]
