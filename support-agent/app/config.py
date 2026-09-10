"""Deployment-owned configuration; request data cannot override a provider."""

import os
from dataclasses import dataclass
from urllib.parse import urlsplit


@dataclass(frozen=True)
class Settings:
    token: str = ""
    llm_enabled: bool = False
    llm_api_key: str = ""
    llm_base_url: str = "https://api.openai.com/v1"
    llm_model: str = ""
    max_concurrency: int = 4
    request_timeout: float = 12.0
    llm_timeout: float = 8.0
    max_body_bytes: int = 32768
    max_provider_bytes: int = 65536

    @property
    def generation_available(self) -> bool:
        return self.llm_enabled and bool(self.llm_api_key and self.llm_model)

    def __post_init__(self):
        if not 1 <= self.max_concurrency <= 32:
            raise ValueError("SUPPORT_AGENT_MAX_CONCURRENCY must be between 1 and 32")
        if not 0 < self.llm_timeout < self.request_timeout <= 60:
            raise ValueError("Timeouts must satisfy 0 < LLM < request <= 60 seconds")
        if not 1024 <= self.max_body_bytes <= 65536:
            raise ValueError("SUPPORT_AGENT_MAX_BODY_BYTES must be between 1024 and 65536")
        if not 1024 <= self.max_provider_bytes <= 262144:
            raise ValueError("SUPPORT_LLM_MAX_RESPONSE_BYTES must be between 1024 and 262144")
        endpoint = urlsplit(self.llm_base_url)
        if endpoint.scheme not in {"http", "https"} or not endpoint.hostname or endpoint.username or endpoint.password or endpoint.query or endpoint.fragment:
            raise ValueError("SUPPORT_LLM_BASE_URL must be an HTTP(S) endpoint without credentials or query")

    @classmethod
    def from_env(cls):
        return cls(
            token=os.getenv("SUPPORT_AGENT_TOKEN", "").strip(),
            llm_enabled=os.getenv("SUPPORT_LLM_ENABLED", "false").lower() == "true",
            llm_api_key=os.getenv("SUPPORT_LLM_API_KEY", "").strip(),
            llm_base_url=os.getenv("SUPPORT_LLM_BASE_URL", "https://api.openai.com/v1").strip(),
            llm_model=os.getenv("SUPPORT_LLM_MODEL", "").strip(),
            max_concurrency=int(os.getenv("SUPPORT_AGENT_MAX_CONCURRENCY", "4")),
            request_timeout=float(os.getenv("SUPPORT_AGENT_REQUEST_TIMEOUT_SECONDS", "12")),
            llm_timeout=float(os.getenv("SUPPORT_LLM_TIMEOUT_SECONDS", "8")),
            max_body_bytes=int(os.getenv("SUPPORT_AGENT_MAX_BODY_BYTES", "32768")),
            max_provider_bytes=int(os.getenv("SUPPORT_LLM_MAX_RESPONSE_BYTES", "65536")),
        )
