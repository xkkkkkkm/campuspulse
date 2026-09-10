"""Actual LangGraph orchestration; all branches produce a bounded response."""

import asyncio
import logging
import re
from typing import TypedDict

from langgraph.graph import END, START, StateGraph
from langsmith import tracing_context

from .config import Settings
from .knowledge import Knowledge
from .models import AnswerRequest, AnswerResponse, GeneratedAnswer
from .provider import Provider, redact

logger = logging.getLogger(__name__)
INJECTION = re.compile(
    r"ignore\s+(?:all\s+)?(?:previous|prior|above|system)|system\s+prompt|developer\s+message|"
    r"reveal\s+(?:the\s+)?(?:prompt|secret|token)|jailbreak|"
    r"忽略.{0,12}(?:指令|规则|提示|要求)|(?:泄露|透露|输出|显示).{0,8}(?:系统提示|密钥|令牌)|"
    r"(?:绕过|突破).{0,8}(?:权限|验证)|执行.{0,8}(?:sql|shell|命令)", re.I,
)
ESCALATE = re.compile(r"人工|转人工|投诉|\b(human (?:support|agent)|escalate|complaint)\b", re.I)
ACTION_CLAIM = re.compile(
    r"\bI(?:'ve| have)?\s+(?:successfully\s+)?(?:created|submitted|sent|reset|changed|deleted|approved|rejected|cancelled|updated)\b|"
    r"(?:已|已经)(?:为你|为您|帮你|帮您|替你|替您)(?:成功)?(?:创建|提交|发送|重置|修改|删除|审批|批准|取消|更新)|"
    r"(?:工单|密码|账户|账号|报名)(?:已|已经)(?:成功)?(?:创建|提交|重置|修改|批准|取消)", re.I,
)
UNSAFE_LINK = re.compile(r"(?:https?://|www\.|\[[^\]]*\]\(|<[/!a-z]|(?:javascript|data|file):|/[\w.-]+\.html)", re.I)


class State(TypedDict, total=False):
    request: AnswerRequest
    route: str
    entries: list[dict]
    generated: GeneratedAnswer | None
    uncertain: bool
    response: AnswerResponse


