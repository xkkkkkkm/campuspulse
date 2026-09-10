# CampusPulse 校园活动智能推荐与组队平台

[English](README.md) · [运行与运维](docs/operations.zh-CN.md) · [架构](docs/architecture.md) · [接口](docs/api.md) · [项目审阅指南](docs/portfolio-overview.md)

CampusPulse 源于高校合作课程项目，围绕校园活动发现、报名与组队协作实现了一套可运行的系统。本仓库提供双语界面、Java API、MySQL 数据库以及可选的离线推荐训练程序，供代码审阅和本地体验；文档以现有实现为准。

## 已实现能力

- 活动浏览、筛选、搜索、收藏、报名及审批状态跟踪。
- 活动发布、编辑、报名审核、参与者群聊与活动提醒。
- 队伍创建、入队申请、申请审批、成员管理、队长转让和关闭。
- 私聊、队伍群聊、活动群聊、私有图片附件、消息历史和已读游标。
- 个人资料与兴趣标签；邮箱验证注册、账号恢复与账号信息变更。
- 通知、客服知识回答、工单提交以及管理员回复。
- 用户与角色管理、活动审核、标签、精选内容和操作审计。
- 基于兴趣、热度、时间和名额的规则推荐，可组合离线历史转化模型分数。

界面支持 English / 简体中文切换，40 条演示活动与 33 条队伍的标题、介绍、地点、标准标签和生成封面均提供英文展示，英文标题和标签也可以搜索。用户自己填写的内容和上传图片像素保留原文；修改演示文字后旧译文会失效。

智能客服已改为本地 **LangGraph** 服务：15 条双语指南、来源引用、持久化多轮会话和显式转人工工单。无需模型密钥即可检索回答；可另外启用模型生成。真实外部模型与 SMTP 尚未完成联调，配置见[中文客服说明](support-agent/README.zh-CN.md)。

## 下载运行

在 GitHub 点击 **Code → Download ZIP**，解压后在项目根目录打开终端，也可使用 Git 克隆。

安装 **Docker（含 Compose v2）** 与 **Python 3.11+**，启动 Docker 后执行：

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

访问 [http://127.0.0.1:8125](http://127.0.0.1:8125)。首次构建会下载依赖；脚本生成带随机应用密钥的本地 `.env`，随后构建并等待 MySQL、后端、前端和私有 LangGraph 客服这四个服务就绪。客服检索回答无需模型密钥。默认宿主机端口只绑定回环地址：前端 `8125`、后端 `8080`、数据库 `3306`；客服服务不映射宿主机端口。

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| 学生 | `linzhixia` | `demo12345` |
| 组织者 | `org` | `org123` |
| 管理员 | `admin` | `admin123` |

这些账号是公开的本地演示数据。默认 `demo` profile 只为每个数据库初始化一次；重启保留用户修改，也不会自动延后示例活动日期。演示模式会显示邮箱验证码，便于没有 SMTP 的情况下体验账号流程。真实用户部署必须使用新数据库并按[生产配置说明](docs/operations.zh-CN.md#生产配置)设置。

停止服务并保留数据卷：

```bash
python3 tools/dev.py down
```

端口冲突、原生 Java 开发、备份恢复及排障见[运行与运维](docs/operations.zh-CN.md)。

## 工程结构

| 目录 | 内容 |
| --- | --- |
| `frontend/` | 原生 HTML、CSS 与模块化浏览器 JavaScript；双语界面 |
| `backend/` | Java 17、Spring Boot、JDBC、Flyway 与 MySQL |
| `support-agent/` | FastAPI / LangGraph，双语指南检索和可选模型生成 |
| `ml/` | 时序 GradientBoostingClassifier 训练、评估、版本发布 |
| `docs/` | 当前架构、接口、运维说明及历史课程源材料 |
| `tools/` | 安全环境加载、本地代理、冒烟检查、备份工具 |

系统采用模块化单体：Node 服务提供静态页面并代理 API；单个 Spring Boot 实例处理业务，私有 LangGraph 服务处理客服检索和生成；MySQL 保存业务数据与推荐分数，持久化目录保存图片。SSE、短期连接票据和请求限流计数保存在进程内，当前部署边界是单 API 实例。HTTPS 终止和多实例共享基础设施需在部署环境另行配置。

推荐训练使用真实曝光和服务端记录的转化，以时间划分训练集与验证集并隔离标签窗口；排除旧版与合成演示行为。数据不足时输出 `insufficient-data`，不发布模型，在线继续规则推荐。详见[中文模型说明](ml/README.zh-CN.md)。仓库不宣称生产准确率或因果提升。

## 验证与审阅

```bash
# Java 17、Maven 3.9+，集成测试还需要运行中的 Docker
python3 tools/dev.py test

# Node.js 22+
npm --prefix frontend ci
npm --prefix frontend run check
npm --prefix frontend test
node --test tools/tests/proxy.test.cjs

python3 -m unittest discover -s tools/tests

# 需先启动演示服务；检查登录、浏览及管理员访问限制
python3 tools/dev.py run python3 tools/smoke_test.py
```

会写入测试数据的图片、模型发布和恢复检查见[隔离测试环境](docs/operations.zh-CN.md#隔离集成演练)。浏览器测试见[前端说明](frontend/README.md)，模型测试见[模型说明](ml/README.zh-CN.md)。这些命令用于复现验证，不代表所有环境均已通过；当前完成情况和限制见[整改状态与验证记录](docs/remediation-status.zh-CN.md)。历史课程评估、设计和管理材料仅保留为过程背景，不作为当前实现证据。

项目代码采用 [MIT License](LICENSE)，第三方资源遵循[各自许可证](THIRD_PARTY_NOTICES.md)。仓库不将合作成果全部归于个人，也不暗示学校背书。个人贡献应以提交记录和可审阅改动为依据。课程旧二进制、真实密钥、用户上传及生成文件不进入源码发布。
