"""Internal ASGI HTTP boundary. Java owns public auth, quotas and persistence."""

import asyncio
import hmac
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.exceptions import RequestValidationError
from starlette.responses import JSONResponse

from .config import Settings
from .graph import SupportGraph
from .models import AnswerRequest, AnswerResponse
from .provider import OpenAIProvider, Provider


class RequestBoundary:
    def __init__(self, app, settings: Settings):
        self.app, self.settings, self.active = app, settings, 0

    async def __call__(self, scope, receive, send):
        if scope["type"] != "http" or scope["path"] != "/v1/answer":
            return await self.app(scope, receive, send)
        settings = self.settings
        async def reject(status, detail, headers=None):
            await JSONResponse({"detail": detail}, status_code=status, headers=headers)(scope, receive, send)
        if not settings.token:
            return await reject(503, "Support agent authentication is not configured")
        auth = [value for key, value in scope["headers"] if key.lower() == b"authorization"]
        expected = ("Bearer " + settings.token).encode()
        if len(auth) != 1 or not hmac.compare_digest(auth[0], expected):
            return await reject(401, "Invalid internal service credentials", {"WWW-Authenticate": "Bearer"})
        if self.active >= settings.max_concurrency:
            return await reject(503, "Support agent is busy", {"Retry-After": "1"})
        # No await between check/increment: one event loop, one server worker.
        self.active += 1
        started = False
        async def tracked_send(message):
            nonlocal started
            if message["type"] == "http.response.start":
                started = True
            await send(message)
        try:
            async with asyncio.timeout(settings.request_timeout):
                headers = dict(scope["headers"])
                try:
                    length = int(headers.get(b"content-length", b"0"))
                except ValueError:
                    return await reject(400, "Invalid Content-Length")
                if length < 0:
                    return await reject(400, "Invalid Content-Length")
                if length > settings.max_body_bytes:
                    return await reject(413, "Request body is too large")
                body = bytearray()
                while True:
                    part = await receive()
                    if part["type"] == "http.disconnect":
                        return
                    body.extend(part.get("body", b""))
                    if len(body) > settings.max_body_bytes:
                        return await reject(413, "Request body is too large")
                    if not part.get("more_body", False):
                        break
                consumed = False
                async def replay():
                    nonlocal consumed
                    if not consumed:
                        consumed = True
                        return {"type": "http.request", "body": bytes(body), "more_body": False}
                    return await receive()
                await self.app(scope, replay, tracked_send)
        except TimeoutError:
            if not started:
                await reject(504, "Support agent request timed out")
        finally:
            self.active -= 1


def create_app(settings: Settings | None = None, provider: Provider | None = None) -> FastAPI:
    settings = settings or Settings.from_env()
    active_provider = provider if provider is not None else OpenAIProvider(settings) if settings.generation_available else None
    graph = SupportGraph(settings, active_provider)

    @asynccontextmanager
    async def lifespan(_app):
        yield
        if isinstance(active_provider, OpenAIProvider):
            await active_provider.aclose()

    app = FastAPI(title="CampusPulse internal support agent", docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan)
    app.state.graph = graph
    app.add_middleware(RequestBoundary, settings=settings)

    @app.exception_handler(RequestValidationError)
    async def validation_error(_request, _error):
        # FastAPI's default errors echo submitted input, which can contain secrets.
        return JSONResponse({"detail": "Invalid support request"}, status_code=422)

    @app.get("/healthz")
    async def health():
        return {"status": "ok", "engine": "langgraph", "knowledgeVersion": "2026-09-10"}

    @app.post("/v1/answer", response_model=AnswerResponse)
    async def answer(request: AnswerRequest):
        return await graph.answer(request)

    return app


app = create_app()
