# 参与 CampusPulse

[English](CONTRIBUTING.md) · [项目介绍](README.zh-CN.md) · [反馈问题或提出建议](https://github.com/xkkkkkkm/campuspulse/issues/new/choose)

欢迎提供可复现的问题、改进文档或提交范围明确的代码修改。Issue 和 Pull Request 均可使用简体中文或英文。

安装和使用问题可在 [Discussions](https://github.com/xkkkkkkm/campuspulse/discussions) 交流；可复现缺陷或范围明确的功能建议请使用 [Issue 表单](https://github.com/xkkkkkkm/campuspulse/issues/new/choose)。

## 从小改进入手

- 体验[本地演示](README.zh-CN.md#下载运行)，反馈难以理解的步骤或可复现的问题，并说明页面、账号角色和界面语言。
- 同步完善中英文安装说明、修复失效链接，或澄清已有功能的实际行为。
- 对照真实界面校对 [knowledge.json](support-agent/app/knowledge.json) 中的双语帮助指南，或按[本地化说明](frontend/README.md#localization)改进界面文案。

以上是入门方向，不代表已经创建或可认领的 Issue。开始前搜索[已有 Issue](https://github.com/xkkkkkkm/campuspulse/issues) 和 [Pull Request](https://github.com/xkkkkkkm/campuspulse/pulls)，避免重复工作。较大的改动建议先通过功能建议说明用户问题和范围；小型修正可以直接提交 PR。

## 本地运行

在 GitHub Fork 仓库，克隆自己的 Fork，并为修改创建分支。安装 **Python 3.11+** 和带 **Compose v2 的 Docker**，启动 Docker 后，在仓库根目录执行：

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

访问 [http://127.0.0.1:8125](http://127.0.0.1:8125)，使用 [README 中的公开演示账号](README.zh-CN.md#下载运行)。首次运行会下载依赖。`python3 tools/dev.py down` 停止服务并保留数据。Windows 可将 `python3` 替换为 `py -3.11`，或已安装的 Python 3.11+ 命令。原生开发和端口配置见[运行与运维](docs/operations.zh-CN.md#原生开发)。

默认演示使用本地客服检索，邮箱验证码显示在页面中；模型生成默认关闭，真实外部模型与 SMTP 投递尚未联调。改进文档或普通演示功能无需提供服务商凭据。

## 按修改范围验证

选择能够验证此次修改的检查。文档修正无需启动完整应用。除链接中的模块指南另有说明外，下列命令均在仓库根目录执行。

| 修改范围 | 建议检查 |
| --- | --- |
| 文档 | 核对相对链接、命令路径及中英文一致性。修改安装命令时尽可能实际执行，并说明未验证的步骤。 |
| 前端 JavaScript 或文案 | 使用 Node.js 22+，先执行 `npm --prefix frontend ci`，再执行 `npm --prefix frontend run check` 和 `npm --prefix frontend test`。页面行为使用[前端指南](frontend/README.md#verification)中的模拟 API 浏览器测试。 |
| API 逻辑 | 使用 JDK 17、Maven 3.9+、Python 3.11+，执行 `python3 tools/dev.py run mvn -f backend/pom.xml test`。涉及持久化、数据库迁移或集成行为时执行 `python3 tools/dev.py test`，需要 Docker 创建临时 MySQL Testcontainers 数据库。见[后端测试说明](backend/README.md#tests)。 |
| Python 辅助工具 | 使用 Python 3.11+ 执行 `python3 -m unittest discover -s tools/tests`。 |
| 本地前端代理 | 使用 Node.js 22+ 执行 `node --test tools/tests/proxy.test.cjs`。 |
| 客服指南或服务逻辑 | 按[中文客服说明](support-agent/README.zh-CN.md)配置 Python 3.13 环境并运行模拟服务商测试。 |
| 推荐训练 | 按[中文模型说明](ml/README.zh-CN.md)配置虚拟环境并运行单元测试。修改模型发布时另用可丢弃数据库进行对应集成验证。 |

修复行为问题时，尽可能增加或调整针对性的回归测试。可见界面改动应检查两种语言，必要时附截图方便审阅。翻译应作用于界面标签及经过校对的演示内容，用户自己填写的文本保留原文。

真实浏览器、媒体、模型发布和恢复演练可能写入数据。相关改动需要这些检查时，使用[隔离集成环境](docs/operations.zh-CN.md#隔离集成演练)，并保持模型生成关闭。单元或模拟测试不能证明真实 SMTP 或模型投递成功。[CI 工作流](.github/workflows/ci.yml)定义了自动检查；请记录实际执行结果，不要推定检查已经通过。

## 提交 Pull Request

一次 PR 聚焦一个问题，说明用户遇到的现象、修改后的行为，以及相关 Issue（如有）。通过 PR 模板记录执行的具体检查、结果，以及未运行的检查和原因。行为或配置改变时更新相关文档。数据库结构变更应新增 Flyway 迁移，不修改已经应用的迁移文件。

提交前检查差异。代码、日志、截图、Issue 和 PR 中不要包含 `.env` 文件、API Key、密码、令牌、数据库备份、私聊或用户个人信息；复现问题使用脱敏样本。引入第三方资源时保留其许可声明，参见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。
