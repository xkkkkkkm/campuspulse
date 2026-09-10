package com.campuspulse.content;

import com.campuspulse.common.Api;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContentSearchTest {
    @Test void reviewedTitleMatchUsesIdentityAndExactSourceInBoundParameters() {
        var predicate = ContentSearch.activities("  AI AND THE FUTURE OF SOCIETY  ");
        assertTrue(predicate.sql().contains("ctb.content_id=a.id"));
        assertTrue(predicate.sql().contains("BINARY a.title=BINARY ?"));
        assertFalse(predicate.sql().contains("人工智能"));
        assertEquals(java.util.List.of("ACTIVITY", "ai", "人工智能与未来社会"), predicate.parameters());
    }

    @Test void unmatchedMaliciousOrWildcardKeywordsNeverEnterGeneratedSql() {
        for (String value : java.util.List.of("' OR 1=1 --", "%", "_", "", "unknown-long-title")) {
            var predicate = ContentSearch.activities(value);
            assertEquals("1=0", predicate.sql());
            assertTrue(predicate.parameters().isEmpty());
        }
    }

    @Test void broadCatalogMatchesAreBoundedAndOversizedKeywordsAreRejected() {
        var activities = ContentSearch.activities("a");
        var teams = ContentSearch.teams("a");
        assertTrue(activities.parameters().size() <= 1 + 40 * 4);
        assertTrue(teams.parameters().size() <= 1 + 33 * 3);
        assertEquals(activities.parameters().size(), activities.sql().chars().filter(value -> value == '?').count());
        assertEquals(teams.parameters().size(), teams.sql().chars().filter(value -> value == '?').count());
        assertThrows(Api.ApiException.class, () -> ContentSearch.activities("x".repeat(201)));
        assertThrows(Api.ApiException.class, () -> ContentSearch.teams("x".repeat(201)));
    }
}
