package com.campuspulse.content;

import com.campuspulse.common.Api;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/** Adds optional translations while preserving every source field for editing and filtering. */
@RestControllerAdvice
public class ContentTranslationAdvice implements ResponseBodyAdvice<Object> {
    private final ObjectMapper mapper;
    private final ContentTranslationService translations;

    public ContentTranslationAdvice(ObjectMapper mapper, ContentTranslationService translations) {
        this.mapper = mapper;
        this.translations = translations;
    }

    @Override public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return MappingJackson2HttpMessageConverter.class.isAssignableFrom(converterType);
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType contentType,
                                            Class<? extends HttpMessageConverter<?>> converterType,
                                            ServerHttpRequest request, ServerHttpResponse response) {
        if (!(body instanceof Api.ApiResponse<?> envelope) || !envelope.success() || envelope.data() == null) return body;
        return enrich(body);
    }

    JsonNode enrich(Object body) {
        JsonNode tree = mapper.valueToTree(body);
        List<Target> targets = new ArrayList<>();
        collect(body, tree, targets);
        Collection<ContentTranslationService.Identity> identities = new LinkedHashSet<>();
        targets.forEach(target -> identities.add(target.identity()));
        Map<ContentTranslationService.Identity, ContentCatalog.Entry> loaded = translations.load(identities);
        for (Target target : targets) {
            ContentCatalog.Entry entry = loaded.get(target.identity());
            if (entry == null) continue;
            for (String field : target.fields()) {
                JsonNode current = target.node().get(field);
                if (current == null || !current.isTextual()) continue;
                String translated = entry.translate("activityTitle".equals(field) ? "title" : field, current.textValue());
                if (translated != null) english(target.node()).put(field, translated);
            }
        }
        return tree;
    }

    private void collect(Object source, JsonNode tree, List<Target> targets) {
        if (source == null || tree == null) return;
        if (tree instanceof ObjectNode node) {
            ContentEntity entity = source.getClass().getAnnotation(ContentEntity.class);
            if (entity != null && node.path("id").isIntegralNumber()) {
                String type = entity.value().isBlank() ? node.path("targetType").asText() : entity.value();
                if ("ACTIVITY".equals(type) || "TEAM".equals(type))
                    targets.add(new Target(node, new ContentTranslationService.Identity(type, node.get("id").longValue()),
                            List.of("title", "description", "location")));
            }
            if (node.path("activityId").isIntegralNumber() && node.path("activityTitle").isTextual())
                targets.add(new Target(node, new ContentTranslationService.Identity("ACTIVITY", node.get("activityId").longValue()), List.of("activityTitle")));
            addVocabulary(node);
            if (source.getClass().isRecord()) {
                for (var component : source.getClass().getRecordComponents()) {
                    try { collect(component.getAccessor().invoke(source), node.get(component.getName()), targets); }
                    catch (IllegalAccessException | InvocationTargetException e) { throw new IllegalStateException("Cannot inspect API record", e); }
                }
            } else if (source instanceof Map<?, ?> map) {
                map.forEach((key, value) -> collect(value, node.get(String.valueOf(key)), targets));
            }
        } else if (tree.isArray() && source instanceof Iterable<?> values) {
            int index = 0;
            for (Object value : values) collect(value, tree.get(index++), targets);
        }
    }

    private void addVocabulary(ObjectNode node) {
        if (node.path("name").isTextual() && node.has("id")
                && List.of("HOME", "OTHER").contains(node.path("type").asText())) {
            String value = ContentVocabulary.TAGS.get(node.get("name").textValue());
            if (value != null) english(node).put("name", value);
        }
        String campus = ContentVocabulary.CAMPUSES.get(node.path("campus").asText(""));
        if (campus != null) english(node).put("campus", campus);
        for (String field : List.of("tags", "interests")) {
            JsonNode values = node.get(field);
            if (values == null || !values.isArray() || values.isEmpty()) continue;
            boolean known = false;
            List<String> localized = new ArrayList<>();
            for (JsonNode value : values) {
                if (!value.isTextual()) { localized.clear(); break; }
                String original = value.textValue();
                known |= ContentVocabulary.TAGS.containsKey(original);
                localized.add(ContentVocabulary.TAGS.getOrDefault(original, original));
            }
            if (known && localized.size() == values.size()) english(node).set(field, mapper.valueToTree(localized));
        }
    }

    private ObjectNode english(ObjectNode node) {
        ObjectNode container = node.has("translations") ? (ObjectNode) node.get("translations") : node.putObject("translations");
        return container.has("en") ? (ObjectNode) container.get("en") : container.putObject("en");
    }

    private record Target(ObjectNode node, ContentTranslationService.Identity identity, List<String> fields) {}
}
