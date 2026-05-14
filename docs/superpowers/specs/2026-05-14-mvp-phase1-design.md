# JobPilot MVP 第一版 — 设计规格文档

> **日期**：2026-05-14
> **状态**：已批准
> **范围**：P0 功能 — 用户认证、AI 对话、岗位探索、公司情报、简历分析
> **策略**：垂直切片、LLM Mock 优先、shadcn/ui + Tailwind

---

## 1. 架构总览

### 1.1 三服务通信架构

```
浏览器
  │
  ▼
Nginx (:80)
  ├── /           → 前端 (Next.js :3000)
  ├── /api/v1/*   → 后端 (Spring Boot :8080)
  └── /api/ai/*   → AI 服务 (FastAPI :8000)
```

**MVP 阶段仅使用同步 HTTP 调用。** RabbitMQ 暂不引入 — 后端通过 HTTP 直接调用 AI 服务，简化开发阶段的调试流程。异步消息队列将在第二版中引入，用于 Job Alert 和后台任务等场景。

### 1.2 分阶段执行顺序

| 阶段 | 功能 | 关键交付物 |
|------|------|-----------|
| A | 用户认证 | 注册、登录、JWT、统一响应格式 |
| B | AI 对话 | 对话界面、Redis + MySQL 双写历史、Mock LLM |
| C | 岗位探索 | 标签浏览、模糊搜索、静态 JSON 种子数据 |
| D | 公司情报 | 公司卡片、情报缓存、Mock 联网搜索 |
| E | 简历系统 | 上传解析、分析评分、本地文件存储 |

每个阶段是一个完整的垂直切片：数据库 Schema → 后端 API → AI 服务（如适用）→ 前端页面 → 测试。一个阶段"完成"的标准是可以在浏览器中端到端演示。

---

## 2. 全局 API 规范（阶段 A）

### 2.1 统一响应信封

所有后端 API 响应使用以下统一格式：

```json
{
  "code": 0,
  "msg": "success",
  "data": { ... },
  "trace_id": "a1b2c3d4"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | 0 = 成功，非零 = 错误码 |
| `msg` | string | 人类可读的消息 |
| `data` | any | 响应数据（错误时为 null） |
| `trace_id` | string | 基于 UUID 的请求追踪 ID，用于调试 |

### 2.2 错误码范围

| 范围 | 分类 | 示例 |
|------|------|------|
| 0 | 成功 | — |
| 1000-1999 | 认证错误 | 1001 未授权、1002 令牌过期、1003 用户已存在 |
| 2000-2999 | 业务错误 | 2001 公司未找到、2002 简历解析失败 |
| 5000-5999 | 系统错误 | 5001 内部错误、5002 AI 服务不可用 |

### 2.3 错误响应示例

```json
{
  "code": 1001,
  "msg": "令牌已过期，请重新登录",
  "data": null,
  "trace_id": "a1b2c3d4"
}
```

### 2.4 分页信封

列表类接口使用以下分页格式：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "list": [ ... ],
    "total": 100,
    "page": 1,
    "page_size": 20
  },
  "trace_id": "..."
}
```

### 2.5 AI 服务响应格式

AI 服务返回简化信封（后端在转发给前端前会进行包装）：

```json
{
  "status": "success",
  "data": { ... },
  "model_used": "claude-haiku-4-5",
  "latency_ms": 320
}
```

错误变体：
```json
{
  "status": "empty",
  "data": null,
  "message": "未找到相关结果，请尝试换个关键词"
}
```
```json
{
  "status": "error",
  "data": null,
  "message": "AI 服务暂时不可用，请稍后重试"
}
```

---

## 3. 阶段 A — 用户认证

### 3.1 功能范围

- 用户注册（用户名 + 邮箱 + 密码）
- 用户登录 → JWT 令牌
- JWT 过滤器拦截所有受保护的端点
- 前端：登录/注册页面，带表单验证

### 3.2 后端设计

**新增文件：**

```
backend/src/main/java/com/jobpilot/
├── entity/UserEntity.java
├── repository/UserRepository.java
├── service/AuthService.java
├── service/impl/AuthServiceImpl.java
├── controller/AuthController.java
├── dto/RegisterRequest.java
├── dto/LoginRequest.java
├── dto/AuthResponse.java
├── dto/ApiResponse.java          ← 统一响应信封
├── filter/JwtAuthFilter.java
├── util/JwtUtil.java
└── exception/BusinessException.java
```

