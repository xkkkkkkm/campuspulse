# CampusPulse support agent

[简体中文](README.zh-CN.md)

This private Python service provides bilingual support through an actual LangGraph `StateGraph`. Java owns the public API, user authentication, conversation ownership and storage, per-user quotas, and support ticket creation. This service has no database, application credentials, ticket API client, browser, or model tools.

The graph routes a question, retrieves reviewed local guides, optionally generates an answer with `langchain-openai`, checks its structure and citations, and returns an answer or a suggestion to submit a ticket. Missing configuration, provider errors, timeouts, or invalid generated answers fall back to the local guides. A returned suggestion never creates a ticket. `LANGGRAPH_RETRIEVAL` explicitly identifies answers that did not use a valid model result.

```mermaid
flowchart LR
  Q[Question + bounded history] --> R[Route]
  R -->|ordinary question| K[Bilingual local retrieval]
  R -->|instruction override attempt| A[Answer / suggest ticket]
  K -->|generation enabled and authorized| L[Optional LLM]
  K -->|no model / unknown / human request| A
  L --> G[Citation and output checks]
  G -->|valid answer or local fallback| A
```

## Run locally

Use Python 3.13. Dependencies are exact versions resolved from stable PyPI releases on 2026-09-10, including LangGraph 1.2.11, langchain-openai 1.6.2, FastAPI 0.141.1 and Uvicorn 0.52.4. The full resolved runtime dependency set is pinned in `requirements.txt`; tests are separate in `requirements-dev.txt`. No embedding download or vector database is needed.

```sh
cd support-agent
python3.13 -m venv .venv
.venv/bin/python -m pip install -r requirements-dev.txt
export SUPPORT_AGENT_TOKEN="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')"
.venv/bin/python -m uvicorn app.main:app --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
```

Configure the same internal token in Java. Keep the service private: the container listens on port 8001 on its container network and should have no Compose `ports` mapping. Local development binds only to loopback. Use one Uvicorn worker because the concurrency cap is per process.

## Internal HTTP contract

`GET /healthz` is unauthenticated for private health checks. It returns `status`, `engine`, and `knowledgeVersion`, with no credentials or conversation data. Interactive docs and OpenAPI routes are disabled.

`POST /v1/answer` requires `Authorization: Bearer <SUPPORT_AGENT_TOKEN>` and JSON:

```json
{
  "message": "How do I reset my password?",
  "locale": "en-US",
  "history": [
    {"role": "user", "content": "I cannot sign in."},
    {"role": "assistant", "content": "Do you need to reset your password?"}
  ],
  "allowGeneration": false
}
```

`message` is 1–500 characters after rejecting blank input, `locale` is `zh-CN` (default) or `en-US`, and `history` has at most six `user`/`assistant` messages of 1–1000 characters. Other roles, extra fields, and coerced boolean values are rejected. There is no user ID, profile, token, provider URL, tool command or conversation ID field. Java determines which prior messages belong to the caller. Follow-up retrieval can use the latest relevant user topic; prior assistant content is never used as authoritative evidence.

```json
{
  "answer": "Reset or change your password\n...",
  "source": "LANGGRAPH_RETRIEVAL",
  "citations": [
    {"id": "account-password", "title": "Reset or change your password", "url": "/login.html"}
  ],
  "suggestEscalation": false
}
```

Answers have at most 4000 characters and three citations. Sources are `LANGGRAPH_RETRIEVAL` or `LANGGRAPH_LLM`. Citation titles and URLs come exclusively from the local allowlist, never model-created metadata. URLs lead to the matching application workflow; these are versioned product guide citations, not claims that a remote page was fetched or that account status was checked.

Invalid credentials return 401; missing service token or saturated capacity returns 503 (busy responses include `Retry-After: 1`); invalid fields return 422 without echoing submitted data; oversized bodies, including chunked bodies, return 413; the overall request deadline returns 504. Java should fall back locally when the internal service is unavailable.

## Optional model generation and privacy

Generation requires all of `SUPPORT_LLM_ENABLED=true`, a nonempty `SUPPORT_LLM_API_KEY`, a configured `SUPPORT_LLM_MODEL`, and the individual request's `allowGeneration=true`. Java must set the latter only after authenticating the user and checking the generation quota. Anonymous questions must pass `false`. There is no default model, and a key alone does not enable generation.

