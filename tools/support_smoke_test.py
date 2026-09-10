"""Exercise localized content and LangGraph through a running disposable demo stack."""
import argparse
import json
import urllib.error
import urllib.parse
import urllib.request


def check(base):
    def call(path, data=None, token=None, language="en-US", method=None):
        headers = {"Content-Type": "application/json", "Accept-Language": language}
        if token:
            headers["Authorization"] = "Bearer " + token
        request = urllib.request.Request(base + "/api" + path, headers=headers, method=method,
                                         data=None if data is None else json.dumps(data).encode())
        try:
            with urllib.request.urlopen(request, timeout=25) as response:
                return response.status, json.load(response).get("data")
        except urllib.error.HTTPError as error:
            return error.code, None

    def ok(*args, **kwargs):
        status, data = call(*args, **kwargs)
        assert status == 200, (args[0], status)
        return data

    activity = next(item for item in ok("/activities?size=100")["items"] if item.get("translations", {}).get("en", {}).get("title"))
    translated_title = activity["translations"]["en"]["title"]
    query = urllib.parse.urlencode({"keyword": translated_title, "size": 100})
    assert activity["id"] in [item["id"] for item in ok("/activities?" + query)["items"]]
    team = next(item for item in ok("/teams/page?size=100")["items"] if item.get("translations", {}).get("en", {}).get("title"))
    assert team["title"] != team["translations"]["en"]["title"]
    guest = ok("/support/chat", {"message": "How can I join a team?"})
    assert guest["source"] == "LANGGRAPH_RETRIEVAL" and guest["citations"]
    assert guest["conversationId"] is None and not guest["escalated"]
    chinese = ok("/support/chat", {"message": "如何报名活动？"}, language="zh-CN")
    assert chinese["source"] == "LANGGRAPH_RETRIEVAL" and "报名" in chinese["answer"]
    student = ok("/auth/login", {"usernameOrStudentNo": "linzhixia", "password": "demo12345"})
    other = ok("/auth/login", {"usernameOrStudentNo": "chenze", "password": "demo12345"})
    token = student["token"]
    tickets_before = ok("/support/tickets", token=token)
    first = ok("/support/chat", {"message": "How can I join a team?"}, token=token)
    conversation = first["conversationId"]
    assert conversation
    try:
        followup = ok("/support/chat", {"message": "What about the requirements?", "conversationId": conversation}, token=token)
        assert followup["conversationId"] == conversation and followup["citations"]
        assert len(ok("/support/conversations/" + conversation, token=token)["messages"]) == 4
        assert call("/support/conversations/" + conversation, token=other["token"])[0] == 404
        assert call("/support/chat", {"message": "hello", "conversationId": conversation}, token=other["token"])[0] == 404
        unknown = ok("/support/chat", {"message": "What is tomorrow's winning lottery number?", "conversationId": conversation}, token=token)
        assert unknown["suggestEscalation"] and not unknown["escalated"] and unknown["ticketId"] is None
        assert ok("/support/tickets", token=token) == tickets_before
    finally:
        ok("/support/conversations/" + conversation, token=token, method="DELETE")
    assert call("/support/conversations/" + conversation, token=token)[0] == 404
    return {"english_activity_title": translated_title, "english_team_title": team["translations"]["en"]["title"],
            "english_search": "passed", "bilingual_langgraph": "passed", "multi_turn_context": "passed",
            "conversation_ownership": "passed", "explicit_escalation_only": "passed", "conversation_deleted": True}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:8125")
    print(json.dumps(check(parser.parse_args().base_url.rstrip("/")), indent=2, ensure_ascii=False))
