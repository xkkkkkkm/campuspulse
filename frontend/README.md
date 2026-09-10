# CampusPulse frontend

A native ES module application with nineteen HTML entry pages. No framework build step is required to serve the application.

```bash
cd frontend
npm ci
npm run dev
```

The development server is `tools/local_http_proxy.cjs` in the repository root. `PORT`, `HOST`, and `TARGET` configure its listener and backend URL. See the root README for the full Docker stack.

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

## Localization

The language selector persists `zh-CN` or `en-US` in browser storage. Chinese browser locales default to Chinese; other locales default to English. The client sends `Accept-Language` and formats dates with `Intl.DateTimeFormat`.

`core/messages.js` contains the English catalog; source-language keys provide Chinese. Static HTML uses `data-i18n` and attribute-specific markers. JavaScript uses `t(key)` for labels and the `ui` template tag for static template segments. Interpolated names, descriptions, messages, custom tags, and other user data are never passed through text translation. Do not translate whole rendered DOM trees or run user content through `translateStatic`.

Use `npm run check` after adding labels: it verifies module imports, CSP-compatible HTML, and every marked translation key. Demo content originally entered in Chinese remains Chinese when the UI language changes.

## Verification

```bash
npm run check
npm test
# On Linux/CI, install the browser once:
npx playwright install --with-deps chromium
npm run e2e
```

The regular Playwright tests use deterministic API fixtures while exercising actual pages in a browser. They cover language persistence, more than fifty activities, paged saved records, post-registration avatar upload, chat history/catchup, idempotent send retries, private image fetching, team lifecycle actions, and safe navigation/dialog keyboard behavior. Node tests cover request cancellation/timeouts, URL rejection, stale search responses, message merging, and renewed SSE tickets.

For integration tests against an **isolated demo stack**:

```bash
E2E_BASE_URL=http://127.0.0.1:18125 npm run e2e:live
```

Live tests sign in through the page, load the main routes, and create a support ticket that an administrator replies to and closes. They require demo credentials, or `E2E_USERNAME`, `E2E_PASSWORD`, `E2E_ADMIN_USERNAME`, and `E2E_ADMIN_PASSWORD`. These tests create persistent test tickets; use a test database. They are skipped in the regular mock suite. Passing mock tests does not establish database or SMTP integration.

## Assets

Font Awesome Free 6.7.2 is vendored in `assets/vendor/fontawesome/` with its original `LICENSE.txt`; pages need no icon CDN. The CampusPulse and fallback avatar SVGs are project-native. Remote or missing profile images fall back to the local avatar. Original activity and profile text is not translated automatically.