Enabling generation authorizes sending the current question and up to six recent conversation messages, along with matching public product guides, to the configured provider. The application does not add account IDs, private profiles, internal bearer tokens or application secrets to that context. Common email addresses, phone/long numeric identifiers, labeled passwords/codes/IDs, bearer tokens and common key formats in question text are redacted locally. This is best-effort filtering: arbitrary names, addresses, novel secret formats and other personal prose can remain. Do not place sensitive data in support questions; leave generation disabled if questions must stay local. LangSmith tracing is disabled around graph execution even if tracing environment variables exist. Logs record only the exception class on provider failure, not prompts, headers or provider error bodies.

The provider receives bounded untrusted conversation text and retrieved guides. The model can write a contextual answer in the selected language; it has no tools and cannot fetch a URL, read a database, approve an application, or submit a ticket. The configured provider base URL is deployment-owned and cannot come from the request or the model. The adapter has no retries, does not follow redirects, requests uncompressed responses, and rejects compressed or oversized responses. Both provider execution and the full request have timeouts.

Output checks validate JSON shape, length, locale, citation membership and duplicates; reject links, HTML, common secret patterns and obvious claims that an account operation was completed; and use local fallback for provider abstention or failure. These checks do not prove that every generated sentence follows from its citations, and do not eliminate hallucinations or all prompt injection. The corpus is small and retrieval uses deterministic bilingual phrases; unusual wording can miss or choose a less relevant guide. Unknown questions suggest a ticket instead of inventing an answer. There is no live account or activity-state lookup.

| Environment variable | Default | Purpose |
| --- | --- | --- |
| `SUPPORT_AGENT_TOKEN` | empty | Shared internal bearer secret; empty disables answer requests; use a random 32-byte value |
| `SUPPORT_LLM_ENABLED` | `false` | Explicit deployment opt-in for model use |
| `SUPPORT_LLM_API_KEY` | empty | Secret for the configured provider |
| `SUPPORT_LLM_BASE_URL` | `https://api.openai.com/v1` | Deployment-owned OpenAI-compatible endpoint; use HTTPS for remote providers |
| `SUPPORT_LLM_MODEL` | empty | Explicit provider model name |
| `SUPPORT_AGENT_MAX_CONCURRENCY` | `4` | Active answer requests, 1–32; no unbounded queue |
| `SUPPORT_AGENT_REQUEST_TIMEOUT_SECONDS` | `12` | Full body read and answer deadline; at most 60 seconds |
| `SUPPORT_LLM_TIMEOUT_SECONDS` | `8` | Provider deadline; positive and shorter than request timeout |
| `SUPPORT_AGENT_MAX_BODY_BYTES` | `32768` | Actual HTTP request byte cap, configurable 1024–65536 |
| `SUPPORT_LLM_MAX_RESPONSE_BYTES` | `65536` | Provider HTTP response cap, configurable 1024–262144 |

The provider must implement chat completions and JSON-object response formatting. Provider-specific model availability, billing, rate limits and data retention require deployment configuration; no external generation is needed for local tests.

## Knowledge maintenance and verification

`app/knowledge.json` contains 15 bilingual entries covering registration, sign-in, password recovery, activities, team lifecycle, chat and private images, notifications, recommendations, tickets, profile and language. Each entry records `reviewedAgainst` source paths. Historical Dify documentation and the old large knowledge document are not read or included in prompts. Review facts against current source when workflows change, keep both locales aligned, and add any new link explicitly to `ALLOWED_URLS`.

```sh
.venv/bin/python -m pytest -q
.venv/bin/python -m pip check
```

Tests exercise actual LangGraph execution and ASGI HTTP handling, bilingual offline retrieval, short follow-ups, generation gating, redaction, injection/unknown routes, citation rejection, model abstention/failure, auth, bounded/chunked bodies, concurrency admission/recovery and deadlines. The real `ChatOpenAI` adapter is also tested through an in-process mock HTTP transport; no live provider or real key is used. Passing these tests does not establish real provider answer quality, public production load, or Docker deployment readiness.
