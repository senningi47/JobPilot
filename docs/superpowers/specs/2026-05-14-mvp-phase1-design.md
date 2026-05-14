# JobPilot MVP Phase 1 — Design Spec

> **Date**: 2026-05-14
> **Status**: Approved
> **Scope**: P0 features — Auth, AI Chat, Job Exploration, Company Intelligence, Resume Analysis
> **Strategy**: Vertical slices, Mock-first LLM, shadcn/ui + Tailwind

---

## 1. Architecture Overview

### 1.1 Three-Service Communication

```
Browser
  │
  ▼
Nginx (:80)
  ├── /           → Frontend (Next.js :3000)
  ├── /api/v1/*   → Backend (Spring Boot :8080)
  └── /api/ai/*   → AI Service (FastAPI :8000)
```

**MVP: synchronous HTTP only.** RabbitMQ is deferred — backend calls AI service via HTTP. This simplifies debugging during development. Async message queue is introduced in Phase 2 when Job Alert and background tasks require it.

### 1.2 Phase Execution Order

| Phase | Feature | Key Deliverable |
|-------|---------|-----------------|
| A | User Auth | Register, Login, JWT, unified response format |
| B | AI Chat | Chat UI, Redis + MySQL dual-write history, mock LLM |
| C | Job Explore | Tag browsing, fuzzy search, static JSON seed data |
| D | Company Intel | Company card, cached intelligence, mock web search |
| E | Resume | Upload/parse, analysis scoring, local file storage |

Each phase is a complete vertical slice: DB schema → backend API → AI service (if applicable) → frontend page → tests. A phase is "done" when it can be demonstrated end-to-end in the browser.

---

## 2. Global API Standards (Phase A)

### 2.1 Unified Response Envelope

All backend API responses use this envelope:

```json
{
  "code": 0,
  "msg": "success",
  "data": { ... },
  "trace_id": "a1b2c3d4"
}
```

| Field | Type | Description |
|-------|------|-------------|
| `code` | int | 0 = success, non-zero = error code |
| `msg` | string | Human-readable message |
| `data` | any | Response payload (null on error) |
| `trace_id` | string | UUID-based request trace ID for debugging |

### 2.2 Error Code Ranges

| Range | Category | Examples |
|-------|----------|----------|
| 0 | Success | — |
| 1000-1999 | Auth errors | 1001 unauthorized, 1002 token expired, 1003 user exists |
| 2000-2999 | Business errors | 2001 company not found, 2002 resume parse failed |
| 5000-5999 | System errors | 5001 internal error, 5002 AI service unavailable |

### 2.3 Standard Error Response

```json
{
  "code": 1001,
  "msg": "Token expired, please login again",
  "data": null,
  "trace_id": "a1b2c3d4"
}
```

### 2.4 Pagination Envelope

For list endpoints:

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

### 2.5 AI Service Response Format

AI service returns a simpler envelope (backend wraps it before forwarding to frontend):

```json
{
  "status": "success",
  "data": { ... },
  "model_used": "claude-haiku-4-5",
  "latency_ms": 320
}
```

Error variants:
```json
{
  "status": "empty",
  "data": null,
  "message": "No results found for the given query"
}
```
```json
{
  "status": "error",
  "data": null,
  "message": "LLM service timeout"
}
```

---

## 3. Phase A — User Authentication

### 3.1 Scope

- User registration (username + email + password)
- User login → JWT token
- JWT filter on all protected endpoints
- Frontend: login/register page with form validation

### 3.2 Backend Design

**New files:**

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
├── dto/ApiResponse.java          ← unified envelope
├── filter/JwtAuthFilter.java
├── util/JwtUtil.java
└── exception/BusinessException.java
```

**UserEntity** maps to the existing `users` table:

```java
@Entity
@Table(name = "users")
public class UserEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String username;       // unique
    private String email;          // unique
    private String passwordHash;   // BCrypt
    private String avatarUrl;
    private String major;
    private Integer graduationYear;
    @Column(columnDefinition = "JSON")
    private String preferences;    // JSON string
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