**UserEntity** 映射已有的 `users` 表：

```java
@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;       // 唯一
    private String email;          // 唯一
    private String passwordHash;   // BCrypt 加密
    private String avatarUrl;
    private String major;
    private Integer graduationYear;
    @Column(columnDefinition = "JSON")
    private String preferences;    // JSON 字符串
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**API 端点：**

| 方法 | 路径 | 需认证 | 说明 |
|------|------|--------|------|
| POST | `/auth/register` | 否 | 注册新用户 |
| POST | `/auth/login` | 否 | 登录，返回 JWT |
| GET | `/auth/me` | 是 | 获取当前用户信息 |

**RegisterRequest：** `{ username, email, password, major?, graduationYear? }`
**LoginRequest：** `{ email, password }`
**AuthResponse：** `{ token, user: { id, username, email, major } }`

**JWT 配置：**
- 算法：HS256
- 过期时间：24 小时（通过 `app.jwt.expiration` 可配置）
- 声明：`userId`、`username`、`email`
- 请求头：`Authorization: Bearer <token>`

**JwtAuthFilter** 拦截除 `/auth/**` 和 `/health` 以外的所有请求，验证令牌并设置 SecurityContext。

### 3.3 前端设计

**新增文件：**

```
frontend/src/
├── app/login/page.tsx          ← 登录表单
├── app/register/page.tsx       ← 注册表单
├── components/auth/
│   ├── LoginForm.tsx
│   └── RegisterForm.tsx
└── lib/store/authStore.ts      ← zustand 认证状态管理
```

**认证流程：**
1. 用户提交登录表单 → POST `/api/v1/auth/login`
2. 响应包含 JWT 令牌 → 存储到 localStorage
3. `authStore`（zustand）持有用户状态，与 localStorage 同步
4. API 客户端拦截器已实现从 localStorage 读取令牌（见 `src/lib/api.ts`）
5. 401 响应自动重定向到 `/login`（拦截器中已实现）

**使用到的 shadcn/ui 组件：** `Button`、`Input`、`Label`、`Card`、`Form`

### 3.4 数据库变更

无 — `users` 表已在 `init.sql` 中定义，无需修改 Schema。

---

## 4. 阶段 B — AI 对话界面

### 4.1 功能范围

- 对话界面，带消息气泡（用户 + AI）
- 打字机效果展示（即使是 Mock 也模拟流式输出）
- 对话历史：Redis（活跃 20 条）+ MySQL（全量持久化）
- 会话管理（创建、列表、恢复会话）
- Mock AI 响应覆盖：正常返回、空结果、错误三种场景

### 4.2 对话历史 — 双写策略

**Redis（热缓存）：**
- 键模式：`chat:session:{session_id}:messages`
- 数据结构：Redis List（LPUSH 新消息，LTRIM 保留 20 条）
- 每条记录：`{ role, content, timestamp }`
- TTL：7 天，每条新消息刷新 TTL
- 用途：活跃对话上下文的快速读取

**MySQL（持久化）：**
- 表：`chat_messages`（新增表，见下方 Schema）
- 完整消息历史，无 TTL
- 用途：搜索、恢复会话、数据分析

**写入路径：**
```
用户发送消息
  → 后端写入 MySQL（chat_messages）
  → 后端 LPUSH 到 Redis，LTRIM 0..19
  → 后端调用 AI 服务
  → AI 返回响应
  → 后端将 AI 响应写入 MySQL
  → 后端 LPUSH AI 响应到 Redis
  → 返回给前端
```

**读取路径（恢复会话）：**
```
用户打开已有会话
  → 后端检查 Redis 是否有缓存消息
  → 若 Redis 有数据：返回 Redis 列表（快速路径）
  → 若 Redis 无数据：从 MySQL 加载最近 20 条，预热 Redis 缓存，返回
```

### 4.3 新增数据库表

```sql
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('user', 'assistant', 'system') NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(50) COMMENT 'LLM 识别的意图: job_search, company_info, resume_help, general',
    model_used VARCHAR(50) COMMENT '使用的模型档位',
    metadata JSON COMMENT '额外数据: 来源、置信度等',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_session_time (session_id, created_at),
    INDEX idx_user_sessions (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

已有的 `chat_sessions` 表存储会话元数据（标题、消息数、渠道）。消息内容存储在 `chat_messages` 表中。

### 4.4 后端设计

**新增文件：**

```
backend/src/main/java/com/jobpilot/
├── entity/ChatSessionEntity.java
├── entity/ChatMessageEntity.java
├── repository/ChatSessionRepository.java
├── repository/ChatMessageRepository.java
├── service/ChatService.java
├── service/impl/ChatServiceImpl.java
├── controller/ChatController.java
├── dto/ChatSendRequest.java
├── dto/ChatMessageDto.java
└── dto/ChatSessionDto.java
```

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/chat/sessions` | 创建新会话，返回 session_id |
| GET | `/chat/sessions` | 获取用户会话列表（分页） |
| GET | `/chat/sessions/{id}/messages` | 获取指定会话的消息 |
| POST | `/chat/sessions/{id}/messages` | 发送消息，获取 AI 响应 |

**ChatServiceImpl 核心逻辑：**
```java
public ChatMessageDto sendMessage(Long userId, String sessionId, String content) {
    // 1. 将用户消息写入 MySQL
    // 2. LPUSH 到 Redis，LTRIM 0..19（保留 20 条）
    // 3. 从 Redis 加载最近上下文（最近 10 条消息作为 LLM 上下文）
    // 4. 调用 AI 服务：POST http://ai-service:8000/chat/send
    // 5. 将 AI 响应写入 MySQL
    // 6. LPUSH AI 响应到 Redis
    // 7. 返回 AI 消息 DTO
}
```

### 4.5 AI 服务设计（Mock）

**新增文件：**

```
ai-service/app/
├── services/mock_data/chat_responses.json
├── services/chat_service.py
├── schemas/chat.py
└── api/chat.py（更新）
```

**Mock 响应路由** — Mock 服务根据关键词匹配选择响应：

| 输入关键词 | 识别意图 | Mock 响应内容 |
|-----------|---------|--------------|
| 包含"岗位"、"工作"、"实习" | job_search | 返回 3 条示例岗位推荐 |
| 包含"公司"、"字节"、"腾讯" | company_info | 返回一个示例公司卡片 |
| 包含"简历"、"简历分析" | resume_help | 返回简历优化建议 |
| 包含"薪资"、"工资"、"钱" | salary | 返回薪资区间数据 |
| 其他 | general | 返回通用帮助信息 |

**三种 Mock 场景**（每个意图都包含全部三种）：

```json
{
  "normal": { "status": "success", "data": { "content": "...", "intent": "job_search", "sources": [...] } },
  "empty":  { "status": "empty",  "data": null, "message": "未找到相关结果，请尝试换个关键词" },
  "error":  { "status": "error",  "data": null, "message": "AI 服务暂时不可用，请稍后重试" }
}
```

错误和空结果场景分别以 5% 和 10% 的概率随机触发，可通过环境变量 `MOCK_ERROR_RATE` 配置。

### 4.6 前端设计

**新增文件：**

```
frontend/src/
├── app/chat/page.tsx                 ← 主对话页面
├── app/chat/layout.tsx               ← 带侧边栏的对话布局
├── components/chat/
│   ├── ChatSidebar.tsx               ← 会话列表
│   ├── ChatMessageList.tsx           ← 消息气泡区域
│   ├── ChatInput.tsx                 ← 输入框 + 发送按钮
│   ├── ChatMessage.tsx               ← 单条消息气泡
│   └── TypewriterText.tsx            ← 打字机动画
└── lib/store/chatStore.ts            ← zustand 对话状态管理
```

**对话界面布局：**

```
┌─────────┬──────────────────────────────────┐
│ 会话列表 │                                  │
│         │     消息区域                      │
│         │     ┌──────────────────┐         │
│ • 新对话 │     │ AI: 你好！       │         │
│ • 昨天  │     │ User: 找后端实习  │         │
│ • 上周  │     │ AI: 推荐以下岗位..│         │
│         │     └──────────────────┘         │
│         │                                  │
│         │  ┌──────────────────────┬──────┐ │
│         │  │ 输入消息...          │ 发送 │ │
│         │  └──────────────────────┴──────┘ │
└─────────┴──────────────────────────────────┘
```

**使用到的 shadcn/ui 组件：** `ScrollArea`、`Avatar`、`Button`、`Input`、`Separator`、`Skeleton`

**打字机效果：** AI 消息通过 `TypewriterText` 组件逐字符动画展示。Mock 模式下动画运行在预获取的响应上。后续接入真实 LLM 流式输出时，该组件将改为消费 SSE 数据块。

---

## 5. 阶段 C — 岗位探索引擎

### 5.1 功能范围

- 静态 JSON 种子数据，覆盖 5 大类别 20+ 个主流专业
- 专业 → 岗位标签映射浏览
- 模糊关键词搜索（Mock LLM 返回精选结果）
- 岗位详情卡片，带标签展示

### 5.2 种子数据设计

**文件：** `ai-service/app/services/mock_data/job_knowledge_graph.json`

**覆盖范围（20+ 专业）：**

| 类别 | 专业 |
|------|------|
| 工学 | 计算机科学与技术、软件工程、人工智能、电子信息工程、通信工程、自动化 |
| 理学 | 数学与应用数学、统计学、物理学 |
| 经济学 | 经济学、金融学、国际经济与贸易 |
| 管理学 | 工商管理、市场营销、会计学、人力资源管理、信息管理与信息系统 |
| 文学 | 英语、新闻学、广告学 |

每个专业条目遵循规划文档附录中的 Schema：

```json
{
  "major": "计算机科学与技术",
  "category": "工学",
  "primary_jobs": [
    {
      "job_title": "后端开发工程师",
      "tags": ["Java", "Python", "Spring Boot", "MySQL", "分布式"],
      "typical_companies": ["字节跳动", "腾讯", "阿里巴巴", "美团", "百度"],
      "salary_range_p50": "25k-40k",
      "difficulty": "中",
      "description": "负责服务端业务逻辑开发、接口设计与系统优化"
    }
  ],
  "extended_jobs": [
    {
      "job_title": "产品经理",
      "tags": ["需求分析", "原型设计", "数据分析", "技术理解"],
      "description": "利用技术背景理解产品与用户需求"
    }
  ]
}
```

### 5.3 后端设计

**新增文件：**

```
backend/src/main/java/com/jobpilot/
├── controller/JobExploreController.java
├── service/JobExploreService.java
├── service/impl/JobExploreServiceImpl.java
├── dto/JobTagDto.java
├── dto/MajorJobDto.java
└── dto/JobSearchRequest.java
```

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/jobs/categories` | 获取所有类别列表（工学、理学、…） |
| GET | `/jobs/categories/{category}/majors` | 获取指定类别下的专业列表 |
| GET | `/jobs/majors/{major}` | 获取指定专业的完整岗位映射 |
| GET | `/jobs/search?q=xxx` | 模糊搜索 → Mock LLM 返回排序结果 |

**后端作为代理** — 将类别/专业查询转发给 AI 服务，由 AI 服务从静态 JSON 种子数据中返回。这样 AI 服务成为知识图谱数据的唯一数据源，后续替换为真实 RAG 时无需修改后端。

### 5.4 AI 服务设计

**新增文件：**

```
ai-service/app/
├── services/job_explore_service.py
├── services/mock_data/job_knowledge_graph.json
└── schemas/job.py
```

**端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/jobs/categories` | 从 JSON 返回类别列表 |
| GET | `/jobs/majors/{major}` | 从 JSON 返回专业详情 |
| GET | `/jobs/search?q=xxx` | Mock 模糊搜索：关键词匹配 + 随机排序 |

**模糊搜索 Mock 逻辑：**
1. 接收查询字符串
2. 在所有专业的 `job_title`、`tags`、`description` 字段中搜索
3. 返回前 5 条匹配结果，附带 Mock 置信度分数
4. 空查询 → 返回空结果（测试空结果场景）

### 5.5 前端设计

**新增文件：**

```
frontend/src/
├── app/explore/page.tsx              ← 探索主页
├── app/explore/[category]/page.tsx   ← 类别详情页
├── app/explore/major/[name]/page.tsx ← 专业 → 岗位卡片
├── components/explore/
│   ├── CategoryGrid.tsx              ← 类别卡片网格
│   ├── MajorCard.tsx                 ← 专业卡片（含主推岗位）
│   ├── JobTagCard.tsx                ← 单个岗位标签卡片
│   ├── JobDetailPanel.tsx            ← 岗位详情展开面板
│   └── SearchBar.tsx                 ← 模糊搜索输入框
```

**页面流转：**

```
/explore
  → CategoryGrid：展示 5 个类别卡片（工学/理学/经济/管理/文学）
  → 点击类别 → /explore/{category}
    → MajorCard 列表：展示该类别下的专业
    → 点击专业 → /explore/major/{name}
      → JobTagCard 网格：主推岗位 + 延伸岗位
      → 点击岗位 → JobDetailPanel 滑入（标签、公司、薪资、描述）
```

**搜索栏** 固定在 `/explore` 页面顶部。输入时触发防抖搜索（300ms），结果以下拉覆盖层形式展示。

**使用到的 shadcn/ui 组件：** `Card`、`Badge`（标签）、`Dialog`（岗位详情）、`Command`（搜索）、`Tabs`（主推/延伸岗位）、`Skeleton`

---

## 6. 阶段 D — 公司情报模块

### 6.1 功能范围

- 公司搜索，带自动补全
- 公司情报卡片（基础信息、JD、发展时间线、薪资、评价）
- 缓存策略：Redis（热数据）+ MySQL（温数据）+ AI 服务（冷数据/Mock）
- Mock 联网搜索返回精选公司数据

### 6.2 公司情报缓存策略

```
请求："字节跳动"
  → 后端检查 Redis：键 `company:intel:{company_name}`
    → 命中：返回缓存数据（TTL 24 小时）
    → 未命中：检查 MySQL company_cache 表
      → 命中且未过期：返回数据，预热 Redis
      → 未命中或已过期：调用 AI 服务
        → AI 服务返回 Mock 公司情报
        → 后端写入 MySQL + Redis
        → 返回给前端
```

### 6.3 后端设计

**新增文件：**

```
backend/src/main/java/com/jobpilot/
├── entity/CompanyCacheEntity.java
├── repository/CompanyCacheRepository.java
├── controller/CompanyController.java
├── service/CompanyService.java
├── service/impl/CompanyServiceImpl.java
└── dto/CompanyIntelDto.java
```

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/companies/search?q=xxx` | 公司名称自动补全 |
| GET | `/companies/{name}` | 获取完整公司情报 |
| POST | `/companies/{name}/refresh` | 强制刷新缓存 |

**CompanyCacheEntity** 映射已有的 `company_cache` 表：
```java
@Entity
@Table(name = "company_cache")
public class CompanyCacheEntity {
    @Id @GeneratedValue
    private Long id;
    private String companyName;
    private String companyNameEn;
    @Column(columnDefinition = "JSON")
    private String basicInfo;
    @Column(columnDefinition = "JSON")
    private String salaryData;
    @Column(columnDefinition = "JSON")
    private String reviewSummary;
    @Column(columnDefinition = "JSON")
    private String timeline;
    private String dataSource;
    private LocalDateTime lastUpdated;
    private LocalDateTime expiresAt;
}
```

### 6.4 AI 服务设计（Mock）

**新增文件：**

```
ai-service/app/
├── services/company_service.py
├── services/mock_data/company_data.json
└── schemas/company.py
```

**Mock 数据** 覆盖 10 家知名科技公司：

| 公司 | 包含数据 |
|------|---------|
| 字节跳动、腾讯、阿里巴巴、美团、百度 | 基础信息、薪资、评价、时间线 |
| 华为、小米、京东、网易、快手 | 基础信息、薪资、评价、时间线 |

每个公司条目遵循 `frontend/src/types/index.ts` 中定义的 `CompanyIntel` 接口。

**三种 Mock 场景：**
- 正常：返回完整公司情报
- 空结果：公司名不在 Mock 数据集中 → "未找到该公司信息"
- 错误：随机 5% 概率模拟超时

### 6.5 前端设计

**新增文件：**

```
frontend/src/
├── app/company/page.tsx              ← 搜索 + 结果页
├── app/company/[name]/page.tsx       ← 公司详情页
├── components/company/
│   ├── CompanySearch.tsx             ← 带自动补全的搜索框
│   ├── CompanyCard.tsx               ← 搜索结果中的摘要卡片
│   ├── CompanyDetailCard.tsx         ← 完整情报卡片
│   ├── CompanyTimeline.tsx           ← 时间线可视化
│   ├── SalaryChart.tsx               ← ECharts 柱状图
│   └── ReviewRadar.tsx               ← ECharts 雷达图
```

**公司详情页布局：**

```
┌────────────────────────────────────────────┐
│  公司名称 | 行业 | 规模 | 融资阶段          │
├────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 基础信息  │  │ 薪资分布  │  │ 评价雷达  │ │
│  │ 卡片     │  │ ECharts  │  │ ECharts  │ │
│  └──────────┘  └──────────┘  └──────────┘ │
│                                            │
│  发展时间线 ────────────────────────────    │
│  ● 2012 成立 → ● 2018 E轮 → ● 2021 上市  │
│                                            │
│  近期新闻                                  │
│  • 字节跳动发布新产品...                    │
│  • 字节跳动Q3财报...                       │
└────────────────────────────────────────────┘
```

**使用到的 shadcn/ui 组件：** `Command`（搜索）、`Card`、`Badge`、`Tabs`（基础信息/薪资/评价）
**ECharts 图表：** `SalaryChart`（柱状图）、`ReviewRadar`（雷达图）— 使用 `echarts-for-react`

---

## 7. 阶段 E — 简历上传与分析

### 7.1 功能范围

- 简历上传（PDF、DOCX），本地文件存储
- LLM 解析 → 结构化简历 JSON（Mock）
- 简历 vs 岗位 JD 匹配分析评分（Mock）
- 简历列表，带版本历史

### 7.2 文件存储设计

**MVP 阶段：本地 Volume，按用户分目录：**

```
/uploads/{user_id}/{原始文件名}
```

**存储接口抽象**（为后续 OSS 切换预留）：

```python
# ai-service/app/services/storage_service.py
class StorageService(ABC):
    @abstractmethod
    async def save(self, user_id: str, filename: str, content: bytes) -> str:
        """保存文件，返回访问路径"""
        pass

    @abstractmethod
    async def read(self, path: str) -> bytes:
        """读取文件内容"""
        pass

    @abstractmethod
    async def delete(self, path: str) -> bool:
        """删除文件"""
        pass

class LocalStorageService(StorageService):
    BASE_DIR = "/uploads"
    # 实现：写入 /uploads/{user_id}/{filename}

class OSSStorageService(StorageService):
    # 第二版预留：委托给 MinIO/阿里云 OSS
    pass
```

后端同时将文件路径存储在 `resumes.raw_file_url` 字段中，因此切换存储方式只需更新 `StorageService` 实现，无需数据库迁移。

### 7.3 后端设计

**新增文件：**

```
backend/src/main/java/com/jobpilot/
├── entity/ResumeEntity.java
├── repository/ResumeRepository.java
├── controller/ResumeController.java
├── service/ResumeService.java
├── service/impl/ResumeServiceImpl.java
├── dto/ResumeDto.java
├── dto/ResumeAnalysisDto.java
└── dto/ResumeScoreDto.java
```

**API 端点：**

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/resumes/upload` | 上传简历文件（multipart） |
| GET | `/resumes` | 获取用户简历列表 |
| GET | `/resumes/{id}` | 获取解析后的简历数据 |
| POST | `/resumes/{id}/analyze` | 针对目标岗位分析简历 |
| GET | `/resumes/{id}/score` | 获取最新分析结果 |

**上传流程：**
```
POST /resumes/upload（multipart: 文件 + 可选的目标岗位）
  → 验证文件类型（仅 PDF/DOCX，最大 10MB）
  → 将文件转发给 AI 服务进行解析
  → AI 返回结构化简历 JSON
  → 将文件保存到本地 volume：/uploads/{user_id}/{文件名}
  → 将结构化数据 + 文件路径写入 MySQL resumes 表
  → 返回 ResumeDto
```

### 7.4 AI 服务设计（Mock）

**新增文件：**

```
ai-service/app/
├── services/resume_service.py
├── services/storage_service.py
├── services/mock_data/resume_responses.json
├── services/mock_data/resume_scores.json
└── schemas/resume.py
```

**Mock 解析响应** — 对任何上传的文件返回完整的结构化简历：

```json
{
  "status": "success",
  "data": {
    "basic_info": { "name": "张三", "email": "zhangsan@example.com", ... },
    "education": [{ "school": "北京大学", "major": "计算机科学", ... }],
    "projects": [{ "name": "电商平台开发", "tech_stack": ["Java", "Spring Boot"], ... }],
    "internships": [{ "company": "字节跳动", "position": "后端开发实习生", ... }],
    "skills": ["Java", "Python", "MySQL", "Redis", "Git"],
    "honors": ["ACM 区域赛银牌"]
  }
}
```

**Mock 分析响应** — 针对任意目标岗位返回评分：

```json
{
  "status": "success",
  "data": {
    "overall_match": 78,
    "dimensions": [
      { "name": "技术技能匹配", "score": 85, "feedback": "掌握 JD 要求的核心技术栈" },
      { "name": "项目经验", "score": 72, "feedback": "项目经历相关但缺少量化成果" },
      { "name": "教育背景", "score": 90, "feedback": "985 计算机专业，背景优秀" },
      { "name": "实习经历", "score": 65, "feedback": "实习经历偏少，建议补充" }
    ],
    "highlights": ["扎实的 Java 基础", "有分布式系统项目经验"],
    "weaknesses": ["缺少大型项目主导经验", "实习经历不足"],
    "suggestions": ["补充 1-2 段大厂实习", "项目描述增加量化数据"]
  }
}
```

**三种 Mock 场景：**
- 正常：返回完整结构化简历 / 评分
- 空结果：不支持的文件类型 → "不支持的文件格式，请上传 PDF 或 DOCX"
- 错误：随机 5% 概率 → "简历解析服务暂时不可用"

### 7.5 前端设计

**新增文件：**

```
frontend/src/
├── app/resume/page.tsx               ← 简历列表页
├── app/resume/upload/page.tsx        ← 上传页面
├── app/resume/[id]/page.tsx          ← 简历详情 + 分析页
├── components/resume/
│   ├── ResumeUpload.tsx              ← 拖拽上传区域
│   ├── ResumePreview.tsx             ← 结构化简历预览
│   ├── ScoreRadar.tsx                ← ECharts 评分雷达图
│   ├── ScoreDimensionCard.tsx        ← 单维度评分卡片
│   └── SuggestionList.tsx            ← 改进建议列表
```

**上传页面：** 使用 shadcn/ui `Card` + 原生 HTML 拖拽事件实现拖拽上传区域。上传前在客户端进行文件类型验证。

**分析页面布局：**

```
┌────────────────────────────────────────────┐
│  简历详情            [重新上传] [分析匹配度] │
├────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────────────┐   │
│  │ 结构化简历  │  │ 匹配度雷达图       │   │
│  │ 预览       │  │ 整体: 78%          │   │
│  │            │  │ (ECharts 雷达图)   │   │
│  └────────────┘  └────────────────────┘   │
│                                            │
│  维度评分                                   │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐     │
│  │技能85│ │项目72│ │教育90│ │实习65│     │
│  └──────┘ └──────┘ └──────┘ └──────┘     │
│                                            │
│  亮点                    待改进             │
│  ✅ 扎实Java基础         ⚠ 实习经历不足    │
│  ✅ 分布式项目经验       ⚠ 缺少量化成果    │
│                                            │
│  改进建议                                   │
│  1. 补充1-2段大厂实习                       │
│  2. 项目描述增加量化数据                     │
└────────────────────────────────────────────┘
```

**使用到的 shadcn/ui 组件：** `Card`、`Progress`（维度评分）、`Badge`、`Button`、`Alert`（建议）

---

## 8. 测试策略

### 8.1 后端测试

每个阶段包含：
- **单元测试**：Service 层（Mock 仓储层）
- **集成测试**：Controller 层（MockMvc，阶段 B 使用内嵌 Redis）

### 8.2 AI 服务测试

- **单元测试**：Mock 服务（验证三种场景：正常、空结果、错误）
- **API 测试**：使用 pytest + httpx `AsyncClient`

### 8.3 前端测试

- **组件测试**：关键 UI 组件的测试（不在 MVP 范围内，记录在第二版计划中）

### 8.4 Mock 数据校验

所有 Mock JSON 文件在构建时通过 TypeScript 接口进行校验。AI 服务的 Mock 响应必须匹配 `ai-service/app/schemas/` 中定义的 Schema。

---

## 9. MVP 不包含的内容

以下功能明确推迟到后续版本：
- RabbitMQ 异步消息
- 真实 LLM API 集成（第二版）
- 微信/飞书渠道接入
- 简历美化 / 导出（第二版）
- 求职进度看板（第二版）
- 前端组件单元测试（第二版）
- 端到端测试（第二版）
- RAG / 向量检索（第二版）
- 动态联网爬取公司数据
