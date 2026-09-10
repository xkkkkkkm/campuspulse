package com.campuspulse.content;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable, reviewed translations of the original demo fixtures. Never match arbitrary text. */
public final class ContentCatalog {
    public static final String RESOURCE = "/content/demo-translations-v1.json";
    private final Map<String, Entry> entries;

    public ContentCatalog() { this(true); }

    /** Frozen V7 migration uses the original catalog; current presentation applies versioned patches. */
    public ContentCatalog(boolean current) {
        try (var input = ContentCatalog.class.getResourceAsStream(RESOURCE)) {
            if (input == null) throw new IllegalStateException("Content translation catalog is missing");
            List<Entry> source = new ObjectMapper().readValue(input, new TypeReference<>() {});
            Map<String, Entry> indexed = new LinkedHashMap<>();
            for (Entry entry : source) {
                if (!List.of("ACTIVITY", "TEAM").contains(entry.type())
                        || !entry.source().keySet().equals(entry.en().keySet())
                        || entry.en().values().stream().anyMatch(value -> value == null || value.isBlank())
                        || indexed.put(entry.type() + ":" + entry.key(), entry) != null)
                    throw new IllegalStateException("Invalid content translation: " + entry.key());
            }
            if (current) {
                try (var patches = ContentCatalog.class.getResourceAsStream("/content/demo-translations-v2.json")) {
                    if (patches == null) throw new IllegalStateException("Content translation patch is missing");
                    List<Entry> updates = new ObjectMapper().readValue(patches, new TypeReference<>() {});
                    for (Entry update : updates) {
                        String key = update.type() + ":" + update.key();
                        if (!indexed.containsKey(key) || !update.source().keySet().equals(update.en().keySet()))
                            throw new IllegalStateException("Invalid content translation patch: " + key);
                        indexed.put(key, update);
                    }
                }
            }
            entries = Map.copyOf(indexed);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read content translation catalog", e);
        }
    }

    public Entry get(String type, String key) { return entries.get(type + ":" + key); }
    public List<Entry> entries() { return List.copyOf(entries.values()); }

    public record Entry(String type, String key, String owner, Map<String, String> source, Map<String, String> en) {
        public Entry { source = Map.copyOf(source); en = Map.copyOf(en); }
        public String translate(String field, String currentValue) {
            return currentValue != null && currentValue.equals(source.get(field)) ? en.get(field) : null;
        }
    }
}
