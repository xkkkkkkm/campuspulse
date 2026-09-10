package com.campuspulse.content;

import java.util.Map;

/** Canonical platform labels shared by presentation and search. */
public final class ContentVocabulary {
    private ContentVocabulary() {}
    static final Map<String, String> TAGS = Map.ofEntries(
            Map.entry("运动健身", "Sports & Fitness"), Map.entry("学术讲座", "Academic Talks"),
            Map.entry("文艺演出", "Arts & Performances"), Map.entry("志愿服务", "Volunteering"),
            Map.entry("竞赛组队", "Competition Teams"), Map.entry("兼职实习", "Jobs & Internships"),
            Map.entry("兴趣社交", "Social & Hobbies"), Map.entry("考研搭子", "Study Partners"),
            Map.entry("校园生活", "Campus Life"), Map.entry("其他", "Other"));
    static final Map<String, String> CAMPUSES = Map.of(
            "天赐庄校区", "Tiancizhuang Campus", "独墅湖校区", "Dushu Lake Campus",
            "阳澄湖校区", "Yangcheng Lake Campus", "未来校区", "Future Campus");

    public static String canonicalTag(String value) {
        if (value == null) return null;
        String text = value.trim();
        return TAGS.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(text))
                .map(Map.Entry::getKey).findFirst().orElse(text);
    }
}
