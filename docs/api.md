# API Reference

[Architecture](architecture.md) · [Operations](operations.md) · [中文运行说明](operations.zh-CN.md)

This is a source-oriented guide to the implemented endpoints, not a separate versioned API specification. In development, inspect `/v3/api-docs` or `/api-docs-ui` on the API port for generated schemas. They are disabled in `prod`. Browser requests normally use the frontend proxy at `http://127.0.0.1:8125`.

## Conventions

Business routes begin with `/api`. JSON responses use:

```json
{"success": true, "message": "OK", "data": {}, "code": "OK"}
```

Errors set an HTTP status and a stable status-style `code`, for example:

```json
{"success": false, "message": "Please sign in again.", "data": null, "code": "UNAUTHORIZED"}
```

Typical status codes are `400` for invalid input, `401` for missing/expired identity, `403` for denied access, `404` for missing records, `409` for a conflict or stale state, `410` for a retired endpoint, `413` for upload limits and `429` for request limits. Send `Accept-Language: en` or `zh-CN`; human-readable messages can vary with locale and some stored content remains in its original language. Do not parse localized message strings as machine identifiers. Responses include `X-Request-ID` for log correlation.

Protected routes need `Authorization: Bearer <token>`. An optional valid token personalizes public discovery. User lookup, registration details, messaging, uploads, notifications, telemetry and ticket records require authentication. Administrator routes enforce `ADMIN`; feature services additionally enforce ownership, participation and current state. Anonymous public read access does not make unpublished records or private details public.

Pagination is endpoint-specific. Activities, search and `/teams/page` return `items`, `total`, `page`, `size`; `/teams` returns a list for compatibility. Pages start at 1 and sizes are bounded. Chat uses message-ID cursors, not page numbers. The supplied deployment uses Asia/Shanghai time; business date-time fields are local date-time strings without a timezone offset, so clients must agree on that deployment timezone.

## Authentication and profiles

| Method | Route after `/api` | Behavior / access |
| --- | --- | --- |
| POST | `/auth/login` | Public username/student-number and password login |
| POST | `/auth/email/send` | Send code for `LOGIN`, `REGISTER`, `RESET`; `BIND` requires login |
| POST | `/auth/login-email` | Public email-code login |
| POST | `/auth/register-email` | Public verified registration; creates a `USER` account |
| POST | `/auth/register` | Retired, returns `410`; use verified registration |
| POST | `/auth/password/reset` | Recover account using email code |
| GET | `/auth/me` | Current protected account/profile |
| POST | `/auth/logout`, `/auth/logout-all` | Both revoke all tokens for the current account |
| PUT | `/auth/me/interests` | Replace current user's tag choices |
| POST | `/auth/me/password/email/send`, `/auth/me/password/change-email` | Verified email password-change flow |
| PUT | `/auth/me/email/bind` | Bind verified email |
| POST | `/auth/me/email/change/send-old`, `/auth/me/email/change/send-new` | Codes for current and proposed email |
| PUT | `/auth/me/email/change` | Complete verified email change |
| PUT | `/profile/me`, `/profile/avatar` | Update current user's profile/avatar |
| GET | `/profile/default-avatars` | Public avatar choices |
| GET | `/profile/favorites`, `/profile/registrations`, `/profile/published` | Current user's records |
| GET | `/profile/teams`, `/profile/teams-joined`, `/profile/teams-created` | Current user's team records |
| GET | `/profile/team-chats`, `/profile/activity-chats` | Current user's available chats |
| GET | `/users/search`, `/users/{id}/brief` | Authenticated user discovery; not anonymous access |

For the local demo, a login request is:

```bash
curl --fail-with-body http://127.0.0.1:8125/api/auth/login \
  -H 'Content-Type: application/json' -H 'Accept-Language: en' \
  --data '{"usernameOrStudentNo":"linzhixia","password":"demo12345"}'
```

The response includes `data.token` and `data.user`. Registration first sends `{"email":"your-address@example.org","purpose":"REGISTER"}` to `/auth/email/send`, then submits `username`, `password`, `nickname`, `studentNo`, `college`, `email` and `code` to `/auth/register-email`. Passwords require uppercase, lowercase, a digit and a symbol, with a length of 8–128 characters. Development codes are exposed only with the configured development mode; real accounts require SMTP. There is no implemented SMS verification flow.

## Activities and teams

