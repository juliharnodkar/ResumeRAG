package com.example.resumerag;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Drops generated sentences that introduce facts not present in the supplied
 * resume (and optional extra allowed terms such as JD requirement labels).
 */
final class EvidenceGrounding {

    private static final Pattern METRIC = Pattern.compile(
            "\\$\\s*\\d[\\d,]*(?:\\.\\d+)?|\\b\\d+(?:\\.\\d+)?\\s*%|\\b\\d+\\s*[xX]\\b|\\b\\d+\\s*(?:million|thousand|billion|k)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern PROPER = Pattern.compile("\\b([A-Z][A-Za-z0-9+#.]*(?:\\s+[A-Z][A-Za-z0-9+#.]*)*)\\b");

    private static final Set<String> ALLOWED_ENGLISH = Set.of(
            "Your", "The", "This", "Add", "If", "Where", "Several", "Experience", "Projects",
            "Skills", "Education", "Resume", "LinkedIn", "GitHub", "No", "Use", "Keep",
            "Related", "Consider", "Professional", "Section", "Needs", "Attention", "JD",
            "Role", "How", "What", "When", "Do", "Not", "Only", "Major", "General"
    );

    private EvidenceGrounding() {
    }

    static List<String> keepGrounded(List<String> sentences, String resumeText, Set<String> extraAllowed) {
        if (sentences == null || sentences.isEmpty()) {
            return List.of();
        }
        String haystack = resumeText == null ? "" : resumeText;
        Set<String> extra = extraAllowed == null ? Set.of() : extraAllowed;
        List<String> kept = new ArrayList<>();
        for (String sentence : sentences) {
            if (sentence != null && !sentence.isBlank() && isGrounded(sentence, haystack, extra)) {
                kept.add(sentence.trim());
            }
        }
        return kept;
    }

    static boolean isGrounded(String sentence, String resumeText, Set<String> extraAllowed) {
        if (sentence == null || sentence.isBlank()) {
            return false;
        }
        String resume = resumeText == null ? "" : resumeText;

        Matcher metrics = METRIC.matcher(sentence);
        while (metrics.find()) {
            if (!containsIgnoreCase(resume, metrics.group()) && !allowed(metrics.group(), extraAllowed)) {
                return false;
            }
        }

        Matcher proper = PROPER.matcher(sentence);
        while (proper.find()) {
            if (proper.start() == 0) {
                continue; // sentence-initial English is not a claimed proper noun
            }
            String token = proper.group(1);
            if (ALLOWED_ENGLISH.contains(token) || isMostlyEnglishFunction(token)) {
                continue;
            }
            if (!containsIgnoreCase(resume, token) && !allowed(token, extraAllowed)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMostlyEnglishFunction(String token) {
        return token.length() <= 2
                || Set.of("If", "And", "For", "With", "From", "That", "This", "These",
                "Your", "You", "The", "A", "An").contains(token);
    }

    private static boolean allowed(String token, Set<String> extraAllowed) {
        for (String extra : extraAllowed) {
            if (extra != null && extra.equalsIgnoreCase(token)) {
                return true;
            }
            if (extra != null && extra.toLowerCase(Locale.ROOT).contains(token.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isBlank()) {
            return false;
        }
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT).trim());
    }

    static Set<String> extraTerms(Iterable<String> values) {
        Set<String> terms = new LinkedHashSet<>();
        if (values == null) {
            return terms;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                terms.add(value.trim());
            }
        }
        return terms;
    }
}
