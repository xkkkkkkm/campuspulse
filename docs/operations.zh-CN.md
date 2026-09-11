# CampusPulse 运行与运维

[English](operations.md) · [中文项目介绍](../README.zh-CN.md) · [架构](architecture.md)

以下命令均在项目根目录运行。开发与备份工具需要 Python 3.11+；Windows 如没有 `python3` 命令，可替换为对应版本的 `py -3.11`。示例使用默认端口，冲突时需在 `.env` 修改。

## 本地容器演示

宿主机安装支持 `up --wait` 的 Docker Compose v2 和 Python 3.11+。容器镜像内已安装 Java、Maven、Node.js 和客服所需的 Python 3.13，容器演示不要求宿主机另装这些运行时。启动 Docker 后执行：

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

脚本在项目根目录生成 `.env` 和不同的随机应用密钥，构建并启动 MySQL、Java 后端、Node 前端代理和私有 LangGraph 客服四个服务。首次运行需要下载镜像和依赖；数据库与图片保存在命名数据卷中。

| 服务 | 默认地址 | 用途 |
| --- | --- | --- |
| 前端 | `http://127.0.0.1:8125` | 页面及同源 API 代理 |
| API | `http://127.0.0.1:8080` | 业务 API、健康检查、开发接口文档 |
| MySQL | `127.0.0.1:3306` | 本地数据库连接及可选训练 |
| 客服服务 | 仅 Compose 内网：`http://support-agent:8001` | 由 Java 调用的私有 LangGraph 服务，不发布宿主机端口 |

宿主机端口默认只绑定回环地址。不要让容器后端与原生后端同时占用同一端口。例如本机 MySQL 已占用 3306、其他网站占用 8080，可在 `.env` 设置 `MYSQL_PORT=13306`、`BACKEND_PORT=18080`。如果还将 `FRONTEND_PORT` 改为 `18125`，同时设置 `APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:18125,http://localhost:18125`，再执行 `python3 tools/dev.py up`，浏览器打开 `http://127.0.0.1:18125`。这些配置修改宿主机端口，Compose 服务间仍使用原容器端口。

默认 `demo` profile 对每个数据库只初始化一次。演示账号为 `linzhixia / demo12345`、`org / org123`、`admin / admin123`。它们是公开示例，不是真实生产流量。重启不会恢复初始数据，也不会刷新活动日期。`APP_SECURITY_EMAIL_DEV_MODE=true` 时，填写符合流程要求的邮箱后点击**发送验证码**：注册页通过弹窗显示验证码；邮箱登录、找回密码及个人资料的账号变更流程，在附近的提示文字中显示“开发码”。发送验证码 API 的响应含 `data.devCode`，例如 `POST /api/auth/email/send`。这个模式不发送 SMTP 邮件，验证码应在页面查看。邮箱登录和找回密码仍要求该邮箱已绑定账号。

```bash
# 停止容器，保留数据库和图片
python3 tools/dev.py down

# 查看状态与最近日志
python3 tools/dev.py run docker compose ps
python3 tools/dev.py run docker compose logs --tail=100 backend
```

`docker compose down -v` 会删除命名数据卷及其内容，不是日常停止命令。需要全新演示数据时，可使用未占用的 Compose 项目名和不同宿主机端口，保留原项目。

## 环境文件与配置生效

`.env.example` 是默认配置样例；`python3 tools/dev.py init` 生成工具实际读取的根目录私有 `.env`。密钥保存在这个本地文件中。工具按数据解析 `KEY=value`，去除成对的外层引号，不执行 shell 代码，也不展开变量引用。推荐用单引号包住字面值，例如 `SUPPORT_LLM_API_KEY='your-provider-key'`，尤其是包含 `$` 或 `#` 的值；不要执行 `source .env`。

