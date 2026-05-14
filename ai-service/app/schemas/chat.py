from pydantic import BaseModel


class ChatRequest(BaseModel):
    session_id: str
    user_id: str
    message: str
    channel: str = "web"


class ChatData(BaseModel):
    content: str
    intent: str
    sources: list[dict] = []


class ChatResponse(BaseModel):
    status: str
    data: ChatData | None = None
    message: str | None = None
    model_used: str = "mock"
    latency_ms: int = 0
