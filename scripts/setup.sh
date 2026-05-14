#!/bin/bash
# JobPilot local development environment setup

set -e

echo "=== JobPilot 开发环境初始化 ==="

# Check prerequisites
command -v docker >/dev/null 2>&1 || { echo "请先安装 Docker"; exit 1; }
command -v node >/dev/null 2>&1 || { echo "请先安装 Node.js >= 18"; exit 1; }

# Copy env file
if [ ! -f .env ]; then
    cp .env.example .env
    echo "已创建 .env 文件，请根据实际情况修改配置"
fi

# Start infrastructure
echo "启动基础设施 (MySQL, Redis, Qdrant, RabbitMQ, MinIO)..."
docker-compose up -d

# Wait for MySQL
echo "等待 MySQL 就绪..."
sleep 10

# Frontend setup
echo "安装前端依赖..."
cd frontend && npm install && cd ..

# AI service setup
echo "安装 AI 服务依赖..."
cd ai-service && python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
deactivate && cd ..

echo ""
echo "=== 初始化完成 ==="
echo "启动命令："
echo "  前端:   cd frontend && npm run dev"
echo "  后端:   cd backend && ./gradlew bootRun"
echo "  AI服务: cd ai-service && uvicorn app.main:app --reload"
echo ""
echo "访问地址："
echo "  前端:   http://localhost:3000"
echo "  后端:   http://localhost:8080/api/v1/health"
echo "  AI服务: http://localhost:8000/health"
echo "  MinIO:  http://localhost:9001"
echo "  RabbitMQ: http://localhost:15672"