通过工具启动命令时，已有进程环境变量会覆盖选定文件中的同名值，空值也会覆盖。修改文件却不生效时，清除旧的已导出变量，或使用具有预期环境的新终端。`SERVER_PORT`、`PORT`、`TARGET`、`DB_URL`、`CAMPUS_DB_*` 等工具默认值只在变量不存在时设置；显式值可以覆盖端口和数据库的自动映射。Compose 只向容器传入 [docker-compose.yml](../docker-compose.yml) 中声明的配置，不会传入文件里的所有变量。

`--env-file` 选择一个文件，不会将 `.env.prod`、`.env.verify` 与 `.env` 合并，也不会自动创建另一个 Compose 项目；用 `-p` 隔离容器与数据卷。启动、状态、日志、停止操作均需保持相同的文件与项目名，例如：

```bash
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod ps
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod logs --tail=100 support-agent
```

编辑 `.env` 后，再运行 `python3 tools/dev.py up`。Compose 会重新创建配置发生变化的服务；`docker compose restart` 只用旧环境重启原容器。命名的正式项目应重新执行[生产配置](#生产配置)中的完整 `up` 命令。原生进程则用 Ctrl+C 停止，再通过相同 `--env-file` 的工具命令启动。

## 原生开发

安装 JDK 17、Maven 3.9+、Node.js 22+、供辅助工具使用的 Python 3.11+ 和 MySQL 8。原生运行 LangGraph 客服还需要 Python 3.13。使用自建 MySQL 时不必安装 Docker；也可只通过 Docker 运行 MySQL：

```bash
python3 tools/dev.py init
python3 tools/dev.py run docker compose up -d --wait mysql
python3 tools/dev.py backend
```

第二个终端启动前端代理：

```bash
python3 tools/dev.py frontend
```

要在本机运行 LangGraph，再打开第三个终端并进入项目根目录。首次创建虚拟环境、安装依赖，然后通过同一工具启动，让 Java 和 Python 读取相同的根目录 `.env` 及 `SUPPORT_AGENT_TOKEN`：

```bash
python3.13 -m venv support-agent/.venv
support-agent/.venv/bin/python -m pip install -r support-agent/requirements.txt
python3 tools/dev.py run support-agent/.venv/bin/python -m uvicorn app.main:app --app-dir support-agent --host 127.0.0.1 --port 8001 --workers 1 --no-access-log
```

Windows 用 `py -3.13 -m venv support-agent/.venv` 创建环境，将 `support-agent/.venv/bin/python` 换成 `support-agent/.venv/Scripts/python.exe`；辅助工具的 `python3` 可按需换成 `py -3.11`。Java 默认连接 `SUPPORT_AGENT_URL=http://127.0.0.1:8001`，须与客服监听地址一致。如果 8001 被占用，同时修改 Uvicorn 的 `--port` 和共用文件中的 `SUPPORT_AGENT_URL`，再重新启动这两个进程。Compose 内部主机名 `support-agent` 不适用于原生 Java。用 `curl --fail http://127.0.0.1:8001/healthz` 检查客服服务，详见[中文客服说明](../support-agent/README.zh-CN.md)。未启动该进程时，Java 仍可能用本地知识兜底回答，但不会运行 LangGraph 或调用模型。

工具为 Java、Node 代理和 Python 训练映射端口及数据库配置。直接运行 `mvn`、`node` 或 `uvicorn` 不会自动读取根目录 `.env`。使用自定义文件时，在各终端的 `backend`、`frontend` 或 `run` 之前统一添加 `--env-file`。自建 MySQL 的 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 可放入该文件或进程环境，先创建数据库；启动后由 Flyway 建表及升级。如果训练也使用外部数据库，按[中文模型说明](../ml/README.zh-CN.md)单独设置匹配的 `CAMPUS_DB_*`；工具不会从自定义 JDBC `DB_URL` 中解析训练配置。

服务页面本身不需要 npm 安装；开发检查和浏览器测试需要。不要用无限制的静态服务公开项目根目录，其中包含配置、源码及开发产物。

## 生产配置

生产 profile 提供的是单实例配置边界，不会自动安装 HTTPS、公网反向代理、共享限流、高可用或监控基础设施。

使用独立配置文件和**新数据库/Compose 项目**；切换 profile 不会删除已有公开演示账号：

```bash
python3 tools/dev.py --env-file .env.prod init --profile prod
```

启动前在本地编辑 `.env.prod`：

| 配置 | 要求 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | 保持 `prod` |
| `APP_SECURITY_TOKEN_SECRET`、`APP_SECURITY_EMAIL_SECRET` | 使用工具生成的不同随机密钥，私密保存 |
| `APP_SECURITY_EMAIL_DEV_MODE` | 保持 `false` |
| `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD` | 使用生成的正式密码；修改配置不会自动更新已有数据库卷的密码 |
| `APP_ADMIN_USERNAME`、`APP_ADMIN_PASSWORD`、`APP_ADMIN_EMAIL` | 填写首位管理员用户名和有效邮箱；工具会生成初始密码 |
| `MAIL_HOST`、`MAIL_PORT`、`MAIL_USERNAME`、`MAIL_PASSWORD` | 配置真实 SMTP 服务 |
| `MAIL_AUTH`、`MAIL_STARTTLS`、`APP_SECURITY_EMAIL_FROM` | 按服务商要求设置认证、STARTTLS 和已验证发件人 |
| `APP_CORS_ALLOWED_ORIGINS` | 填写实际浏览器来源，包含协议和端口 |
| `FRONTEND_PORT`、`BACKEND_PORT`、`MYSQL_PORT` | 选择未占用的宿主机端口，默认仍只绑定回环地址 |

首位管理员用户名接受 3–64 位字母、数字或下划线；密码需 16–128 位且包含足够不同字符。配置不合法会启动失败，不会直接提升同名现有账号。首次成功启动后从部署配置移除 `APP_ADMIN_PASSWORD`；后续重启使用已有管理员。

```bash
python3 tools/dev.py --env-file .env.prod run docker compose --env-file .env.prod -p campuspulse-prod up --build -d --wait --wait-timeout 240
```

SMTP 使用服务商提供的 SMTP 用户名和密码/授权码，与模型 API key 无关。`MAIL_STARTTLS=true` 启用 SMTP 连接上的 STARTTLS（常见端口 587），不会启用 465 端口的隐式 TLS/SMTPS，也不强制 STARTTLS 协商成功。现有 Compose 没有隐式 TLS 或强制 STARTTLS 的开关。选择服务商支持的 SMTP/STARTTLS 地址；其他传输要求需要显式扩展应用与 Compose 配置。后端运行在 Docker 中时，`MAIL_HOST=localhost` 指向后端容器本身，应改成真实 SMTP 主机；`APP_SECURITY_EMAIL_FROM` 应填写服务商允许的发件人。

后端拒绝弱密钥、相同的签名/验证密钥和开启的开发验证码模式。这些检查不验证 SMTP 的真实投递。接入真实用户前，使用选定服务商验证注册、找回密码及账号变更流程。健康检查不依赖邮件连接，因此 `UP` 不代表邮件可用。

为前端配置 HTTPS 反向代理，保持数据库/API 端口私有。反向代理需关闭 SSE 缓冲并允许长连接。当前应用按 socket 对端 IP 限流，多个用户经过内置代理后可能共用一个额度。公开使用前需设计可信代理和边界限流，不能直接信任用户传入的 `X-Forwarded-For`。

当前仅支持单 API 实例。水平扩展需共享票据、限流状态与实时事件，并设计图片存储；单独挂载共享目录不能解决多实例问题。

## 可选集成与模型

Compose 会启动本地 LangGraph 服务，无需模型密钥就能检索双语指南并返回引用。`tools/dev.py init` 生成内部共享密钥 `SUPPORT_AGENT_TOKEN`，它与模型服务商 API key 是两种凭据，Java 和客服服务须使用相同的内部密钥。该服务不向宿主机发布端口。启用模型生成回答请按首页[完整配置步骤](../README.zh-CN.md#启用模型生成客服可选)操作，再按前文重新创建配置过的服务。默认不指定模型。

已登录用户按用户限流，并受 `SUPPORT_MAX_DAILY_GENERATIONS`（默认 100）限制；匿名只使用检索。启用生成后，问题和受限的近期历史会发送给配置的服务商，并尽力脱敏。原生启动、限制及测试详见[中文客服说明](../support-agent/README.zh-CN.md)。真实外部模型和 SMTP 尚未联调。当前实现不读取历史 Dify 配置，应配置 LangGraph 服务。

推荐训练是手动离线命令，不是常驻服务。[中文模型说明](../ml/README.zh-CN.md)提供只读试运行、资源边界、发布与回滚步骤。没有模型也可完整运行应用；分数缺失或版本过期时使用规则推荐。

## 健康检查、日志和测试

[整改与验证记录](remediation-status.zh-CN.md)列出本地已执行结果和剩余检查；以下命令用于复现。

```bash
curl --fail http://127.0.0.1:8125/healthz
curl --fail http://127.0.0.1:8080/actuator/health/readiness
curl --fail http://127.0.0.1:8080/actuator/health/liveness
# 仅通过私有 API 地址查看指标
curl --fail http://127.0.0.1:8080/actuator/metrics

# Java 单元与真实 MySQL 集成测试；需要 Docker
python3 tools/dev.py test

# 前端源码、单元和代理检查
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend test
node --test tools/tests/proxy.test.cjs

# 辅助工具单元测试
python3 -m unittest discover -s tools/tests
```

`tools/smoke_test.py` 检查匿名队伍浏览，使用公开学生账号登录并读取个人资料、活动和推荐，同时验证普通账号不能访问管理员 API。运行 `python3 tools/dev.py run python3 tools/smoke_test.py`，默认地址为 `http://127.0.0.1:8125`，可通过 `--base-url` 覆盖。它需要未修改的演示密码，不覆盖完整业务，也不用于证明正式部署可用。虽然不创建业务样本，登录会更新账号锁定计数并写入认证审计，因此并非严格只读。浏览器和模型检查见各自目录 README。

运行前按检查类型选择目标：

| 检查 | 目标与写入行为 |
| --- | --- |
| Java `tools/dev.py test` | 在自建的临时 MySQL Testcontainers 数据库写入样本；宿主机需 JDK 17、Maven 和 Docker，不需运行演示栈 |
| 前端单元/模拟浏览器测试、辅助工具/客服单元测试 | 本地或模拟服务，不写真实应用样本，不调用付费模型 |
| `tools/smoke_test.py` | 运行中的演示环境；仅登录计数与认证审计写入 |
| `tools/support_smoke_test.py` | 可丢弃的运行中演示环境；写入会话轮次并在清理时删除自己的会话，登录审计保留 |
| `tools/media_smoke_test.py` | 可丢弃的运行中演示环境；留下上传图片和私聊消息 |
| 前端 `e2e:live` | 可丢弃的运行中演示环境；留下已回复/关闭的客服工单和浏览器行为记录，创建客服会话并尝试清理 |
| `ml/tests/integration_publication.py` | 显式指定的可丢弃 MySQL；写模型版本/分数、临时切换 active 版本，再恢复并清理测试版本 |
| `tools/integration_backup.py` | 可丢弃的 Compose 源项目和新恢复项目；写入图片/消息，暂停源后端并保留备份与项目 |

真实服务测试栈应保持 `SUPPORT_LLM_ENABLED=false`，在运行客服或真实浏览器检查前让配置生效。启用生成后，这些测试的登录用户问题可能调用已配置的真实服务商。删除测试会话不会撤回服务商请求或登录审计写入。

readiness 包含数据库健康状态，liveness 独立检查进程存活。指标仅通过私有 API 端口暴露，前端代理不转发。API 输出 ECS 结构化控制台日志及用于关联日志的 `X-Request-ID` 响应头。部署环境应收集日志，并避免导出密钥、Authorization 头、验证码或私聊正文。健康接口不暴露详细内部信息。开发接口文档为 `/api-docs-ui` 和 `/v3/api-docs`，在 `prod` 中关闭。

## 隔离集成演练

以下额外检查会写入测试样本，并在备份时暂停测试后端。必须使用**全新、可丢弃**的 Compose 项目及独立环境，不要对现有个人或正式数据库运行。下一节普通备份命令仍是日常运维入口。

先执行 `python3 tools/dev.py --env-file .env.verify init` 创建独立文件，在其中设置 `MYSQL_PORT=23306`、`BACKEND_PORT=28080`、`FRONTEND_PORT=28125`、`APP_CORS_ALLOWED_ORIGINS=http://127.0.0.1:28125,http://localhost:28125`。如果端口或下述项目名已被使用，应另选。保留 `demo` profile、该隔离库自己的演示账号，以及 `SUPPORT_LLM_ENABLED=false`。

```bash
python3 tools/dev.py --env-file .env.verify run docker compose --env-file .env.verify -p campuspulse-verify up --build -d --wait --wait-timeout 240

# 写入客服会话，检查历史与归属，然后删除自己的会话
python3 tools/support_smoke_test.py --base-url http://127.0.0.1:28125

# 创建图片与私聊，检查访问权限和重试幂等
python3 tools/media_smoke_test.py --base-url http://127.0.0.1:28125

# 先按 ml/README.zh-CN.md 安装Python环境，明确选择测试库
CAMPUS_TEST_DATABASE=1 python3 tools/dev.py --env-file .env.verify run .venv/bin/python ml/tests/integration_publication.py

# 只暂停这个可丢弃源项目，并恢复到另一个全新测试项目
CAMPUS_TEST_DATABASE=1 python3 tools/dev.py --env-file .env.verify run python3 tools/integration_backup.py --source-project campuspulse-verify --source-url http://127.0.0.1:28125 --target-project campuspulse-verify-restore --backup-path runtime/verify-backup --mysql-port 23307 --backend-port 28081 --frontend-port 28126
```

`media_smoke_test.py` 验证发送/接收方可读取图片，无关用户与匿名请求被拒绝，公开 `/uploads/chat` 路径返回 `404`，重复发送返回同一 ID。`integration_publication.py` 在真实 MySQL 上使用小型合成模型/样本，恢复原 active 指针并清理自己的测试版本；它验证发布协议，不代表推荐效果。

`integration_backup.py` 比较十一张持久内容表的数量，以接收方身份访问恢复的私有图片并比对 SHA-256，随后运行恢复环境的 API 冒烟。结果写入新备份目录的 `verification.json`，两个项目保留供检查。每次选择未使用的备份目录和恢复项目。`CAMPUS_TEST_DATABASE=1` 只是显式开关，不会自动判断数据库是否可丢弃；检查项目时应保留通过环境工具传入的匹配 `.env.verify`。

以上使用 POSIX shell 的环境赋值语法；PowerShell 可在受控命令前设置 `$env:CAMPUS_TEST_DATABASE='1'`。哪些演练实际完成，以[验证账本](remediation-status.zh-CN.md)为准。

## 备份与恢复

备份工具要求 Compose 后端和 MySQL 已运行。它短暂停止后端写入，导出数据库并归档 `/app/uploads`，最后重启后端；备份失败也会尝试重启。这是维护窗口，期间请求可能失败。独立训练任务或其他数据库写入者也需暂停；工具只控制 Compose 后端。

默认演示项目：

```bash
python3 tools/backup.py backup backups/demo-snapshot --project campuspulse
```

上述正式项目：

```bash
python3 tools/backup.py backup backups/prod-snapshot --project campuspulse-prod --env-file .env.prod
```

每次选择不存在的新目录。成功备份包含 `database.sql`、`uploads.tar.gz` 与 SHA-256 清单；没有完整清单的目录不能视为成功备份。数据包含账号、报名、聊天及图片，应私密保存。备份不包含 `.env`、密钥、源码版本或本地模型文件；这些需在安全的备份流程中另外保存。

恢复必须使用**全新、未使用的项目名**，不能覆盖正在运行的部署。单独准备环境文件，保留所需数据库凭据与 profile，并配置不同的 `MYSQL_PORT`、`BACKEND_PORT`、`FRONTEND_PORT`。同时更新恢复环境的 CORS 来源。例如私下复制 `.env.prod` 为 `.env.restore`，将端口改为 `13306`、`18080`、`18125`。

```bash
python3 tools/backup.py restore backups/prod-snapshot --project campuspulse-restore --env-file .env.restore
```

恢复会检查校验和与归档路径，拒绝源项目名，以及已经存在容器/数据卷的目标项目。随后创建新数据库卷、导入数据库和图片并启动应用。失败可能留下用于排障的部分目标资源；查看原因后，下次尝试仍需使用未使用的项目名。

恢复后验证健康检查、登录、代表性的报名/队伍成员关系、聊天历史及图片访问，并核对源代码版本与 Flyway 历史。仓库不宣称已测得恢复时间或数据丢失上限；在实际环境演练，验证完成前保留原系统。

## 保留策略与升级

有上限的清理任务大约每小时运行一次。通用任务删除六个月前的通知、十二个月前的行为/事件/提醒投递记录，每类每次最多 500 条。其验证码清理阈值是过期七天，但独立的认证任务已会删除过期一天以上的验证码、两天前的发送保护记录及 90 天前的认证审计，每类每次最多 1000 条。客服任务每次删除最多 100 个不活跃满 30 天且无有效轮次租约的会话，以及最多 1000 条超过 90 天的生成额度记录。用户也可选择或删除自己的历史会话；每个账号最多 50 个会话，每个会话最多 50 轮。这些任务不删除业务记录、私聊/群聊及管理操作审计。长期数据增长、旧模型版本及上传空间需要运维管理；当前不是完整的数据保留或彻底删除方案。

升级版本前先备份。Flyway 会校验并执行新迁移，不应修改已应用的迁移或关闭检查绕过错误。旧应用不一定兼容新数据库结构；应在隔离项目中演练恢复，不能假设数据库降级可逆。

## 常见问题

| 现象 | 优先检查 |
| --- | --- |
| 端口已占用 | 修改对应环境文件的端口与浏览器来源，再执行该项目的 `up` 命令重新创建容器 |
| 修改密钥/配置后没变化 | 核对 `--env-file`、项目名及旧的已导出变量；用 `up` 重新创建容器，或重启原生进程 |
| 原生客服一直使用兜底 | 单独启动客服进程，核对回环地址、健康检查和共用的 `SUPPORT_AGENT_TOKEN` |
| 修改密码后数据库拒绝访问 | 已有 MySQL 卷保留原密码；恢复匹配配置或正式轮换数据库凭据 |
| `prod` 启动失败 | 根据日志检查随机密钥、开发验证码开关和首位管理员参数 |
| Compose 一直未就绪 | 检查服务状态、后端/MySQL 日志、Docker 内存及依赖下载 |
| 页面显示后端异常 | 检查 API readiness 和代理目标，确认原生/容器后端没有争用端口 |
| 新演示库无法训练 | 真实且标签窗口成熟的样本不足时属于预期；旧数据和合成数据被排除 |
| 重启后演示活动日期较早 | 初始化只执行一次；创建新活动或新建独立演示库 |
| readiness 为 UP 但邮件失败 | 邮件不参与就绪检查，需单独验证服务商、发件人和 STARTTLS |
| 代理后突发请求返回 429 | 当前按代理地址共享 IP 额度；公开使用前完善边界和用户限流 |
