package com.campuspulse.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.MockServerRestTemplateCustomizer;
import org.springframework.http.MediaType;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SupportAgentClientTest {
    private static final String TOKEN = "test-support-agent-token-for-local-tests-only";
    @Test void internalRequestCarriesBoundedContextAndTokenButNoUserIdentity() {
        var customizer = new MockServerRestTemplateCustomizer();
        var builder = new RestTemplateBuilder(customizer);
        var client = new SupportAgentClient(builder, new ObjectMapper(), true, "http://support-agent:8001", TOKEN);
        var server = customizer.getServer();
        server.expect(requestTo("http://support-agent:8001/v1/answer")).andExpect(header("Authorization", "Bearer " + TOKEN))
                .andExpect(content().json("{\"message\":\"Join a team\",\"locale\":\"en-US\",\"history\":[],\"allowGeneration\":false}", true))
                .andRespond(withSuccess("{\"answer\":\"Open Teams.\",\"source\":\"LANGGRAPH_RETRIEVAL\",\"citations\":[{\"id\":\"teams\",\"title\":\"Teams\",\"url\":\"/team-lobby.html\"}],\"suggestEscalation\":false}", MediaType.APPLICATION_JSON));
        assertEquals("LANGGRAPH_RETRIEVAL", client.answer("Join a team", "en-US", List.of(), false).source()); server.verify();
    }
    @Test void untrustedCitationAndUnauthorizedGenerationAreRejected() {
        for (String response : List.of(
                "{\"answer\":\"bad\",\"source\":\"LANGGRAPH_RETRIEVAL\",\"citations\":[{\"id\":\"x\",\"title\":\"x\",\"url\":\"https://evil.example/\"}],\"suggestEscalation\":false}",
                "{\"answer\":\"bad\",\"source\":\"LANGGRAPH_LLM\",\"citations\":[],\"suggestEscalation\":false}")) {
            var customizer = new MockServerRestTemplateCustomizer();
            var client = new SupportAgentClient(new RestTemplateBuilder(customizer), new ObjectMapper(), true, "http://support-agent:8001", TOKEN);
            var server = customizer.getServer();
            server.expect(anything()).andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
            assertThrows(IllegalStateException.class, () -> client.answer("test", "en-US", List.of(), false)); server.verify();
        }
    }
}
