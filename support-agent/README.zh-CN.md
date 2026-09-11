# CampusPulse LangGraph 智能客服

[English](README.md)

本服务使用 Python、FastAPI 和真正的 LangGraph `StateGraph` 提供双语客服。Java 负责公网接口、用户认证、会话归属及持久化、个人调用额度和工单创建；Python 服务不连接业务数据库，不持有业务登录凭据，不提供浏览器、工单客户端或模型工具。

处理流程为：问题路由 → 本地双语指南检索 → 可选模型生成 → 输出及引用校验 → 回答或建议提交工单。未配置模型时，只能通过本地词表匹配 15 条双语指南，不能生成开放式模型回答。没有模型配置、模型超时、调用失败或生成结果未通过校验时，返回本地指南或本地提示，并使用 `LANGGRAPH_RETRIEVAL` 标明来源。建议提交工单不会自动创建工单。

推荐使用 Compose，配置步骤见根目录说明中的[启用模型生成客服（可选）](../README.zh-CN.md#启用模型生成客服可选)。由**部署者**在项目根目录 `.env` 填写模型服务配置，网站访客无需填写模型密钥。真实模型调用适配器已经实现，并通过模拟 HTTP 响应验证；尚未完成真实服务商联调验收。

## 本地运行

使用 Python 3.13。依赖于 2026-09-10 查询 PyPI 稳定版本并解析兼容组合，包含 LangGraph 1.2.11、langchain-openai 1.6.2、FastAPI 0.141.1 和 Uvicorn 0.52.4；运行时全部解析依赖固定在 `requirements.txt`，测试依赖在 `requirements-dev.txt`。无需下载 embedding 或启动向量数据库。

原生启动的以下命令均在**项目根目录**运行，不要切换到 `support-agent`。如果根目录还没有 `.env`，先执行 `python3 tools/dev.py init`；已有环境直接复用现有 `.env`。用 Python 3.13 创建虚拟环境并安装客服运行依赖：

```sh
python3.13 -m venv support-agent/.venv
support-agent/.venv/bin/python -m pip install -r support-agent/requirements.txt
python3 tools/dev.py run support-agent/.venv/bin/python -m uvicorn app.main:app --app-dir support-agent --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
```

Windows 使用 `py -3.13` 替换 `python3.13`，`py -3` 替换 `python3`，虚拟环境可执行文件使用 `Scripts/python.exe`：

```powershell
py -3.13 -m venv support-agent/.venv
support-agent/.venv/Scripts/python.exe -m pip install -r support-agent/requirements.txt
py -3 tools/dev.py run support-agent/.venv/Scripts/python.exe -m uvicorn app.main:app --app-dir support-agent --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
```

按[原生开发说明](../docs/operations.zh-CN.md#原生开发)准备 Java 环境，在另一个根目录终端运行 `python3 tools/dev.py backend`（Windows 使用 `py -3 tools/dev.py backend`）。两进程通过脚本读取同一个根目录 `.env`，包括同一个 `SUPPORT_AGENT_TOKEN`，不要为 Python 另外生成或导出第二个令牌。已设置的 shell 环境变量会覆盖 `.env`，切换配置时需清除过时的 `SUPPORT_*` 环境覆盖。`SUPPORT_AGENT_ENABLED=true` 时，Java 默认连接 `http://127.0.0.1:8001`。`SUPPORT_AGENT_TOKEN` 是 Java 与 Python 的内部共享令牌，**不是**模型服务商的 `SUPPORT_LLM_API_KEY`。

容器在私有网络的 8001 端口监听，不应设置向宿主机发布的 Compose `ports`；本地开发仅绑定回环地址。并发限制为进程内状态，应保持单 Uvicorn worker。

## 内部 HTTP 合同

`GET /healthz` 用于私网健康检查，无需鉴权，仅返回 `status`、`engine` 和 `knowledgeVersion`，不包含会话或密钥。交互式文档和 OpenAPI 路由关闭。

`POST /v1/answer` 必须携带 `Authorization: Bearer <SUPPORT_AGENT_TOKEN>`：

```json
{
  "message": "忘记密码怎么办？",
  "locale": "zh-CN",
  "history": [
    {"role": "user", "content": "我无法登录。"},
    {"role": "assistant", "content": "是否需要找回密码？"}
  ],
  "allowGeneration": false
}
```

问题最多 500 个字符且不得为空白；语言仅支持 `zh-CN`（默认）和 `en-US`；历史最多 6 条，每条 1–1000 字符，角色只允许 `user`/`assistant`。其他字段、角色和伪布尔类型均拒绝。合同没有账户 ID、个人档案、登录令牌、模型地址、工具命令或会话 ID 字段。Java 先校验会话归属，再选取最近历史；简短追问可沿用最近用户问题的主题，不把历史助手回答当作可信知识。

返回格式：

```json
{
  "answer": "找回或修改密码\n……",
  "source": "LANGGRAPH_RETRIEVAL",
  "citations": [
    {"id": "account-password", "title": "找回或修改密码", "url": "/login.html"}
  ],
  "suggestEscalation": false
}
```

回答最多 4000 字符，最多 3 条引用。`source` 只会是 `LANGGRAPH_RETRIEVAL` 或 `LANGGRAPH_LLM`。引用 ID 必须属于本轮检索结果，标题和链接由本地指南生成，模型不能提供任意链接。引用链接指向对应平台操作页面，表示版本化产品指南入口，不表示实时抓取过页面或查询过个人状态。

鉴权失败返回 401；未设置内部令牌或容量满返回 503（容量满带 `Retry-After: 1`）；参数错误返回 422 且不回显原文；超长请求体（包括分块传输）返回 413；完整请求超时返回 504。Java 在内部服务不可用时应继续提供本地回退回答。

## 可选模型与隐私边界

同时满足 `SUPPORT_LLM_ENABLED=true`、非空 `SUPPORT_LLM_API_KEY`、已配置 `SUPPORT_LLM_MODEL`，并且当前请求 `allowGeneration=true`，才允许调用模型。Java 仅在用户已登录且生成额度允许时传 `true`；匿名请求必须传 `false`。Python 还要求问题属于普通问答且命中本地指南；未知问题、试图覆盖指令的问题和转人工请求均不调用模型。没有默认模型，仅设置密钥不会开启模型调用。

启用或切换模型时，由部署者按[Compose 配置示例](../README.zh-CN.md#启用模型生成客服可选)修改根目录 `.env` 的 `SUPPORT_LLM_ENABLED`、`SUPPORT_LLM_API_KEY`、`SUPPORT_LLM_BASE_URL` 和 `SUPPORT_LLM_MODEL`。服务地址使用提供商文档中的 API 基址，模型名使用该账号实际可用的准确模型标识。Compose 执行 `python3 tools/dev.py up` 应用配置；原生运行需停止并通过 `tools/dev.py` 重新启动 Java 和 Python，因为配置在启动时读取。关闭生成时，将 `SUPPORT_LLM_ENABLED=false` 应用到这两个服务；保留 `SUPPORT_AGENT_ENABLED=true` 即可继续使用 LangGraph 本地检索。

验证时，先登录网站，再问一个指南覆盖的问题，例如“忘记密码怎么办？”。在客服 API 响应或已保存会话中查看 `source`：

| 来源 | 含义 |
| --- | --- |
| `LANGGRAPH_LLM` | 已配置的模型服务返回答案，且输出与引用校验通过 |
| `LANGGRAPH_RETRIEVAL` | Python 返回本地指南或本地提示，没有采用有效的模型结果 |
| `LOCAL_KNOWLEDGE` | 客服代理未启用、不可用或容量已满时，由 Java 返回本地回退回答 |

如果仍是检索回答，先检查两个服务是否加载修改后的配置、用户是否登录、生成额度是否还有剩余：每用户每小时允许 10 次尝试；`SUPPORT_MAX_DAILY_GENERATIONS` 默认 100，按 UTC 日在整个部署中共享。额度在 Python 判断是否调用模型前预留，因此不等于成功生成次数。随后确认问题命中指南，以及所选服务和模型支持 Chat Completions 的 `response_format={"type":"json_object"}`。鉴权失败、模型不可用、服务商限流、默认 **8 秒**模型超时、JSON 格式错误、模型拒答或语言/引用/内容校验失败，都会回退本地回答。服务商异常日志只记录异常类型，不公开被拒绝的模型正文。`/healthz` 正常只代表服务可用，不代表模型调用成功；Java 回退与配置排查另见[运维说明](../docs/operations.zh-CN.md)。

开启生成意味着允许将当前问题、最多 6 条最近对话和检索到的公开平台指南发送给所配置的模型服务商。应用不会额外附加账户 ID、私人档案、内部鉴权令牌或应用密钥。原文中的常见邮箱、手机号/长数字标识、带标签的密码/验证码/账号、Bearer 令牌和常见密钥格式会先做本地脱敏。但脱敏不是完整的个人信息识别：姓名、地址、特殊密钥形式或自由描述中的私人信息仍可能保留。不要在客服问题中填写敏感数据；需要问题完全留在本地时，应关闭模型生成。整个图执行显式关闭 LangSmith tracing，不会因环境中启用了 tracing 就上传对话。失败日志只记录异常类型，不记录问题、请求头或服务商错误正文。

模型可以结合有限历史生成选定语言的实际回答，但仅得到受限的不可信对话及对应指南，不绑定任何工具，不能请求用户给出的 URL、访问数据库、审批申请或创建工单。模型地址只能来自部署配置，不能由请求或模型覆盖。适配器不自动重试、不跟随跳转，请求未压缩响应并拒绝压缩或过大的响应，同时限制模型执行和整体请求时间。

校验会检查 JSON 结构、回答长度、语言、引用归属与重复项，并拒绝链接、HTML、常见敏感信息和明显声称“已经替你完成操作”的输出。模型表示无法确认、出错或不满足校验时回退本地指南。这些校验不能证明每个句子都被引用支持，也不能消除所有幻觉或提示词注入。检索采用小型人工审核词表，特殊表述可能漏检或匹配较弱；未命中问题建议提交工单，不编造答案。本服务不能查询实时个人或活动状态。

| 环境变量 | 默认值 | 作用 |
| --- | --- | --- |
| `SUPPORT_AGENT_TOKEN` | 空 | `tools/dev.py init` 生成的 Java/Python 内部共享令牌；不是模型密钥；为空时拒绝回答请求 |
| `SUPPORT_LLM_ENABLED` | `false` | 部署方显式开启模型 |
| `SUPPORT_LLM_API_KEY` | 空 | 由部署者填写的模型服务密钥，仅模型适配器使用，不在网站中填写 |
| `SUPPORT_LLM_BASE_URL` | `https://api.openai.com/v1` | OpenAI 兼容地址；远端服务应使用 HTTPS |
| `SUPPORT_LLM_MODEL` | 空 | 显式指定的模型名称 |
| `SUPPORT_AGENT_MAX_CONCURRENCY` | `4` | 同时处理回答请求数，范围 1–32，无无限等待队列 |
| `SUPPORT_AGENT_REQUEST_TIMEOUT_SECONDS` | `12` | 包括读取请求体的完整请求期限，最大 60 秒 |
| `SUPPORT_LLM_TIMEOUT_SECONDS` | `8` | 模型期限，大于 0 且短于完整请求期限 |
| `SUPPORT_AGENT_MAX_BODY_BYTES` | `32768` | 实际请求体字节限制，范围 1024–65536 |
| `SUPPORT_LLM_MAX_RESPONSE_BYTES` | `65536` | 模型 HTTP 响应字节限制，范围 1024–262144 |

模型服务需兼容 Chat Completions 和 JSON object 输出。模型可用性、计费、限流和数据保留策略由部署方配置。以下本地测试不调用外部模型、不使用真实密钥；针对运行中网站的测试，登录后可能调用已配置的模型服务，无外部调用的验证应使用单独的测试配置并设置 `SUPPORT_LLM_ENABLED=false`。

## 知识维护与验证

`app/knowledge.json` 包含 15 条双语结构化指南，覆盖注册登录、密码、活动、组队、聊天及私有图片、通知、推荐、工单、个人资料和语言。每条记录 `reviewedAgainst` 源码路径，方便随功能变化复核。不会读取历史 Dify 文档或旧的大型客服知识文档，更不会整篇加入模型上下文。更新事实时同步两种语言；新链接需要显式加入 `ALLOWED_URLS`。

在项目根目录安装额外测试依赖，再进入 `support-agent` 执行测试：

```sh
support-agent/.venv/bin/python -m pip install -r support-agent/requirements-dev.txt
cd support-agent
.venv/bin/python -m pytest -q
.venv/bin/python -m pip check
```

Windows 将两种虚拟环境路径中的 `/bin/python` 替换为 `/Scripts/python.exe`。之后再次使用 `tools/dev.py` 前，需返回项目根目录。

测试覆盖真正的 LangGraph 调用和 ASGI HTTP 边界、双语无密钥检索、追问上下文、模型启用开关、脱敏、注入与未知问题、错误引用、模型拒答/异常、鉴权、超长与分块请求、并发拒绝和容量恢复、模型与整体超时。真实 `ChatOpenAI` 适配器通过进程内模拟 HTTP 服务验证序列化和响应解析，不访问外部模型或使用真实密钥。测试通过不代表已验证真实服务商回答质量、公网生产负载或 Docker 部署。
