from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    # AI / LLM
    anthropic_api_key: str = ""
    llm_model_strong: str = "claude-opus-4-7"
    llm_model_medium: str = "claude-sonnet-4-6"
    llm_model_light: str = "claude-haiku-4-5-20251001"

    # Redis
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_password: str = ""

    # Qdrant
    qdrant_host: str = "localhost"
    qdrant_port: int = 6333

    # Application
    app_env: str = "development"

    class Config:
        env_file = ".env"
        extra = "ignore"


settings = Settings()
