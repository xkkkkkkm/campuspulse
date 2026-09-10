package com.campuspulse.content;

import com.campuspulse.common.Api;
import org.springframework.http.HttpStatus;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bounded SQL predicates for searching the same reviewed translations that the API displays. */
public final class ContentSearch {
    private static final ContentCatalog CATALOG = new ContentCatalog();
    private ContentSearch() {}

    public static Predicate activities(String keyword) {
        return build(keyword, "ACTIVITY", "a", List.of("title", "description", "location"));
    }

    public static Predicate teams(String keyword) {
        return build(keyword, "TEAM", "t", List.of("title", "description"));
    }

    public static Predicate activityTitles(String keyword) {
        return build(keyword, "ACTIVITY", "a", List.of("title"));
    }

    public static Predicate tags(String keyword) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 200) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "关键词过长");
        List<Object> parameters = new ArrayList<>();
        parameters.add("%" + (keyword == null ? "" : keyword.trim()) + "%");
        if (normalized.isEmpty()) return new Predicate("tg.name LIKE ?", parameters);
        var names = ContentVocabulary.TAGS.entrySet().stream()
                .filter(entry -> entry.getValue().toLowerCase(Locale.ROOT).contains(normalized))
                .map(java.util.Map.Entry::getKey).sorted().toList();
        if (names.isEmpty()) return new Predicate("tg.name LIKE ?", parameters);
        parameters.addAll(names);
        return new Predicate("(tg.name LIKE ? OR tg.name IN (" + String.join(",", java.util.Collections.nCopies(names.size(), "?")) + "))", parameters);
    }

    private static Predicate build(String keyword, String type, String alias, List<String> fields) {
        String normalized = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > 200) throw new Api.ApiException(HttpStatus.BAD_REQUEST, "关键词过长");
        if (normalized.isEmpty()) return new Predicate("1=0", List.of());
        List<String> alternatives = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        parameters.add(type);
        for (ContentCatalog.Entry entry : CATALOG.entries()) {
            if (!type.equals(entry.type())) continue;
            List<String> matchingFields = fields.stream()
                    .filter(field -> entry.en().get(field).toLowerCase(Locale.ROOT).contains(normalized)).toList();
            if (matchingFields.isEmpty()) continue;
            parameters.add(entry.key());
            List<String> sourceChecks = new ArrayList<>();
            for (String field : matchingFields) {
                // Alias and field are internal constants. All catalog values use bound parameters.
                sourceChecks.add("BINARY " + alias + "." + field + "=BINARY ?");
                parameters.add(entry.source().get(field));
            }
            alternatives.add("(ctb.seed_key=? AND (" + String.join(" OR ", sourceChecks) + "))");
        }
        if (alternatives.isEmpty()) return new Predicate("1=0", List.of());
        // This source check runs in the caller's SELECT/count query, avoiding a read/check race.
        String sql = "EXISTS (SELECT 1 FROM content_translation_binding ctb WHERE ctb.content_type=?"
                + " AND ctb.content_id=" + alias + ".id AND (" + String.join(" OR ", alternatives) + "))";
        return new Predicate(sql, parameters);
    }

    public record Predicate(String sql, List<Object> parameters) {
        public Predicate { parameters = List.copyOf(parameters); }
    }
}
