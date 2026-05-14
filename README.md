# JobPilot - AI 智能求职助手

> 面向在校大学生及应届毕业生的 AI 驱动智能求职助手，覆盖从岗位探索到拿到 Offer 的全链路。

## 项目简介

JobPilot 通过大语言模型（LLM）的自主推理与实时联网搜索能力，帮助用户高效求职。核心功能包括：

- **岗位探索引擎** — 专业标签体系 + LLM 模糊搜索
- **公司情报模块** — 联网实时抓取，结构化公司卡片
- **薪资福利分析** — 多源数据聚合，分位数分析
- **全网评价聚合** — 六维雷达图可视化
- **简历全套能力** — 解析、分析评分、帮写、美化、导出
- **技能差距分析** — JD 逐条对标，学习资源推荐
- **求职进度看板** — Kanban 式追踪 + 智能提醒
- **面试准备助手** — 题库生成 + Mock Interview
- **职业路径规划** — 可视化晋升路径图
- **市场趋势看板** — 需求热度、城市分布、薪资趋势
- **Job Alert 推送** — 个性化职位订阅
- **同龄人 Benchmark** — 匿名数据对比
- **公司 PK 对比** — 多维度横向比较

## 技术栈

| 层级 | 技术 |
|------|------|
| 前端 | Next.js 14, TypeScript, Tailwind CSS, ECharts |
| 后端 | Spring Boot 3.x (Java), Python FastAPI |
| 数据库 | MySQL 8.0+, Redis 7.0+, Qdrant |
| 消息队列 | RabbitMQ |
| AI/LLM | Anthropic Claude API, LangChain |
| 文件存储 | MinIO / 阿里云 OSS |
| 基础设施 | Docker, Nginx, GitHub Actions |
| 监控 | Prometheus + Grafana |

## 项目结构

```
JobPilot/
├── frontend/          # Next.js 14 前端应用
├── backend/           # Spring Boot 后端服务
├── ai-service/        # Python FastAPI AI 服务
├── infra/             # 基础设施配置 (Docker, Nginx, K8s)
├── scripts/           # 开发/部署脚本
├── docs/              # 项目文档
├── shared/            # 共享类型定义与协议
├── docker-compose.yml # 本地开发环境编排
└── .github/           # CI/CD 工作流
```

## 快速开始

### 环境要求

- Node.js >= 18
- Java >= 17
- Python >= 3.11
- Docker & Docker Compose

### 本地开发

```bash
# 克隆项目
git clone https://github.com/senningi47/JobPilot.git
cd JobPilot

# 启动基础设施 (MySQL, Redis, Qdrant, RabbitMQ)
docker-compose up -d

# 启动前端
cd frontend && npm install && npm run dev

# 启动后端
cd backend && ./gradlew bootRun

# 启动 AI 服务
cd ai-service && pip install -r requirements.txt && uvicorn app.main:app --reload
```

## 开发计划

- **MVP (6~8 周)**: 岗位探索 + 公司情报 + AI 对话 + 简历分析
- **第二版 (8~12 周后)**: 微信小程序 + 简历帮写 + 技能分析 + 进度看板
- **第三版 (持续迭代)**: Mock Interview + 职业规划 + 市场趋势 + Job Alert

## License

MIT
