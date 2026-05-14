from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api import health, chat, resume

app = FastAPI(
    title="JobPilot AI Service",
    description="AI/LLM inference service for JobPilot",
    version="0.1.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:3000", "http://localhost:8080"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health.router, tags=["Health"])
app.include_router(chat.router, prefix="/chat", tags=["Chat"])
app.include_router(resume.router, prefix="/resume", tags=["Resume"])


@app.get("/")
def root():
    return {"service": "jobpilot-ai", "version": "0.1.0"}
