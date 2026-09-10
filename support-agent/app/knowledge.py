"""Small reviewed corpus with deterministic, bilingual phrase retrieval."""

import json
import re
from pathlib import Path

CORPUS_PATH = Path(__file__).with_name("knowledge.json")
ALLOWED_URLS = frozenset({
    "/register.html", "/login.html", "/profile.html", "/profile-edit.html",
    "/activity-lobby.html", "/publish-activity.html", "/team-lobby.html",
    "/chat.html", "/notifications.html", "/home.html", "/customer-service.html",
})


def contains(text: str, phrase: str) -> bool:
    if re.search(r"[\u3400-\u9fff]", phrase):
        return phrase in text
    return re.search(r"(?<![a-z0-9])" + re.escape(phrase) + r"(?![a-z0-9])", text) is not None


class Knowledge:
    def __init__(self, path: Path = CORPUS_PATH):
        corpus = json.loads(path.read_text(encoding="utf-8"))
        self.entries = corpus["entries"]
        self.by_id = {entry["id"]: entry for entry in self.entries}
        if len(self.by_id) != len(self.entries):
            raise ValueError("Duplicate knowledge IDs")
        for entry in self.entries:
            if entry["url"] not in ALLOWED_URLS:
                raise ValueError("Knowledge URL is not on the internal allowlist")
            for locale in ("en-US", "zh-CN"):
                if not entry[locale]["facts"] or not entry[locale]["title"]:
                    raise ValueError("Knowledge must have both languages")

    def retrieve(self, message: str, history: list[dict]) -> list[dict]:
        scores = self._scores(message)
        # Follow-ups inherit only the last substantive USER topic. Never treat a
        # previous assistant answer as authoritative knowledge or instructions.
        if not scores and self.is_followup(message):
            for turn in reversed(history):
                if turn["role"] == "user":
                    scores = self._scores(turn["content"])
                    if scores:
                        break
        if not scores:
            return []
        best = scores[0][0]
        return [entry for score, entry in scores if score >= max(1.0, best * 0.6)][:3]

    def _scores(self, message: str) -> list[tuple[float, dict]]:
        text = message.lower()
        scored = []
        for entry in self.entries:
            matches = [term for term in entry["keywords"] if contains(text, term.lower())]
            if matches:
                # Specific phrases beat a generic topic mention (e.g. image
                # upload versus chat). Length is capped so scripts cannot win.
                score = sum(
                    1 + min(len(term), 12) / 12
                    + (2 if " " in term or (len(term) >= 4 and re.search(r"[\u3400-\u9fff]", term)) else 0)
                    for term in matches
                )
                scored.append((score, entry))
        return sorted(scored, key=lambda item: (-item[0], item[1]["id"]))

    @staticmethod
    def is_followup(message: str) -> bool:
        return len(message) <= 100 and bool(re.search(
            r"\b(it|that|those|then|requirements?|how long|what about|and how|still|next)\b|"
            r"那|这个|这样|然后|还是|多久|要求|怎么办|具体|还有|详细|不行", message.lower()
        ))

    @staticmethod
    def citation(entry: dict, locale: str) -> dict:
        return {"id": entry["id"], "title": entry[locale]["title"], "url": entry["url"]}
