package com.campuspulse.content;

import com.campuspulse.common.Api;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ContentTranslationAdviceTest {
    private final ContentCatalog catalog = new ContentCatalog();
    private final ContentTranslationService service = mock(ContentTranslationService.class);
    private final ContentTranslationAdvice advice = new ContentTranslationAdvice(new ObjectMapper(), service);
    private MockMvc mvc;

    @BeforeEach void setup() {
        when(service.load(anyCollection())).thenReturn(Map.of(
                new ContentTranslationService.Identity("ACTIVITY", 11), catalog.get("ACTIVITY", "ai"),
                new ContentTranslationService.Identity("TEAM", 11), catalog.get("TEAM", "ai_team")));
        mvc = MockMvcBuilders.standaloneSetup(new FixtureController()).setControllerAdvice(advice).build();
    }

    @Test void bothLocalesReceiveStableOriginalAndReviewedEnglish() throws Exception {
        for (String locale : List.of("zh-CN", "en-US")) {
            mvc.perform(get("/content-fixture").header("Accept-Language", locale))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(11))
                    .andExpect(jsonPath("$.data.title").value("人工智能与未来社会"))
                    .andExpect(jsonPath("$.data.translations.en.title").value("AI and the Future of Society"))
                    .andExpect(jsonPath("$.data.translations.en.location").value("Library Auditorium, Dushu Lake Campus"))
                    .andExpect(jsonPath("$.data.tags[0]").value("学术讲座"))
                    .andExpect(jsonPath("$.data.translations.en.tags[0]").value("Academic Talks"));
        }
    }

    @Test void editingOneSourceFieldInvalidatesOnlyItsTranslation() {
        var edited = new Activity(11, "用户自己的标题", catalog.get("ACTIVITY", "ai").source().get("description"),
                "用户修改的地点", List.of());
        var json = advice.enrich(Api.ok(edited)).path("data");
        assertEquals("用户自己的标题", json.path("title").asText());
        assertFalse(json.path("translations").path("en").has("title"));
        assertFalse(json.path("translations").path("en").has("location"));
        assertEquals(catalog.get("ACTIVITY", "ai").en().get("description"), json.path("translations").path("en").path("description").asText());
    }

    @Test void identicalUserTitleAndWrongContentTypeDoNotBorrowSeedTranslations() {
        var seed = catalog.get("ACTIVITY", "ai").source();
        var userContent = new Activity(99, seed.get("title"), seed.get("description"), seed.get("location"), List.of());
        assertFalse(advice.enrich(userContent).has("translations"));
        var wrongType = new Team(11, null, null, seed.get("title"), seed.get("description"));
        assertFalse(advice.enrich(wrongType).has("translations"));
    }

    @Test void nestedReferencesUseActivityIdentityAndUserFieldsRemainUntouched() {
        var entry = catalog.get("TEAM", "ai_team");
        var team = new Team(11, 11L, "人工智能与未来社会", entry.source().get("title"), entry.source().get("description"));
        var json = advice.enrich(Api.ok(Map.of("items", List.of(team), "message", "人工智能与未来社会"))).path("data");
        assertEquals("人工智能与未来社会", json.path("message").asText());
        assertEquals("AI and the Future of Society", json.path("items").get(0).path("translations").path("en").path("activityTitle").asText());
        assertEquals(entry.en().get("title"), json.path("items").get(0).path("translations").path("en").path("title").asText());
    }

    @Test void vocabularyPreservesTagIdsUnknownTagsAndCanonicalCampus() {
        var json = advice.enrich(Map.of("tag", Map.of("id", 7, "type", "HOME", "name", "运动健身"),
                "profile", Map.of("campus", "独墅湖校区", "tags", List.of("竞赛组队", "自定义标签"))));
        assertEquals(7, json.path("tag").path("id").asInt());
        assertEquals("运动健身", json.path("tag").path("name").asText());
        assertEquals("Sports & Fitness", json.path("tag").path("translations").path("en").path("name").asText());
        assertEquals("独墅湖校区", json.path("profile").path("campus").asText());
        assertEquals("Dushu Lake Campus", json.path("profile").path("translations").path("en").path("campus").asText());
        assertEquals("自定义标签", json.path("profile").path("translations").path("en").path("tags").get(1).asText());
    }

    @Test void completeCatalogHasNoMissingEnglishFields() {
        assertEquals(40, catalog.entries().stream().filter(entry -> entry.type().equals("ACTIVITY")).count());
        assertEquals(33, catalog.entries().stream().filter(entry -> entry.type().equals("TEAM")).count());
        for (var entry : catalog.entries()) {
            assertEquals(entry.source().keySet(), entry.en().keySet());
            entry.en().values().forEach(value -> {
                assertFalse(value.isBlank());
                assertFalse(value.codePoints().anyMatch(cp -> Character.UnicodeScript.of(cp) == Character.UnicodeScript.HAN), entry.key());
            });
        }
    }

    @ContentEntity("ACTIVITY")
    public record Activity(long id, String title, String description, String location, List<String> tags) {}
    @ContentEntity("TEAM")
    public record Team(long id, Long activityId, String activityTitle, String title, String description) {}
    @RestController
    public static class FixtureController {
        @GetMapping("/content-fixture") public Api.ApiResponse<Activity> fixture() {
            var source = new ContentCatalog().get("ACTIVITY", "ai").source();
            return Api.ok(new Activity(11, source.get("title"), source.get("description"), source.get("location"), List.of("学术讲座")));
        }
    }
}
