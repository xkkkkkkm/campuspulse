# Changelog

This log describes tagged source releases. See [GitHub Releases](https://github.com/xkkkkkkm/campuspulse/releases) for downloadable snapshots and [the commit history](https://github.com/xkkkkkkm/campuspulse/commits/main) for individual changes.

## v0.1.0 — Local demo preview — 2026-09-14

The first tagged preview packages the existing CampusPulse application for local evaluation and community contributions.

- English and Simplified Chinese interface, with reviewed translations for the seeded activity and team catalog.
- Activity discovery, registration review, team applications, membership management and persisted messaging.
- LangGraph support with cited bilingual guides, conversation history and explicit support tickets; optional provider-backed generation.
- Spring Boot/MySQL persistence, migrations, a Compose startup helper, and optional offline recommendation training.
- Bilingual setup instructions, product diagrams, actual interface screenshots, a product tour, contribution guides and issue/PR templates.

This is a local demo preview. External model calls and SMTP delivery have not been verified with real providers. Model generation is disabled by default, and deployment uses one API instance. See [the README](README.md), [operations](docs/operations.md) and [review guide](docs/portfolio-overview.md) for configuration and limits.

### 中文说明

首个带版本标签的源码预览，整理现有 CampusPulse 应用，便于本地体验和社区贡献。包含中英文界面、活动报名与组队审批、持久化消息、带引用的 LangGraph 客服指南与工单，以及 Docker Compose 启动和可选离线推荐训练。本次同时补齐真实界面截图、双语体验指南、贡献指南和 Issue/PR 模板。

此版本用于本地演示；模型生成默认关闭，真实模型和 SMTP 尚未与实际服务商联调，部署采用单 API 实例。运行方式与限制见[中文 README](README.zh-CN.md)及[运维说明](docs/operations.zh-CN.md)。
