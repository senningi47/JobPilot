from fastapi import APIRouter

from app.schemas.chat import ChatRequest, ChatResponse
from app.services.chat_service import get_chat_response

router = APIRouter()


@router.post("/send", response_model=ChatResponse)
async def send_message(req: ChatRequest) -> ChatResponse:
    """接收用户消息，路由到对应意图处理"""
    return get_chat_response(req.message)
