from typing import Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator


class StrictModel(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class HistoryMessage(StrictModel):
    role: Literal["user", "assistant"]
    content: str = Field(min_length=1, max_length=1000)


class AnswerRequest(StrictModel):
    message: str = Field(min_length=1, max_length=500)
    locale: Literal["zh-CN", "en-US"] = "zh-CN"
    history: list[HistoryMessage] = Field(default_factory=list, max_length=6)
    allowGeneration: bool = False

    @field_validator("message")
    @classmethod
    def nonempty(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("message must not be blank")
        return value.strip()


class Citation(StrictModel):
    id: str = Field(min_length=1, max_length=80)
    title: str = Field(min_length=1, max_length=160)
    url: str = Field(min_length=1, max_length=160)


class AnswerResponse(StrictModel):
    answer: str = Field(min_length=1, max_length=4000)
    source: Literal["LANGGRAPH_RETRIEVAL", "LANGGRAPH_LLM"]
    citations: list[Citation] = Field(default_factory=list, max_length=3)
    suggestEscalation: bool = False


class GeneratedAnswer(StrictModel):
    answer: str = Field(min_length=1, max_length=4000)
    citationIds: list[str] = Field(min_length=1, max_length=3)
    abstain: bool
