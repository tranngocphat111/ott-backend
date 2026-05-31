package mediaservice.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTagParser {

    // Supports both @[displayName](userId) and @username formats
    private static final Pattern MENTION_PATTERN = Pattern.compile("@\\[.*?\\]\\(([A-Za-z0-9_-]+)\\)|@([A-Za-z0-9_.-]+)");
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([A-Za-z0-9_.-]+)");

    private TextTagParser() {}

    public static List<String> extractMentions(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher m = MENTION_PATTERN.matcher(text);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            // Group 1 is for @[name](id), Group 2 is for @username
            String target = m.group(1) != null ? m.group(1).trim() : (m.group(2) != null ? m.group(2).trim() : null);
            if (target != null && !target.isBlank()) found.add(target);
        }
        return new ArrayList<>(found);
    }

    public static List<String> extractHashTags(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher m = HASHTAG_PATTERN.matcher(text);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            String tag = m.group(1).trim().toLowerCase();
            if (!tag.isBlank()) found.add(tag);
        }
        return new ArrayList<>(found);
    }
}
