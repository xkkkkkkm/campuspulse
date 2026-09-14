# Contributing to CampusPulse

[简体中文](CONTRIBUTING.zh-CN.md) · [Project overview](README.md) · [Report a bug or suggest a feature](https://github.com/xkkkkkkm/campuspulse/issues/new/choose)

CampusPulse welcomes reproducible bug reports, documentation improvements and focused code changes. Issues and pull requests can be written in English or Simplified Chinese.

For setup and usage questions, use [Discussions](https://github.com/xkkkkkkm/campuspulse/discussions). For a reproducible defect or a scoped feature proposal, use the [issue forms](https://github.com/xkkkkkkm/campuspulse/issues/new/choose).

## Start with a small contribution

- Try the [local demo](README.md#download-and-run) and report a confusing step or a reproducible bug, including the page, role and interface language.
- Improve the English and Chinese setup instructions together, fix a broken link, or clarify an existing feature's behavior.
- Review a bilingual help guide in [knowledge.json](support-agent/app/knowledge.json) against the actual UI, or improve an interface label using the [localization guide](frontend/README.md#localization).

These are suggested starting points, not a list of assigned or available issues. Search [existing issues](https://github.com/xkkkkkkm/campuspulse/issues) and [pull requests](https://github.com/xkkkkkkm/campuspulse/pulls) before duplicating work. For a larger change, describe the user problem and proposed scope in a feature request first. A small correction can go straight to a pull request.

## Run locally

Fork the repository on GitHub, clone your fork, and create a branch for your change. Run the following from the repository root with **Python 3.11+** and a running **Docker engine with Compose v2**:

```bash
python3 tools/dev.py init
python3 tools/dev.py up
```

Open [http://127.0.0.1:8125](http://127.0.0.1:8125); use the public demo accounts in the [README](README.md#download-and-run). The first run downloads dependencies. `python3 tools/dev.py down` stops the stack while retaining its data. On Windows, replace `python3` with `py -3.11` or your installed Python 3.11+ command. Native development and port configuration are covered in [operations](docs/operations.md#native-development).

The default demo uses local support retrieval and displays email verification codes on screen. Model generation is disabled by default; real external model calls and SMTP delivery remain unverified. You do not need provider credentials to contribute to documentation or ordinary demo features.

## Validate the part you change

Choose checks that exercise your change. A documentation correction does not need a full application stack. Commands below run from the repository root unless the linked component guide says otherwise.

| Change | Useful checks |
| --- | --- |
| Documentation | Check relative links, command paths and consistency between English and Chinese. If you changed a setup command, try it when possible and state any untested steps. |
| Frontend JavaScript or labels | With Node.js 22+: `npm --prefix frontend ci`, then `npm --prefix frontend run check` and `npm --prefix frontend test`. For page behavior, use the mock browser checks in [frontend/README.md](frontend/README.md#verification). |
| API logic | With JDK 17, Maven 3.9+ and Python 3.11+: `python3 tools/dev.py run mvn -f backend/pom.xml test`. For persistence, migrations or integration behavior, run `python3 tools/dev.py test`; Docker is required for its temporary MySQL Testcontainers databases. See [backend tests](backend/README.md#tests). |
| Python helper tools | `python3 -m unittest discover -s tools/tests` with Python 3.11+. |
| Local frontend proxy | `node --test tools/tests/proxy.test.cjs` with Node.js 22+. |
| Support guides or agent logic | Follow the Python 3.13 environment and simulated-provider tests in the [support agent guide](support-agent/README.md). |
| Recommendation training | Follow the virtual environment setup and unit tests in [ml/README.md](ml/README.md). Use the separate disposable database procedure if changing model publication. |

For a behavior fix, add or adjust a focused regression test where practical. For a visible UI change, inspect both languages and include screenshots when they help review. Keep translations tied to UI labels and reviewed demo content; user-authored text should remain original.

Live browser, media, model publication and recovery exercises can write data. Use the [isolated integration setup](docs/operations.md#isolated-integration-exercises) when those checks are relevant, keeping model generation disabled. Unit or mock tests do not establish real SMTP/model delivery. The [CI workflow](.github/workflows/ci.yml) defines automated checks; report what actually ran rather than assuming a passing result.

## Open a pull request

Keep each pull request focused and explain the user-visible problem, resulting behavior and related issue if one exists. Use the PR template to record exact checks and their results, plus anything not run and why. Update the relevant documentation when behavior or configuration changes. Add a new Flyway migration for schema changes rather than editing an already applied migration.

Review your diff before submitting. Do not include `.env` files, API keys, passwords, tokens, database backups, private messages or personal user data in code, logs, screenshots, issues or PRs. Use sanitized examples when reporting a problem. Bundled third-party assets must retain their license notices; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
