from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter()


class ChatRequest(BaseModel):
    session_id: str
    user_id: str
    message: str
    channel: str = "web"


class ChatResponse(BaseModel):
    session_id: str
    reply: str
    intent: str | None = None


@router.post("/send", response_model=ChatResponse)
async def send_message(req: ChatRequest):
    """接收用户消息，路由到对应 Agent 处理"""
    # TODO: 接入 LLM cc-switch 调度 + 意图路由
    return ChatResponse(
        session_id=req.session_id,
        reply=f"[TODO] 收到消息: {req.message}",
        intent=None,
    )
