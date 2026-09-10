package com.campuspulse.content;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class ContentTranslationService {
    private final JdbcTemplate db;
    private final ContentCatalog catalog = new ContentCatalog();

    public ContentTranslationService(JdbcTemplate db) { this.db = db; }

    /** A single batched read for all content identities in a response, including nested references. */
    public Map<Identity, ContentCatalog.Entry> load(Collection<Identity> identities) {
        if (identities.isEmpty()) return Map.of();
        List<Long> ids = identities.stream().map(Identity::id).distinct().toList();
        String marks = String.join(",", Collections.nCopies(ids.size(), "?"));
        Map<Identity, ContentCatalog.Entry> result = new HashMap<>();
        for (var row : db.queryForList("SELECT content_type,content_id,seed_key FROM content_translation_binding WHERE content_id IN (" + marks + ")", ids.toArray())) {
            Identity identity = new Identity((String) row.get("content_type"), ((Number) row.get("content_id")).longValue());
            ContentCatalog.Entry entry = catalog.get(identity.type(), (String) row.get("seed_key"));
            if (entry != null && identities.contains(identity)) result.put(identity, entry);
        }
        return result;
    }

    /** Called only inside first-time demo initialization with its explicitly created seed identities. */
    public void bindSeeds(String type, Map<String, Long> seeds) {
        boolean activity = "ACTIVITY".equals(type);
        if (!activity && !"TEAM".equals(type)) throw new IllegalArgumentException("Unsupported content type");
        String table = activity ? "activities" : "teams";
        String owner = activity ? "organizer_id" : "creator_id";
        for (var seed : seeds.entrySet()) {
            ContentCatalog.Entry entry = catalog.get(type, seed.getKey());
            if (entry == null) throw new IllegalStateException("Seed translation missing: " + seed.getKey());
            String sql = """
                    INSERT IGNORE INTO content_translation_binding(content_type,content_id,seed_key)
                    SELECT ?,c.id,? FROM %s c JOIN users u ON u.id=c.%s
                    WHERE c.id=? AND BINARY c.title=BINARY ? AND BINARY c.description=BINARY ? AND u.username=?
                    %s
                    """.formatted(table, owner, activity ? "AND BINARY c.location=BINARY ?" : "");
            var parameters = new java.util.ArrayList<Object>(List.of(type, seed.getKey(), seed.getValue(),
                    entry.source().get("title"), entry.source().get("description"), entry.owner()));
            if (activity) parameters.add(entry.source().get("location"));
            db.update(sql, parameters.toArray());
        }
    }

    public record Identity(String type, long id) {}
}
