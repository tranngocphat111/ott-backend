package mediaservice.utils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TextTagParser {

    private static final Pattern MENTION_PATTERN = Pattern.compile("@([A-Za-z0-9_.-]+)");
    private static final Pattern HASHTAG_PATTERN = Pattern.compile("#([A-Za-z0-9_.-]+)");

    private TextTagParser() {}

    public static List<String> extractMentions(String text) {
        if (text == null || text.isBlank()) return List.of();
        Matcher m = MENTION_PATTERN.matcher(text);
        Set<String> found = new LinkedHashSet<>();
        while (m.find()) {
            String name = m.group(1).trim();
            if (!name.isBlank()) found.add(name);
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
