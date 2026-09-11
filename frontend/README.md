# CampusPulse frontend

A native ES module application with nineteen HTML entry pages. No framework build step is required to serve the application.

## Run

For the complete application, follow the [Docker setup](../docs/operations.md#local-container-demo). For a native frontend, install Node.js 22+ and Python 3.11+, and start MySQL and the API using the [backend instructions](../backend/README.md#run). In a second terminal, run from the repository root:

```bash
python3 tools/dev.py frontend
```

Open [http://127.0.0.1:8125](http://127.0.0.1:8125), or the `FRONTEND_PORT` configured in `.env`. The helper reads the root `.env`, maps `FRONTEND_PORT` to the listener port, and uses `BACKEND_PORT` for the API target. Existing process environment variables take precedence. See [operations](../docs/operations.md) for Windows Python commands and port configuration.

The development server is `tools/local_http_proxy.cjs` in the repository root. It needs no npm dependencies to serve pages. Running `npm run dev` from `frontend/` starts this same frontend proxy only: it does not start the API or read `.env`. For that direct command, set `PORT`, `HOST`, and `TARGET` in the process environment; defaults are `8125`, `127.0.0.1`, and `http://127.0.0.1:8080`.

## Module boundaries

- `assets/js/script.js` loads the current page module and reports initialization errors.
- `core/api.js`, `auth.js`, and `navigation.js` handle requests, session state, timeouts/cancellation, uploads, private media, and allowed return paths.
- `core/pagination.js` and `array-pagination.js` handle server pages and discard superseded requests.
- `core/realtime.js` renews each one-use SSE ticket, retries with exponential backoff, and polls while a connection is unavailable. `chat-state.js` merges messages by ID and preserves send identifiers for retries.
- `core/ui.js`, `render.js`, and `accessibility.js` provide shared cards, dialogs, labels, focus trapping, and keyboard controls.
- `core/support-tickets.js`, `team-lifecycle.js`, and `admin-tools.js` provide their corresponding workflows.
- `pages/` contains one initializer per page; the publishing cover picker is a separate module.

Lists use backend pagination. Profile drawers show a preview and link to paginated records; a `100+` overview count means at least 100 records. Array endpoints expose a next page when the current batch is full; the final next page can be empty when the total is an exact multiple of the page size. The server remains the authority for capacity, ownership, membership, and state changes.

Chat opens the latest fifty messages, loads older history on demand, and drains all newer pages after reconnecting. Retrying the same text or image send reuses `clientMessageId`. Images are authenticated downloads converted into temporary browser blob URLs. Read counts are refreshed while the conversation is open. Group read cursors advance only when the page is visible and scrolled to the latest messages.

On the support page, signed-in users can restore a saved conversation after navigating away, select an older conversation in **Conversation history / 历史会话**, start a new conversation, or clear the selected conversation. Clearing deletes that conversation from the server; submitting a human support request is a separate explicit action.

## Localization

The **中文 / EN** language buttons persist `zh-CN` or `en-US` in browser storage. Chinese browser locales default to Chinese; other locales default to English. The client sends `Accept-Language` and formats dates with `Intl.DateTimeFormat`.

`core/messages.js` contains the English catalog; source-language keys provide Chinese. Static HTML uses `data-i18n` and attribute-specific markers. JavaScript uses `t(key)` for labels and the `ui` template tag for static template segments. Interpolated names, descriptions, messages, custom tags, and other user data are never passed through text translation. Do not translate whole rendered DOM trees or run user content through `translateStatic`.

`core/content-i18n.js` uses trusted API display translations for the 73 catalogued demo records (40 activities and 33 teams), including titles, descriptions, locations and linked activity references. Standard categories, tags and campuses also have English labels, and generated covers use the displayed title. Switching back to Chinese restores the source text. Editing still uses original fields; new user content and fields changed from their reviewed source remain in their original language.

Use `npm run check` from `frontend/` after adding labels: it verifies module imports, CSP-compatible HTML, and every marked translation key.

## Verification

With Node.js 22+ installed, run from the repository root:

```bash
cd frontend
npm ci
npm run check
npm test
# Install Chromium once; on Linux/CI add --with-deps for system libraries.
npx playwright install chromium
npm run e2e
```

The regular Playwright tests start a local frontend and use deterministic API fixtures while exercising actual pages in a browser. They cover language persistence and demo translations, more than fifty activities, paged saved records, post-registration avatar upload, chat history/catchup, idempotent send retries, private image fetching, team lifecycle actions, support citations and saved conversations, and safe navigation/dialog keyboard behavior. Node tests cover request cancellation/timeouts, URL rejection, stale search responses, message merging, and renewed SSE tickets.

For live tests, first start an **isolated demo stack** using [the integration setup](../docs/operations.md#isolated-integration-exercises). Keep `SUPPORT_LLM_ENABLED=false` in that stack's environment and apply it to the backend and support service before testing. The signed-in support test can call a configured real model when generation is enabled. Do not run this suite against production. With the npm dependencies and browser installed above, run from `frontend/` using its actual frontend URL (the linked example uses port 28125):

```bash
E2E_BASE_URL=http://127.0.0.1:28125 npm run e2e:live
```

That command uses POSIX shell syntax. In PowerShell, run from `frontend/` instead:

```powershell
$env:E2E_BASE_URL = 'http://127.0.0.1:28125'
$env:E2E_LIVE = '1'
npx playwright test live.spec.mjs
Remove-Item Env:E2E_LIVE, Env:E2E_BASE_URL
```

The four live cases cover browser sign-in and main routes, an administrator replying to and closing a support ticket, demo content and generated-cover language switching, and cited support answers with conversation restoration and deletion. They require the translated demo fixtures and student/admin accounts; defaults are `linzhixia / demo12345` and `admin / admin123`. Override credentials with `E2E_USERNAME`, `E2E_PASSWORD`, `E2E_ADMIN_USERNAME`, and `E2E_ADMIN_PASSWORD` if needed. The ticket test creates a persistent ticket; the conversation test deletes its test conversation. Live cases are skipped in the regular mock suite. Passing mock tests does not establish database or SMTP integration; see the [validation record](../docs/remediation-status.md) for completed runs.

## Assets

Font Awesome Free 6.7.2 is vendored in `assets/vendor/fontawesome/` with its original `LICENSE.txt`; pages need no icon CDN. The CampusPulse and fallback avatar SVGs are project-native. Remote or missing profile images fall back to the local avatar. Uploaded image contents and user-authored profile text are not translated automatically.