| Method | Route after `/api` | Behavior / access |
| --- | --- | --- |
| GET | `/activities`, `/activities/highlights`, `/activities/{id}`, `/activities/{id}/teams` | Public discovery with visibility rules |
| POST | `/activities` | Authenticated creation; enters pending review |
| PUT / DELETE | `/activities/{id}` | Owner or administrator; DELETE archives |
| POST | `/activities/{id}/favorite` | Current user; optional `favorited=true/false` sets state |
| POST | `/activities/{id}/register`, `/activities/{id}/cancel-registration` | Current user's registration lifecycle |
| GET | `/activities/{id}/my-registration` | Current user's registration state |
| GET | `/activities/{id}/organizer-contact` | Registration/ownership access checks |
| GET | `/activities/{id}/registrations` | Owner/administrator; accepts `page`, `size` |
| POST | `/activities/{activityId}/registrations/{registrationId}/approve` or `/reject` | Owner/administrator; capacity and state checked |
| GET | `/teams`, `/teams/page`, `/teams/{id}` | Public team discovery |
| POST | `/teams` | Authenticated team creation |
| PUT / DELETE | `/teams/{id}` | Owner/administrator; DELETE archives |
| POST | `/teams/{id}/join`, `/teams/{id}/cancel-request` | Submit/cancel the current user's application |
| GET | `/teams/{id}/requests` | Owner/administrator application review |
| POST | `/teams/{teamId}/requests/{requestId}/approve` or `/reject` | Owner/administrator; capacity and state checked |
| POST | `/teams/{id}/leave` | Leave team; leader must transfer first |
| POST | `/teams/{id}/transfer` | Transfer leadership with `{"userId":123}` |
| POST | `/teams/{id}/close` | Close a team through its management permission checks |
| DELETE | `/teams/{id}/members/{userId}` | Remove member through management permission checks |

Activity/team responses preserve source fields and add optional `translations.en` for display; edits submit the original fields. Reviewed translations are invalidated field by field when source text changes. English demo titles and canonical platform tag names are searchable. See [content localization](../backend/docs/content-localization.md).

Activity queries support `keyword`, `tag`, `category`, `status`, `sort`, `page`, `size`; team queries support `activityId`, `keyword`, `tag`, `page`, `size`. For update requests carrying `version`, send the version read from the detail endpoint to detect a stale edit. Refresh on `409` before resubmitting.

Activity creation uses `title`, `location`, `startTime`, optional `endTime`, `maxParticipants`, optional `coverUrl`, `description`, nonempty `tags` and `teamingEnabled`. A registration request contains `realName`, `phone`, `college`, and optional `intro`. Those personal registration fields are not part of anonymous discovery. Team creation uses `title`, `description`, `maxMembers`, optional `activityId`, `startTime`, `endTime` and `tags`; an application contains `message`.

## Chat, realtime and images

| Method | Route after `/api` | Behavior / access |
| --- | --- | --- |
| GET / POST | `/dm/{peerId}/messages` | Authenticated direct-message history/send |
| GET | `/dm/threads` | Current user's direct conversations |
| POST | `/dm/{peerId}/read` | Advance current user's read cursor |
| GET / POST | `/teams/{teamId}/messages` | Current active member of a live team |
| POST | `/teams/{teamId}/read` | Advance team read cursor |
| POST | `/activities/{id}/chat/enable` | Owner/administrator opens activity chat |
| GET | `/activities/{id}/chat/enabled` | Authenticated chat status |
| GET / POST | `/activities/{id}/chat/messages` | Approved participant, organizer or administrator of an enabled live chat |
| POST | `/activities/{id}/chat/read` | Advance activity read cursor |
| POST | `/realtime/chat/ticket` | Create single-use stream ticket; bearer required |
| GET | `/realtime/chat/stream` | Consume ticket and authorize exactly one chat scope |
| POST | `/upload/avatar`, `/upload/cover`, `/upload/chat` | Authenticated multipart upload with field `file` |
| GET / DELETE | `/upload/files`, `/upload/files/{id}` | List owned uploads / delete an unused owned file |
| GET | `/chat-media/{id}` | Protected image bytes; uploader/conversation access checked |

Send text with `{"content":"Hello","clientMessageId":"unique-message-123","contentType":"TEXT"}`. The client message ID accepts 8–80 letters, digits, underscores or hyphens. Preserve it when retrying a request: an identical retry returns the original ID, while conflicting reuse returns `409`. Without a supplied ID the server generates one, so the client cannot deduplicate an unknown successful attempt.

