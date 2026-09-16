package com.example.resumerag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Builds a PostgreSQL POSIX regex ({@code ~*}) that matches a concept as
 * whole tokens. Token characters are {@code [A-Za-z0-9+#]}, matching
 * {@link RequirementMatchingService} normalization. This is not a synonym list.
 */
public final class LexicalQueryPattern {

    private static final Pattern TOKEN_SPLIT =
            Pattern.compile("[^A-Za-z0-9+#]+");

    private static final String BOUNDARY = "[^A-Za-z0-9+#]";

    private static final String POSIX_SPECIALS = ".\\[](){}*+?^$|";

    private LexicalQueryPattern() {
    }

    public static Optional<String> fromQuery(String query) {
        if (query == null || query.isBlank()) {
            return Optional.empty();
        }

        List<String> tokens = tokenize(query);

        if (tokens.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder pattern = new StringBuilder();
        pattern.append("(^|").append(BOUNDARY).append(")");

        for (int i = 0; i < tokens.size(); i++) {
            if (i > 0) {
                pattern.append(BOUNDARY).append("+");
            }
            pattern.append(escapePosix(tokens.get(i)));
        }

        pattern.append("(").append(BOUNDARY).append("|$)");

        return Optional.of(pattern.toString());
    }

    static List<String> tokenize(String query) {
        String[] parts = TOKEN_SPLIT.split(query);
        List<String> tokens = new ArrayList<>();

        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                tokens.add(part.toLowerCase(Locale.ROOT));
            }
        }

        return tokens;
    }

    static String escapePosix(String token) {
        StringBuilder escaped = new StringBuilder(token.length());

        for (int i = 0; i < token.length(); i++) {
            char character = token.charAt(i);

            if (POSIX_SPECIALS.indexOf(character) >= 0) {
                escaped.append('\\');
            }

            escaped.append(character);
        }

        return escaped.toString();
    }
}
