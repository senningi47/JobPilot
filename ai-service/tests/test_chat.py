import pytest
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_chat_normal_job_search():
    """Sending a job search message returns 200 and a valid status."""
    resp = client.post(
        "/chat/send",
        json={
            "session_id": "test-001",
            "user_id": "u1",
            "message": "我想找后端开发的工作",
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] in ("success", "empty", "error")


def test_chat_normal_company():
    """Sending a company info message returns 200 and a valid status."""
    resp = client.post(
        "/chat/send",
        json={
            "session_id": "test-002",
            "user_id": "u2",
            "message": "告诉我字节跳动怎么样",
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] in ("success", "empty", "error")


def test_chat_general():
    """Sending a general greeting returns 200 and a valid status."""
    resp = client.post(
        "/chat/send",
        json={
            "session_id": "test-003",
            "user_id": "u3",
            "message": "你好",
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] in ("success", "empty", "error")