**API Endpoints:**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/auth/register` | No | Register new user |
| POST | `/auth/login` | No | Login, returns JWT |
| GET | `/auth/me` | Yes | Get current user profile |

**RegisterRequest:** `{ username, email, password, major?, graduationYear? }`
**LoginRequest:** `{ email, password }`
**AuthResponse:** `{ token, user: { id, username, email, major } }`

**JWT Configuration:**
- Algorithm: HS256
- Expiration: 24h (configurable via `app.jwt.expiration`)
- Claims: `userId`, `username`, `email`
- Header: `Authorization: Bearer <token>`

**JwtAuthFilter** intercepts all requests except `/auth/**` and `/health`, validates token, sets SecurityContext.

### 3.3 Frontend Design

**New files:**

```
frontend/src/
├── app/login/page.tsx          ← login form
├── app/register/page.tsx       ← register form
├── components/auth/
│   ├── LoginForm.tsx
│   └── RegisterForm.tsx
└── lib/store/authStore.ts      ← zustand store for auth state
```

**Auth flow:**
1. User submits login form → POST `/api/v1/auth/login`
2. Response contains JWT token → stored in localStorage
3. `authStore` (zustand) holds user state, synced with localStorage
4. API client interceptor already reads token from localStorage (exists in `src/lib/api.ts`)
5. 401 responses redirect to `/login` (already implemented in interceptor)

**shadcn/ui components used:** `Button`, `Input`, `Label`, `Card`, `Form`

### 3.4 Database Changes

None — `users` table already exists in `init.sql`. No schema changes needed.

---

## 4. Phase B — AI Chat Interface

### 4.1 Scope

- Chat UI with message bubbles (user + AI)
- Streaming-style display (typewriter effect, even with mock)
- Chat history: Redis (active 20 messages) + MySQL (full persistence)
- Session management (create, list, resume sessions)
- Mock AI responses covering: normal, empty, error scenarios

### 4.2 Chat History — Dual-Write Strategy

**Redis (hot cache):**
- Key pattern: `chat:session:{session_id}:messages`
- Data structure: Redis List (LPUSH new messages, LTRIM to 20)
- Each entry: `{ role, content, timestamp }`
- TTL: 7 days, refreshed on each new message
- Purpose: fast reads for active conversation context

**MySQL (persistent):**
- Table: `chat_messages` (new table, see schema below)
- Full message history, no TTL
- Purpose: search, resume sessions, analytics

**Write path:**
```
User sends message
  → Backend writes to MySQL (chat_messages)
  → Backend LPUSH to Redis list, LTRIM 0..19
  → Backend calls AI service
  → AI returns response
  → Backend writes AI response to MySQL
  → Backend LPUSH AI response to Redis
  → Returns to frontend
```

**Read path (session resume):**
```
User opens existing session
  → Backend checks Redis for cached messages
  → If Redis has data: return Redis list (fast path)
  → If Redis miss: load last 20 from MySQL, warm Redis cache, return
```

### 4.3 New Database Table

```sql
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('user', 'assistant', 'system') NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(50) COMMENT 'LLM-identified intent: job_search, company_info, resume_help, general',
    model_used VARCHAR(50) COMMENT 'which LLM tier was used',
    metadata JSON COMMENT 'extra data: sources, confidence, etc.',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_session_time (session_id, created_at),
    INDEX idx_user_sessions (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

The existing `chat_sessions` table stores session metadata (title, message_count, channel). Messages go in `chat_messages`.

### 4.4 Backend Design

**New files:**

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

**API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/chat/sessions` | Create new session, returns session_id |
| GET | `/chat/sessions` | List user's sessions (paginated) |
| GET | `/chat/sessions/{id}/messages` | Get messages for a session |
| POST | `/chat/sessions/{id}/messages` | Send message, get AI response |

**ChatServiceImpl key logic:**
```java
public ChatMessageDto sendMessage(Long userId, String sessionId, String content) {
    // 1. Save user message to MySQL
    // 2. LPUSH to Redis, LTRIM to 19 (index 0..19 = 20 items)
    // 3. Load recent context from Redis (last 10 messages for LLM context)
    // 4. Call AI service: POST http://ai-service:8000/chat/send
    // 5. Save AI response to MySQL
    // 6. LPUSH AI response to Redis
    // 7. Return AI message DTO
}
```

### 4.5 AI Service Design (Mock)

**New files:**

```
ai-service/app/
├── services/mock_data/chat_responses.json
├── services/chat_service.py
├── schemas/chat.py
└── api/chat.py (updated)
```

**Mock response routing** — the mock service selects responses based on keyword matching:

| Input Pattern | Intent | Mock Response |
|---------------|--------|---------------|
| Contains "岗位", "工作", "实习" | job_search | Returns 3 sample job recommendations |
| Contains "公司", "字节", "腾讯" | company_info | Returns a sample company card |
| Contains "简历", "简历分析" | resume_help | Returns resume tips |
| Contains "薪资", "工资", "钱" | salary | Returns salary range data |
| Default | general | Returns a helpful general response |

**Three mock scenarios** (each intent has all three):

```json
{
  "normal": { "status": "success", "data": { "content": "...", "intent": "job_search", "sources": [...] } },
  "empty":  { "status": "empty",  "data": null, "message": "未找到相关结果，请尝试换个关键词" },
  "error":  { "status": "error",  "data": null, "message": "AI 服务暂时不可用，请稍后重试" }
}
```

Error and empty scenarios trigger randomly with 5% and 10% probability respectively in mock mode, configurable via env var `MOCK_ERROR_RATE`.

### 4.6 Frontend Design

**New files:**

```
frontend/src/
├── app/chat/page.tsx                 ← main chat page
├── app/chat/layout.tsx               ← chat layout with sidebar
├── components/chat/
│   ├── ChatSidebar.tsx               ← session list
│   ├── ChatMessageList.tsx           ← message bubbles
│   ├── ChatInput.tsx                 ← input + send button
│   ├── ChatMessage.tsx               ← single message bubble
│   └── TypewriterText.tsx            ← typewriter animation
└── lib/store/chatStore.ts            ← zustand chat state
```

**Chat UI layout:**

```
┌─────────┬──────────────────────────────────┐
│ Session │                                  │
│ List    │     Message Area                 │
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

**shadcn/ui components used:** `ScrollArea`, `Avatar`, `Button`, `Input`, `Separator`, `Skeleton`

**Typewriter effect:** AI messages animate character-by-character using a `TypewriterText` component. In mock mode, the animation runs on the pre-fetched response. When real LLM streaming is added later, this component will consume SSE chunks instead.

---

## 5. Phase C — Job Exploration Engine

### 5.1 Scope

- Static JSON seed data covering 20+ majors across 5 categories
- Major → Job tag mapping browsing
- Fuzzy keyword search (mock LLM returns curated results)
- Job detail cards with tag chips

### 5.2 Seed Data Design

**File:** `ai-service/app/services/mock_data/job_knowledge_graph.json`

**Coverage (20+ majors):**

| Category | Majors |
|----------|--------|
| 工学 | 计算机科学与技术, 软件工程, 人工智能, 电子信息工程, 通信工程, 自动化 |
| 理学 | 数学与应用数学, 统计学, 物理学 |
| 经济学 | 经济学, 金融学, 国际经济与贸易 |
| 管理学 | 工商管理, 市场营销, 会计学, 人力资源管理, 信息管理与信息系统 |
| 文学 | 英语, 新闻学, 广告学 |

Each major entry follows the schema from the planning doc Appendix:

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

### 5.3 Backend Design

**New files:**

```
backend/src/main/java/com/jobpilot/
├── controller/JobExploreController.java
├── service/JobExploreService.java
├── service/impl/JobExploreServiceImpl.java
├── dto/JobTagDto.java
├── dto/MajorJobDto.java
└── dto/JobSearchRequest.java
```

**API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/jobs/categories` | List all categories (工学, 理学, ...) |
| GET | `/jobs/categories/{category}/majors` | List majors in a category |
| GET | `/jobs/majors/{major}` | Get full job mapping for a major |
| GET | `/jobs/search?q=xxx` | Fuzzy search → mock LLM returns ranked results |

**Backend acts as a proxy** — it forwards category/major queries to the AI service, which returns data from the static JSON seed. This keeps the AI service as the single source of truth for knowledge graph data, making it easy to swap static JSON for real RAG later.

### 5.4 AI Service Design

**New files:**

```
ai-service/app/
├── services/job_explore_service.py
├── services/mock_data/job_knowledge_graph.json
└── schemas/job.py
```

**Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/jobs/categories` | Returns category list from JSON |
| GET | `/jobs/majors/{major}` | Returns major detail from JSON |
| GET | `/jobs/search?q=xxx` | Mock fuzzy search: keyword matching + random ranking |

**Fuzzy search mock logic:**
1. Receive query string
2. Search across all majors' `job_title`, `tags`, `description` fields
3. Return top 5 matches with mock confidence scores
4. Empty query → return empty result (tests the empty scenario)

### 5.5 Frontend Design

**New files:**

```
frontend/src/
├── app/explore/page.tsx              ← main exploration page
├── app/explore/[category]/page.tsx   ← category detail
├── app/explore/major/[name]/page.tsx ← major → job cards
├── components/explore/
│   ├── CategoryGrid.tsx              ← category cards grid
│   ├── MajorCard.tsx                 ← major card with primary jobs
│   ├── JobTagCard.tsx                ← single job tag card
│   ├── JobDetailPanel.tsx            ← expanded job detail
│   └── SearchBar.tsx                 ← fuzzy search input
```

**Page flow:**

```
/explore
  → CategoryGrid: shows 5 category cards (工学/理学/经济/管理/文学)
  → Click category → /explore/{category}
    → MajorCard list: shows majors in that category
    → Click major → /explore/major/{name}
      → JobTagCard grid: primary jobs + extended jobs
      → Click a job → JobDetailPanel slides in (tags, companies, salary, description)
```

**Search bar** is sticky at top of `/explore` page. Typing triggers debounced search (300ms) → results appear in a dropdown overlay.

**shadcn/ui components used:** `Card`, `Badge` (for tags), `Dialog` (for job detail), `Command` (for search), `Tabs` (primary/extended jobs), `Skeleton`

---

## 6. Phase D — Company Intelligence Module

### 6.1 Scope

- Company search with autocomplete
- Company intelligence card (basic info, JD, timeline, salary, reviews)
- Cache strategy: Redis (hot) + MySQL (warm) + AI service (cold/mock)
- Mock web search returns curated company data

### 6.2 Company Cache Strategy

```
Request: "字节跳动"
  → Backend checks Redis: key `company:intel:{company_name}`
    → HIT: return cached data (TTL 24h)
    → MISS: check MySQL company_cache table
      → HIT & not expired: return data, warm Redis
      → MISS or expired: call AI service
        → AI service returns mock company intel
        → Backend writes to MySQL + Redis
        → Return to frontend
```

### 6.3 Backend Design

**New files:**

```
backend/src/main/java/com/jobpilot/
├── entity/CompanyCacheEntity.java
├── repository/CompanyCacheRepository.java
├── controller/CompanyController.java
├── service/CompanyService.java
├── service/impl/CompanyServiceImpl.java
└── dto/CompanyIntelDto.java
```

**API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| GET | `/companies/search?q=xxx` | Autocomplete company names |
| GET | `/companies/{name}` | Get full company intelligence |
| POST | `/companies/{name}/refresh` | Force refresh cache |

**CompanyCacheEntity** maps to existing `company_cache` table:
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

### 6.4 AI Service Design (Mock)

**New files:**

```
ai-service/app/
├── services/company_service.py
├── services/mock_data/company_data.json
└── schemas/company.py
```

**Mock data** covers 10 popular tech companies:

| Company | Data Included |
|---------|--------------|
| 字节跳动, 腾讯, 阿里巴巴, 美团, 百度 | Basic info, salary, reviews, timeline |
| 华为, 小米, 京东, 网易, 快手 | Basic info, salary, reviews, timeline |

Each company entry follows the `CompanyIntel` interface from `frontend/src/types/index.ts`.

**Three mock scenarios:**
- Normal: full company intel returned
- Empty: company name not in mock dataset → "未找到该公司信息"
- Error: random 5% chance of simulated timeout

### 6.5 Frontend Design

**New files:**

```
frontend/src/
├── app/company/page.tsx              ← search + results
├── app/company/[name]/page.tsx       ← company detail page
├── components/company/
│   ├── CompanySearch.tsx             ← search with autocomplete
│   ├── CompanyCard.tsx               ← summary card in search results
│   ├── CompanyDetailCard.tsx         ← full intelligence card
│   ├── CompanyTimeline.tsx           ← timeline visualization
│   ├── SalaryChart.tsx               ← ECharts bar chart
│   └── ReviewRadar.tsx               ← ECharts radar chart
```

**Company detail page layout:**

```
┌────────────────────────────────────────────┐
│  公司名称 | 行业 | 规模 | 融资阶段          │
├────────────────────────────────────────────┤
│  ┌──────────┐  ┌──────────┐  ┌──────────┐ │
│  │ 基础信息  │  │ 薪资分布  │  │ 评价雷达  │ │
│  │ Card     │  │ ECharts  │  │ ECharts  │ │
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

**shadcn/ui components used:** `Command` (search), `Card`, `Badge`, `Tabs` (info/salary/reviews)
**ECharts:** `SalaryChart` (bar), `ReviewRadar` (radar) — using `echarts-for-react`

---

## 7. Phase E — Resume Upload & Analysis

### 7.1 Scope

- Resume upload (PDF, DOCX) with local file storage
- LLM parse → structured resume JSON (mock)
- Resume vs JD analysis scoring (mock)
- Resume list with version history

### 7.2 File Storage Design

**MVP: local volume, structured by user:**

```
/uploads/{user_id}/{original_filename}
```

**Storage interface** (abstraction for future OSS switch):

```python
# ai-service/app/services/storage_service.py
class StorageService(ABC):
    @abstractmethod
    async def save(self, user_id: str, filename: str, content: bytes) -> str:
        """Save file, return access path"""
        pass

    @abstractmethod
    async def read(self, path: str) -> bytes:
        """Read file content"""
        pass

    @abstractmethod
    async def delete(self, path: str) -> bool:
        """Delete file"""
        pass

class LocalStorageService(StorageService):
    BASE_DIR = "/uploads"
    # Implementation: writes to /uploads/{user_id}/{filename}

class OSSStorageService(StorageService):
    # Stub for Phase 2: delegates to MinIO/阿里云 OSS
    pass
```

Backend also stores the file path in `resumes.raw_file_url` column, so switching storage only requires updating the `StorageService` implementation — no DB migration needed.

### 7.3 Backend Design

**New files:**

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

**API Endpoints:**

| Method | Path | Description |
|--------|------|-------------|
| POST | `/resumes/upload` | Upload resume file (multipart) |
| GET | `/resumes` | List user's resumes |
| GET | `/resumes/{id}` | Get parsed resume data |
| POST | `/resumes/{id}/analyze` | Analyze against target position |
| GET | `/resumes/{id}/score` | Get latest analysis result |

**Upload flow:**
```
POST /resumes/upload (multipart: file + optional targetPosition)
  → Validate file type (PDF/DOCX only, max 10MB)
  → Forward file to AI service for parsing
  → AI returns structured resume JSON
  → Save file to local volume: /uploads/{user_id}/{filename}
  → Save structured data + file path to MySQL resumes table
  → Return ResumeDto
```

### 7.4 AI Service Design (Mock)

**New files:**

```
ai-service/app/
├── services/resume_service.py
├── services/storage_service.py
├── services/mock_data/resume_responses.json
├── services/mock_data/resume_scores.json
└── schemas/resume.py
```

**Mock parse response** — returns a complete structured resume for any uploaded file:

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

**Mock analysis response** — returns scoring against any target position:

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

**Three mock scenarios:**
- Normal: returns full structured resume / score
- Empty: unsupported file type → "不支持的文件格式，请上传 PDF 或 DOCX"
- Error: random 5% chance → "简历解析服务暂时不可用"

### 7.5 Frontend Design

**New files:**

```
frontend/src/
├── app/resume/page.tsx               ← resume list
├── app/resume/upload/page.tsx        ← upload page
├── app/resume/[id]/page.tsx          ← resume detail + analysis
├── components/resume/
│   ├── ResumeUpload.tsx              ← drag-and-drop upload zone
│   ├── ResumePreview.tsx             ← structured resume display
│   ├── ScoreRadar.tsx                ← ECharts radar for scoring
│   ├── ScoreDimensionCard.tsx        ← single dimension score card
│   └── SuggestionList.tsx            ← improvement suggestions
```

**Upload page:** drag-and-drop zone using shadcn/ui `Card` + native HTML drag events. File type validation on client side before upload.

**Analysis page layout:**

```
┌────────────────────────────────────────────┐
│  简历详情            [重新上传] [分析匹配度] │
├────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────────────┐   │
│  │ 结构化简历  │  │ 匹配度雷达图       │   │
│  │ 预览       │  │ 整体: 78%          │   │
│  │            │  │ (ECharts radar)    │   │
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

**shadcn/ui components used:** `Card`, `Progress` (dimension scores), `Badge`, `Button`, `Alert` (for suggestions)

---

## 8. Testing Strategy

### 8.1 Backend Tests

Each phase includes:
- **Unit tests** for service layer (mock repositories)
- **Integration tests** for controller layer (MockMvc, embedded Redis for Phase B)

### 8.2 AI Service Tests

- **Unit tests** for mock services (verify all three scenarios: normal, empty, error)
- **API tests** using pytest + httpx `AsyncClient`

### 8.3 Frontend Tests

- **Component tests** for key UI components (not in MVP scope, noted for Phase 2)

### 8.4 Mock Data Validation

All mock JSON files are validated against the TypeScript interfaces at build time. AI service mock responses must match the schemas defined in `ai-service/app/schemas/`.

---

## 9. Out of Scope for MVP

The following are explicitly deferred:
- RabbitMQ async messaging
- Real LLM API integration (Phase 2)
- WeChat/Feishu channels
- Resume beautification / export (Phase 2)
- Job Tracker kanban (Phase 2)
- Unit tests for frontend components (Phase 2)
- E2E tests (Phase 2)
- RAG / vector search (Phase 2)
- Dynamic web crawling for company data
