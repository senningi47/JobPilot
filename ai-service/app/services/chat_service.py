import json
import os
import random

from app.schemas.chat import ChatData, ChatResponse

_MOCK_DATA: dict | None = None

INTENT_KEYWORDS: dict[str, list[str]] = {
    "job_search": ["找工作", "求职", "岗位", "职位", "招聘", "想做", "开发", "工程师", "后端", "前端", "算法", "运维", "测试", "产品", "设计"],
    "company_info": ["公司", "企业", "字节", "阿里", "腾讯", "美团", "百度", "京东", "小米", "华为", "拼多多", "快手", "怎么样", "好不好"],
    "resume_help": ["简历", "履历", "优化", "润色", "修改简历", "写简历", "STAR", "简历建议", "求职信"],
    "salary": ["薪资", "工资", "薪酬", "收入", "多少钱", "月薪", "年薪", "涨薪", "待遇", "福利", "期权", "股票"],
}


def load_mock_data() -> dict:
    """Load mock chat responses from JSON file."""
    global _MOCK_DATA
    if _MOCK_DATA is not None:
        return _MOCK_DATA

    data_path = os.path.join(
        os.path.dirname(__file__), "mock_data", "chat_responses.json"
    )
    with open(data_path, encoding="utf-8") as f:
        _MOCK_DATA = json.load(f)
    return _MOCK_DATA


def detect_intent(message: str) -> str:
    """Scan message for intent keywords and return detected intent."""
    for intent, keywords in INTENT_KEYWORDS.items():
        for keyword in keywords:
            if keyword in message:
                return intent
    return "general"


def get_chat_response(message: str) -> ChatResponse:
    """Detect intent, randomly select scenario (5% error, 10% empty, 85% normal),
    and return a ChatResponse."""
    data = load_mock_data()
    intent = detect_intent(message)

    rand = random.random()
    if rand < 0.05:
        scenario = "error"
    elif rand < 0.15:
        scenario = "empty"
    else:
        scenario = "normal"

    entry = data[intent][scenario]

    if scenario == "normal":
        payload = entry["data"]
        return ChatResponse(
            status=entry["status"],
            data=ChatData(
                content=payload["content"],
                intent=payload.get("intent", intent),
                sources=payload.get("sources", []),
            ),
        )

    return ChatResponse(
        status=entry["status"],
        message=entry["message"],
    )
