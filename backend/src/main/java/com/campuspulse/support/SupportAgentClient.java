package com.campuspulse.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class SupportAgentClient {
    private final RestTemplate http;
    private final ObjectMapper json;
    private final boolean enabled;
    private final String endpoint, token;

    public SupportAgentClient(RestTemplateBuilder builder, ObjectMapper json,
            @Value("${app.support.agent-enabled:false}") boolean enabled,
            @Value("${app.support.agent-url:http://127.0.0.1:8001}") String baseUrl,
            @Value("${app.support.agent-token:}") String token) {
        this.http = builder.connectTimeout(Duration.ofSeconds(2)).readTimeout(Duration.ofSeconds(20)).build();
        this.json = json; this.enabled = enabled; this.token = token;
        this.endpoint = baseUrl.replaceAll("/+$", "") + "/v1/answer";
        if (enabled && token.length() < 32) throw new IllegalArgumentException("Configure SUPPORT_AGENT_TOKEN with at least 32 random characters");
    }

    public boolean enabled() { return enabled; }

    public SupportAnswer answer(String message, String locale, List<Map<String,String>> history, boolean allowGeneration) {
        if (!enabled) throw new IllegalStateException("Support agent disabled");
        SupportAnswer answer = http.execute(endpoint, HttpMethod.POST, request -> {
            request.getHeaders().setBearerAuth(token);
            request.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            json.writeValue(request.getBody(), Map.of("message", message, "locale", locale, "history", history, "allowGeneration", allowGeneration));
        }, response -> {
            if (!response.getStatusCode().is2xxSuccessful()) throw new IllegalStateException("Support agent unavailable");
            byte[] bytes = response.getBody().readNBytes(32769);
            if (bytes.length > 32768) throw new IllegalStateException("Support answer exceeds limit");
            return json.readValue(bytes, SupportAnswer.class);
        });
        if (answer == null || answer.answer() == null || answer.answer().isBlank() || answer.answer().length() > 4000
                || answer.source() == null || !Set.of("LANGGRAPH_RETRIEVAL", "LANGGRAPH_LLM").contains(answer.source())
                || answer.citations() == null || answer.citations().size() > 3)
            throw new IllegalStateException("Invalid support answer");
        for (var citation : answer.citations()) {
            if (citation == null || citation.id() == null || !citation.id().matches("[a-z0-9_-]{1,80}")
                    || citation.title() == null || citation.title().length() > 200 || citation.url() == null
                    || !citation.url().matches("/[a-z-]+\\.html(?:#[a-z0-9_-]+)?"))
                throw new IllegalStateException("Invalid support citation");
        }
        if (!allowGeneration && "LANGGRAPH_LLM".equals(answer.source())) throw new IllegalStateException("Unexpected model generation");
        return answer;
    }
}