class SupportGraph:
    def __init__(self, settings: Settings, provider: Provider | None = None, knowledge: Knowledge | None = None):
        self.settings, self.provider = settings, provider
        self.knowledge = knowledge or Knowledge()
        builder = StateGraph(State)
        builder.add_node("route", self._route)
        builder.add_node("retrieve", self._retrieve)
        builder.add_node("generate", self._generate)
        builder.add_node("ground", self._ground)
        builder.add_node("answer", self._answer)
        builder.add_edge(START, "route")
        builder.add_conditional_edges("route", lambda state: "answer" if state["route"] == "blocked" else "retrieve")
        builder.add_conditional_edges("retrieve", self._after_retrieve)
        builder.add_edge("generate", "ground")
        builder.add_edge("ground", "answer")
        builder.add_edge("answer", END)
        self.graph = builder.compile()

    async def answer(self, request: AnswerRequest) -> AnswerResponse:
        # Never let environment-enabled LangSmith tracing export chat text.
        with tracing_context(enabled=False):
            result = await self.graph.ainvoke({"request": request}, config={"recursion_limit": 10})
        return result["response"]

    def _route(self, state: State) -> dict:
        request = state["request"]
        poisoned = INJECTION.search(request.message) or any(INJECTION.search(turn.content) for turn in request.history)
        return {"route": "blocked" if poisoned else "escalate" if ESCALATE.search(request.message) else "normal"}

    def _retrieve(self, state: State) -> dict:
        request = state["request"]
        entries = self.knowledge.retrieve(request.message, [turn.model_dump() for turn in request.history])
        if state["route"] == "escalate":
            entries = [self.knowledge.by_id["support-tickets"]]
        return {"entries": entries}

    def _after_retrieve(self, state: State) -> str:
        if state["entries"] and state["route"] == "normal" and state["request"].allowGeneration and self.settings.generation_available and self.provider:
            return "generate"
        return "answer"

    async def _generate(self, state: State) -> dict:
        request = state["request"]
        evidence = [{"id": entry["id"], "title": entry[request.locale]["title"], "facts": entry[request.locale]["facts"]} for entry in state["entries"]]
        # Providers never receive identity metadata or deployment secrets. Common
        # sensitive strings in user-authored prose are also removed locally.
        try:
            generated = await asyncio.wait_for(self.provider.generate(
                message=redact(request.message),
                history=[{"role": turn.role, "content": redact(turn.content)} for turn in request.history],
                locale=request.locale, evidence=evidence,
            ), timeout=self.settings.llm_timeout)
            return {"generated": GeneratedAnswer.model_validate(generated)}
        except Exception as error:
            # Exception messages may contain provider URLs, headers or text.
            logger.warning("Support generation fell back to retrieval (%s)", type(error).__name__)
            return {"generated": None}

    def _ground(self, state: State) -> dict:
        generated = state.get("generated")
        if not generated:
            return {}
        allowed = {entry["id"] for entry in state["entries"]}
        chinese = bool(re.search(r"[\u3400-\u9fff]", generated.answer))
        language_matches = chinese if state["request"].locale == "zh-CN" else not chinese
        valid = (
            generated.answer.strip()
            and not generated.abstain
            and set(generated.citationIds).issubset(allowed)
            and len(set(generated.citationIds)) == len(generated.citationIds)
            and not UNSAFE_LINK.search(generated.answer)
            and not ACTION_CLAIM.search(generated.answer)
            and not INJECTION.search(generated.answer)
            and redact(generated.answer) == generated.answer
            and language_matches
        )
        # This verifies structure/provenance and rejects obvious unsafe claims.
        # It does not prove semantic entailment or eliminate hallucinations.
        return {"generated": generated if valid else None, "uncertain": generated.abstain}

    def _answer(self, state: State) -> dict:
        request = state["request"]
        entries, generated = state.get("entries", []), state.get("generated")
        if generated:
            selected = {entry["id"]: entry for entry in entries}
            response = AnswerResponse(
                answer=generated.answer.strip(), source="LANGGRAPH_LLM",
                citations=[self.knowledge.citation(selected[key], request.locale) for key in generated.citationIds],
                suggestEscalation=False,
            )
        elif entries:
            paragraphs = [entry[request.locale]["title"] + "\n" + "\n".join(entry[request.locale]["facts"]) for entry in entries]
            if state.get("uncertain"):
                paragraphs.append("这些是相关操作指南，但不足以确认你的具体问题；如仍无法解决，请登录后提交支持工单。" if request.locale == "zh-CN" else "These are the related guides, but they do not establish an answer to your specific issue; sign in and submit a support ticket if it remains unresolved.")
            response = AnswerResponse(
                answer="\n\n".join(paragraphs)[:4000], source="LANGGRAPH_RETRIEVAL",
                citations=[self.knowledge.citation(entry, request.locale) for entry in entries],
                suggestEscalation=state["route"] == "escalate" or state.get("uncertain", False),
            )
        else:
            blocked = state["route"] == "blocked"
            if request.locale == "en-US":
                answer = "I can explain CampusPulse workflows using the reviewed guides. I cannot reveal secrets, bypass permissions or perform account actions." if blocked else "I do not have a verified guide that answers this question. You can ask about accounts, activities, teams, chat, notifications or recommendations."
                answer += " If you need help with a specific issue, sign in and submit a support ticket with the page, steps and error message. Do not include passwords or verification codes."
            else:
                answer = "我可以根据已审核的平台指南说明操作流程，不能泄露密钥、绕过权限或代替你执行账号操作。" if blocked else "当前已审核的指南无法确认这个问题的答案。可以询问账号、活动、组队、聊天、通知或推荐相关问题。"
                answer += "如需处理具体异常，请登录后提交支持工单，描述页面、操作步骤和错误提示，不要提供密码或验证码。"
            response = AnswerResponse(answer=answer, source="LANGGRAPH_RETRIEVAL", citations=[], suggestEscalation=True)
        return {"response": response}
