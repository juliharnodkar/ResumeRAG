package com.example.resumerag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Direct-mention verification that is inflection- and conjunction-aware.
 *
 * <p>Does not change the 0.50 semantic threshold. Does not retrieve. Does not
 * maintain a domain synonym list. Distinct tokens such as Java/JavaScript and
 * AWS/Azure/GCP remain distinct because matching is whole-token after a
 * conservative stem, never prefix-based.
 */
final class ConceptEvidenceMatcher {

    private static final Set<String> STOPWORDS = Set.of(
            "and", "or", "of", "the", "a", "an", "in", "for", "with", "to", "on"
    );

    private static final Set<String> BACHELOR_MARKERS = Set.of(
            "bachelor", "bachelors", "baccalaureate", "undergraduate",
            "bs", "ba", "bsc", "beng", "bba"
    );

    private static final Set<String> DEGREE_COMPLETERS = Set.of(
            "degree", "science", "arts", "engineering", "business",
            "education", "sc", "tech", "technology"
    );

    /**
     * Action nominalizations whose meaning is the act, not the topic noun.
     * Satisfied by the same stem or by a small closed set of English
     * handling/resolving verbs — not by merely mentioning the object.
     */
    private static final Set<String> ACTION_NOMINALS = Set.of(
            "resolution", "handling"
    );

    private static final Set<String> ACTION_SUPPORT_STEMS = Set.of(
            "handl", "resolv"
    );

    private ConceptEvidenceMatcher() {
    }

    static boolean matches(String concept, String text) {
        if (concept == null || concept.isBlank() || text == null || text.isBlank()) {
            return false;
        }

        String normalizedConcept = normalize(concept);
        String normalizedText = normalize(text);
        if (normalizedConcept.isBlank() || normalizedText.isBlank()) {
            return false;
        }

        if (containsPhrase(normalizedText, normalizedConcept)) {
            return true;
        }

        List<String> conceptTokens = contentTokens(normalizedConcept);
        List<String> textTokens = allTokens(normalizedText);
        if (conceptTokens.isEmpty() || textTokens.isEmpty()) {
            return false;
        }

        if (isBachelorsDegreeConcept(conceptTokens)) {
            return hasBachelorsDegreeEvidence(textTokens);
        }

        for (String conceptToken : conceptTokens) {
            if (!tokenEvidenced(conceptToken, textTokens)) {
                return false;
            }
        }
        return true;
    }

    private static boolean tokenEvidenced(String conceptToken, List<String> textTokens) {
        for (String textToken : textTokens) {
            if (tokensMatch(conceptToken, textToken)) {
                return true;
            }
        }
        return ACTION_NOMINALS.contains(conceptToken)
                && hasActionSupport(textTokens);
    }

    private static boolean tokensMatch(String left, String right) {
        if (left.equals(right)) {
            return true;
        }
        String leftStem = stem(left);
        String rightStem = stem(right);
        return !leftStem.isBlank() && leftStem.equals(rightStem);
    }

    private static boolean hasActionSupport(List<String> textTokens) {
        for (String token : textTokens) {
            if (ACTION_SUPPORT_STEMS.contains(stem(token))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isBachelorsDegreeConcept(List<String> conceptTokens) {
        if (conceptTokens.isEmpty() || conceptTokens.size() > 2) {
            return false;
        }
        boolean bachelor = false;
        boolean onlyDegreeWords = true;
        for (String token : conceptTokens) {
            if (BACHELOR_MARKERS.contains(token) || "bachelor".equals(stem(token))) {
                bachelor = true;
            } else if (!"degree".equals(token)) {
                onlyDegreeWords = false;
            }
        }
        return bachelor && onlyDegreeWords;
    }

    private static boolean hasBachelorsDegreeEvidence(List<String> textTokens) {
        boolean bachelor = false;
        boolean completer = false;
        for (String token : textTokens) {
            if (BACHELOR_MARKERS.contains(token) || "bachelor".equals(stem(token))) {
                bachelor = true;
            }
            if (DEGREE_COMPLETERS.contains(token) || DEGREE_COMPLETERS.contains(stem(token))) {
                completer = true;
            }
        }
        return bachelor && completer;
    }

    private static boolean containsPhrase(String normalizedText, String normalizedConcept) {
        return (" " + normalizedText + " ").contains(" " + normalizedConcept + " ");
    }

    private static List<String> contentTokens(String normalized) {
        List<String> tokens = new ArrayList<>();
        for (String token : allTokens(normalized)) {
            if (!STOPWORDS.contains(token) && !isNoiseToken(token)) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    private static List<String> allTokens(String normalized) {
        List<String> tokens = new ArrayList<>();
        if (normalized == null || normalized.isBlank()) {
            return tokens;
        }
        for (String token : normalized.split(" ")) {
            if (!token.isBlank()) {
                tokens.add(token);
            }
        }
        return tokens;
    }

    /**
     * Apostrophes become spaces during normalize ("bachelor's" → "bachelor s").
     * Keep "c" because it is a distinct language token.
     */
    private static boolean isNoiseToken(String token) {
        return token.length() == 1 && !"c".equals(token);
    }

    static String normalize(String value) {
        String prepared = value
                .toLowerCase(Locale.ROOT)
                .replaceAll("b\\.\\s*sc\\.?", "bsc")
                .replaceAll("b\\.\\s*s\\b", "bs");
        return prepared
                .replaceAll("[^a-z0-9+#]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Conservative inflection strip. Never truncates to a prefix of a different
     * token (Java vs JavaScript, AWS vs Azure).
     *
     * <p>Agent nouns (-or, -er) are stripped before verb suffixes so that
     * "communicator" → "communicat" matches "communication" → "communicat",
     * and "supervisor" → "supervis" matches "supervision" → "supervis".
     * Only applies when the resulting stem is ≥ 4 characters to avoid
     * collapsing short tokens.
     */
    static String stem(String token) {
        if (token == null || token.length() < 3) {
            return token == null ? "" : token;
        }
        if (token.indexOf('+') >= 0 || token.indexOf('#') >= 0) {
            return token;
        }

        String s = token;

        // Agent suffixes: -or and -er (e.g. communicator→communicat, supervisor→supervis)
        // Only strip when the remaining stem is >= 4 chars and ends in a vowel or
        // a consonant cluster typical of English verb stems.
        if (s.endsWith("or") && s.length() > 5) {
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("er") && s.length() > 5
                && !s.endsWith("eer") && !s.endsWith("eer")) {
            s = s.substring(0, s.length() - 2);
        }

        if (s.endsWith("ies") && s.length() > 4) {
            s = s.substring(0, s.length() - 3) + "y";
        } else if (s.endsWith("es") && s.length() > 4 && !s.endsWith("sses") && !s.endsWith("nes")) {
            s = s.substring(0, s.length() - 2);
        } else if (s.endsWith("s") && !s.endsWith("ss") && !s.endsWith("us") && !s.endsWith("is")
                && s.length() > 3) {
            s = s.substring(0, s.length() - 1);
        }

        if (s.endsWith("ing") && s.length() > 6) {
            s = s.substring(0, s.length() - 3);
        }
        if (s.endsWith("ed") && s.length() > 5) {
            s = s.substring(0, s.length() - 2);
        }
        if (s.endsWith("ion") && s.length() > 6) {
            s = s.substring(0, s.length() - 3);
        }
        return s;
    }
}
