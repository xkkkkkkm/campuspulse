package com.campuspulse.support;

import java.util.List;

public record SupportAnswer(String answer, String source, List<Citation> citations, boolean suggestEscalation) {
    public record Citation(String id, String title, String url) {}
}