History requests accept `size` (maximum 100), and either `afterId` or `beforeId`, never both. No cursor returns the latest page. Returned pages are chronological; use the newest ID with `afterId` after reconnecting, or the oldest ID with `beforeId` to load older messages. Read requests carry `{"lastReadId":123}`; updates are bounded and monotonic. Message records include `contentType`, `imageUrl` and `readCount`.

To send an image, upload it to `/upload/chat`, then use the returned `data.url` as `imageUrl` in an `IMAGE` message. The sender must own that upload. Images are validated and converted to PNG; default limits are 5 MiB for avatars and 10 MiB for covers/chat, with additional pixel and per-user storage bounds. Supported upload decoders are PNG, JPEG and GIF; animation and original metadata are not preserved. Fetch chat images with bearer authentication and display the received blob. Public avatar/cover URLs do not provide access to chat files.

A stream-ticket response has `data.ticket` and `data.expiresIn` of 60 seconds. Connect EventSource to `/api/realtime/chat/stream?ticket=...&teamId=...`, or use `activityId` or `userId` (the direct-message peer), with exactly one scope. Obtain a new ticket for a new connection. SSE carries post-commit hints; read database history for reliable recovery. Tickets and connections live in one API process.

## Discovery, notifications, support and administration

| Method | Route after `/api` | Behavior / access |
| --- | --- | --- |
| GET | `/search?keyword=...&type=all` | Public SQL keyword search; `type` is `all`, `activity` or `team` |
| GET | `/tags` | Public tags |
| GET | `/recommendations/feed`, `/recommendations/activities`, `/recommendations/teams` | Public, optionally personalized; bounded `size` |
| POST | `/behavior` | Authenticated client observations; conversion events are rejected |
| GET | `/notifications` | Current user's paginated notices |
| POST | `/notifications/{id}/read`, `/notifications/read-all` | Mark owned notices read |
| DELETE | `/notifications/{id}` | Delete an owned notice |
| GET | `/support/knowledge` | Public local knowledge summary |
| POST | `/support/chat` | Public LangGraph retrieval; generation requires login, configuration and quota |
| GET | `/support/conversations` | Current user’s saved conversations, newest first, at most 50 |
| GET / DELETE | `/support/conversations/{id}` | Read or delete an owned support conversation; another owner returns 404 |
| POST | `/support/escalate`, `/support/tickets` | Authenticated persistent ticket creation |
| GET | `/support/tickets`, `/support/tickets/{id}` | Owner/administrator ticket access |
| POST | `/support/tickets/{id}/replies` | Owner/administrator reply |
| GET / PATCH | `/support/admin/tickets`, `/support/admin/tickets/{id}` | Administrator list/status; `OPEN`, `IN_PROGRESS`, `CLOSED` |
| GET | `/admin/stats`, `/admin/users`, `/admin/users/{id}/detail`, `/admin/activities`, `/admin/audit-log` | Administrator governance views |
| POST | `/admin/users/{id}/status`, `/admin/users/{id}/role` | Administrator account controls; last active admin protected |
| POST | `/admin/activities/{id}/audit`, `/admin/activities/{id}/status` | Administrator activity governance |
| GET / POST | `/admin/tags`, `/admin/featured` | Administrator tag and featured management |
| PUT / DELETE | `/admin/tags/{id}` | Administrator tag edits/deletion, with usage checks |
| DELETE | `/admin/featured/{id}` | Administrator removes featured entry |

Client telemetry supports exposure/click/detail/search observations and is recorded as `CLIENT`. Trusted `REGISTER`, `FAVORITE`, `TEAM_APPLY` and `TEAM_JOIN` outcomes come from business transactions as `SERVER`; client calls cannot manufacture training conversions. Support requests use `message` (at most 500 characters) and optional `conversationId`; language comes from `Accept-Language`. Answers include `source`, `citations`, `suggestEscalation`, `conversationId`, `escalated` and `ticketId`. Chat does not create tickets: `/support/escalate` is an explicit authenticated action. Authenticated conversations retain at most 50 turns, and the graph receives at most six recent messages. Guest questions have no persistent conversation and cannot enable model generation. Ticket replies use `message`. Provider failure falls back to local support. Exact provider delivery, performance under load and public-deployment rate limits require environment-specific validation.
