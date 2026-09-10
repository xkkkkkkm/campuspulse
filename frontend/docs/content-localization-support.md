# Content localization and support UI

Content objects retain their original `title`, `description`, `location`, `tags`, and related activity names. The backend supplies trusted display text in `translations.en`; `core/content-i18n.js` selects it at rendering time according to the persisted interface language. It also maps the platform's standard tag and campus values. Unknown titles, descriptions, tags, messages, names, and uploaded image contents remain as authored.

Cards, generated SVG posters, details, team dialogs, home search results, personal records, chat names and management summaries read localized display fields. Editing inputs and saved request bodies continue using originals, including canonical Chinese tag values. Changing the language reloads the page so content and generated posters follow the selected language. Uploaded covers retain their original URL.

Signed-in users load their server conversation list and automatically restore the most recent conversation on re-entry. A history selector opens any existing conversation or starts a new one, and the refresh button recovers from list-loading failures. The list is tied to the authenticated account; changing the account reloads the page, and no locally stored conversation ID is reused. Guests use the assistant without a history selector.

Support sends `Accept-Language` with each API request and retains `conversationId` for subsequent questions and a user-requested escalation. Replies display their answer, user-facing source label, optional citations, and an optional human-assistance button. Suggestions never create a ticket by themselves. References accept only HTTP(S) URLs or absolute site paths, escape their labels, and open with `noopener noreferrer`.

Requests are serialized to keep conversation order. Network failure restores the question as a retry draft; the UI does not print internal backend error details. Clearing the conversation aborts pending requests and deletes a known conversation through `DELETE /support/conversations/{id}`. Success or a 404 removes that conversation and selects the next available history, or a new conversation if none remain; other failures retain the ID for retry. Late replies and restored history are ignored when an operation is superseded or the account changes. It starts a fresh conversation; existing support tickets remain available separately.

Verification:

```sh
npm run check
npm test
npm run e2e -- ui.spec.mjs content-support.spec.mjs
```

The browser suite mocks every API request and does not write to a running application database. Content tests cover both locales, trusted translation versus original author text, safe cover generation, canonical tag filtering, unchanged edit/save payloads, team references, support conversation continuity, safe citations, explicit escalation, retry and stale-response suppression after clearing.
