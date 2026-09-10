# Content translations

All API source fields remain canonical. Display an English field using
`item.translations?.en?.title ?? item.title` when the UI locale is English.
Use `item.title`, `item.description`, `item.location`, tag IDs and original tag
names for editing, form submission and filtering. Metadata is returned for both
English and Chinese requests, so switching locale does not require a refetch.

For example:

```json
{
  "id": 11,
  "title": "人工智能与未来社会",
  "location": "独墅湖校区图书馆报告厅",
  "tags": ["学术讲座", "校园生活"],
  "translations": {
    "en": {
      "title": "AI and the Future of Society",
      "location": "Library Auditorium, Dushu Lake Campus",
      "tags": ["Academic Talks", "Campus Life"]
    }
  }
}
```

`ContentEntity` marks activity and team response records. The advice enriches
lists, details, highlights, recommendations, search results, activity team lists,
profile favorites/registrations/teams/chats, and administrator content records.
Nested `activityId` / `activityTitle` pairs receive `translations.en.activityTitle`.
Standard tag objects receive `translations.en.name`; standard campus values
receive `translations.en.campus`. Tag and interest arrays retain their order,
with unknown values retained verbatim. Generated cover text can use the same
translated title and location. Custom images are not rewritten.

The immutable `content/demo-translations-v1.json` contains the complete original
40 activity and 33 team fixtures, their owners, and reviewed English translations.
V7 creates only a content identity binding table. On an existing database it
requires an exact title, description, location (activities), original owner,
and creation before completed demo initialization. Ambiguous matches are skipped.
It never updates activity, team, tag, user or initialization rows. First-time demo
initialization binds its explicit seed keys and newly created IDs; a completed
initialization remains a no-op.

At response time a translation requires both the bound content type/ID and an
exact match against that field's source text. A user edit therefore invalidates
only the affected translated field. Unbound user content and missing translations
retain their source text. Binding lookup is batched once per API response.

V7 includes the catalog resource in its Flyway checksum. Treat that resource as
an immutable migration input: extend catalogs through a new versioned migration
and resource. Previously applied migrations V1–V6 are unchanged.

Verification is in `ContentTranslationAdviceTest` and
`ContentTranslationMigrationIT`. The integration tests cover V6 upgrades without
rewriting records, excluding later user copies, all 73 fresh demo bindings, and
preserving edits across initialization retries.

Activity and team lobby keyword filters and `/api/search` accept the displayed
English title, description, and activity location as well as source text. Team
search also accepts the linked activity's English title. Catalog matching is
case-insensitive and literal; query values are always bound parameters. The SQL
predicate checks the content identity and current source field in the same
SELECT used for filtering/counting, so stale translations do not match after an
edit. The finite catalog bounds the number of predicates independently of user
input. Existing visibility, status, tag filters, ordering, and pagination apply
to both languages. Search tests are in `ContentSearchTest` and
`ContentTranslationMigrationIT`.

## Versioned support-demo update

The V7 catalog remains immutable in `demo-translations-v1.json`. Current presentation also loads `demo-translations-v2.json`; V9 updates only the two unchanged, provenance-bound support-team descriptions from Dify to LangGraph. Edited descriptions are preserved and receive no stale translation. English canonical tag matching applies to user-created content as well as demo records.
