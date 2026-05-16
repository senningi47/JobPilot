-- JobPilot Database Initialization
CREATE DATABASE IF NOT EXISTS jobpilot
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE jobpilot;

-- Users
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(500),
    major VARCHAR(100),
    graduation_year INT,
    preferences JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Resumes
CREATE TABLE IF NOT EXISTS resumes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    structured_data JSON COMMENT '结构化简历数据',
    raw_file_url VARCHAR(500) COMMENT '原始文件 OSS 路径',
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Job Collections (favorites)
CREATE TABLE IF NOT EXISTS job_collections (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_title VARCHAR(100) NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    company_id VARCHAR(50),
    source_url VARCHAR(500),
    jd_summary TEXT,
    tags JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Company Intelligence Cache
CREATE TABLE IF NOT EXISTS company_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    company_name VARCHAR(100) NOT NULL,
    company_name_en VARCHAR(100),
    basic_info JSON COMMENT '基础信息',
    salary_data JSON COMMENT '薪资数据',
    review_summary JSON COMMENT '评价聚合',
    timeline JSON COMMENT '发展时间线',
    data_source VARCHAR(50),
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    INDEX idx_company_name (company_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Job Tracker Records
CREATE TABLE IF NOT EXISTS tracker_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_name VARCHAR(100) NOT NULL,
    position VARCHAR(100) NOT NULL,
    apply_date DATE,
    status ENUM('interested','applied','screening','assessment','interviewing','waiting','offer','rejected') DEFAULT 'interested',
    notes TEXT,
    deadline DATE,
    next_action VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Chat Sessions
CREATE TABLE IF NOT EXISTS chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL,
    channel VARCHAR(20) DEFAULT 'web',
    title VARCHAR(200),
    message_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_user_sessions (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Chat Messages
CREATE TABLE IF NOT EXISTS chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    role ENUM('user', 'assistant') NOT NULL,
    content TEXT NOT NULL,
    intent VARCHAR(50),
    model_used VARCHAR(50),
    token_count INT DEFAULT 0,
    metadata JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_session_time (session_id, created_at),
    INDEX idx_user_sessions (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- AI Provider Configuration
CREATE TABLE IF NOT EXISTS ai_provider_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT 'NULL 表示系统全局配置',
    provider_name VARCHAR(50) NOT NULL COMMENT 'groq / siliconflow / openrouter / custom',
    display_name VARCHAR(100) COMMENT '展示名称',
    base_url VARCHAR(255),
    api_key VARCHAR(255) COMMENT '加密存储',
    model_name VARCHAR(100),
    is_active BOOLEAN DEFAULT FALSE,
    is_builtin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Search Provider Configuration
CREATE TABLE IF NOT EXISTS search_provider_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT COMMENT 'NULL 表示系统全局配置',
    provider_name VARCHAR(50) NOT NULL COMMENT 'tavily / serper / searxng / bing / custom',
    display_name VARCHAR(100),
    api_key VARCHAR(255) COMMENT '加密存储，SearXNG 可为空',
    base_url VARCHAR(255) COMMENT 'SearXNG 自定义实例填这里',
    is_active BOOLEAN DEFAULT FALSE,
    is_builtin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Job Alerts
CREATE TABLE IF NOT EXISTS job_alerts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_types JSON COMMENT '岗位类型列表',
    cities JSON COMMENT '目标城市列表',
    salary_min INT,
    salary_max INT,
    company_size VARCHAR(50),
    frequency ENUM('daily','weekly') DEFAULT 'daily',
    channel VARCHAR(20) DEFAULT 'web',
    is_active BOOLEAN DEFAULT TRUE,
    last_fired_at TIMESTAMP NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
