package com.example.resumerag.skill;

import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AnalysisGuard {

    private AnalysisGuard() {
    }

    /*
     * New domain-neutral guard.
     *
     * Deterministic requirement assessments are authoritative.
     * Gemini is only responsible for explaining them.
     */
    public static String verifyOrFallback(
            String generated,
            List<RequirementMatch> requirements
    ) {

        if (generated == null || generated.isBlank()) {
            return fallback(requirements);
        }

        if (requirements == null) {
            return fallback(List.of());
        }

        if (!hasRequiredSections(generated)) {
            return fallback(requirements);
        }

        if (containsContradiction(generated, requirements)) {
            return fallback(requirements);
        }

        return generated.trim();
    }

    /*
     * Backwards-compatible overload for existing tests/callers.
     */
    public static String verifyOrFallback(
            String generated,
            List<String> matched,
            List<String> missing
    ) {

        if (generated == null || generated.isBlank()) {
            return legacyFallback(matched, missing);
        }

        if (!hasRequiredSections(generated)) {
            return legacyFallback(matched, missing);
        }

        String lower = generated.toLowerCase(Locale.ROOT);

        if (matched != null) {
            for (String item : matched) {
                if (item != null && !item.isBlank()
                        && lower.contains(item.toLowerCase(Locale.ROOT))
                        && extractSection(lower, "gaps", null).contains(item.toLowerCase(Locale.ROOT))) {
                    return legacyFallback(matched, missing);
                }
            }
        }

        return generated.trim();
    }

    private static boolean hasRequiredSections(String text) {

        String upper = text.toUpperCase(Locale.ROOT);

        int summary = upper.indexOf("SUMMARY");
        int strengths = upper.indexOf("STRENGTHS");
        int gaps = upper.indexOf("GAPS");

        return summary >= 0
                && strengths > summary
                && gaps > strengths;
    }

    private static boolean containsContradiction(
            String generated,
            List<RequirementMatch> requirements
    ) {
        String lower = generated.toLowerCase(Locale.ROOT);

        String summarySection = extractSection(
                lower,
                "summary",
                "strengths"
        );

        String strengthsSection = extractSection(
                lower,
                "strengths",
                "gaps"
        );

        String gapsSection = extractSection(
                lower,
                "gaps",
                null
        );

        for (RequirementMatch requirement : requirements) {
            if (requirement == null
                    || requirement.requirement() == null
                    || requirement.requirement().isBlank()) {
                continue;
            }

            String text = requirement.requirement()
                    .toLowerCase(Locale.ROOT)
                    .trim();

            if (requirement.status() == RequirementStatus.NOT_EVIDENCED
                    || requirement.status() == RequirementStatus.PARTIAL) {

                if (strengthsSection.contains(text)) {
                    return true;
                }

                if (summarySection.contains(text)
                        && isPresentedAsMatched(summarySection, text)) {
                    return true;
                }
            }

            if (requirement.status() == RequirementStatus.MATCHED
                    && gapsSection.contains(text)) {
                return true;
            }
        }

        return false;
    }

    private static String extractSection(
            String text,
            String section,
            String nextSection
    ) {
        String marker = section.toLowerCase(Locale.ROOT);
        int start = text.indexOf(marker);

        if (start < 0) {
            return "";
        }

        start += marker.length();

        if (nextSection == null) {
            return text.substring(start);
        }

        int end = text.indexOf(
                nextSection.toLowerCase(Locale.ROOT),
                start
        );

        return end < 0
                ? text.substring(start)
                : text.substring(start, end);
    }

    private static boolean isPresentedAsMatched(
            String section,
            String requirement
    ) {
        int position = section.indexOf(requirement);

        if (position < 0) {
            return false;
        }

        int start = Math.max(0, position - 120);
        int end = Math.min(
                section.length(),
                position + requirement.length() + 120
        );

        String context = section.substring(start, end);

        return context.contains("matched")
                || context.contains("satisfied")
                || context.contains("meets")
                || context.contains("strong match")
                || context.contains("fully meets")
                || context.contains("requirement is met")
                || context.contains("requirement is satisfied");
    }
    private static String fallback(
            List<RequirementMatch> requirements
    ) {

        StringBuilder result = new StringBuilder();

        result.append("SUMMARY\n");
        result.append("The deterministic requirement assessment is shown below.\n\n");

        result.append("STRENGTHS\n");

        boolean hasStrength = false;

        if (requirements != null) {
            for (RequirementMatch requirement : requirements) {

                if (requirement != null
                        && requirement.status() == RequirementStatus.MATCHED) {

                    result.append("- ")
                            .append(requirement.requirement())
                            .append("\n");

                    hasStrength = true;
                }
            }
        }

        if (!hasStrength) {
            result.append("- No fully matched requirements were verified.\n");
        }

        result.append("\nGAPS\n");

        boolean hasGap = false;

        if (requirements != null) {
            for (RequirementMatch requirement : requirements) {

                if (requirement != null
                        && (requirement.status() == RequirementStatus.NOT_EVIDENCED
                        || requirement.status() == RequirementStatus.PARTIAL)) {

                    result.append("- ")
                            .append(requirement.requirement())
                            .append(" — ")
                            .append(requirement.status())
                            .append("\n");

                    hasGap = true;
                }
            }
        }

        if (!hasGap) {
            result.append("- No missing or partial requirements were verified.\n");
        }

        return result.toString().trim();
    }

    private static String legacyFallback(
            List<String> matched,
            List<String> missing
    ) {

        StringBuilder result = new StringBuilder();

        result.append("SUMMARY\n");
        result.append("Deterministic requirement assessment completed.\n\n");

        result.append("STRENGTHS\n");

        if (matched == null || matched.isEmpty()) {
            result.append("- None verified.\n");
        } else {
            for (String item : matched) {
                result.append("- ").append(item).append("\n");
            }
        }

        result.append("\nGAPS\n");

        if (missing == null || missing.isEmpty()) {
            result.append("- None identified.\n");
        } else {
            for (String item : missing) {
                result.append("- ").append(item).append("\n");
            }
        }

        return result.toString().trim();
    }
}