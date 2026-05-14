from pydantic import BaseModel


class BasicInfo(BaseModel):
    name: str
    phone: str
    email: str
    education: str
    target_position: str


class Education(BaseModel):
    school: str
    major: str
    degree: str
    start_date: str
    end_date: str
    gpa: str


class Project(BaseModel):
    name: str
    role: str
    tech_stack: list[str]
    description: str
    achievements: list[str]


class Internship(BaseModel):
    company: str
    position: str
    start_date: str
    end_date: str
    description: str
    achievements: list[str]


class ResumeParseData(BaseModel):
    basic_info: BasicInfo
    education: list[Education]
    projects: list[Project]
    internships: list[Internship]
    skills: list[str]
    honors: list[str]


class ScoreDimension(BaseModel):
    name: str
    score: int
    feedback: str


class ResumeScoreData(BaseModel):
    overall_match: int
    dimensions: list[ScoreDimension]
    highlights: list[str]
    weaknesses: list[str]
    suggestions: list[str]


class ResumeParseResponse(BaseModel):
    status: str
    data: ResumeParseData | None = None
    message: str | None = None


class ResumeScoreResponse(BaseModel):
    status: str
    data: ResumeScoreData | None = None
    message: str | None = None
