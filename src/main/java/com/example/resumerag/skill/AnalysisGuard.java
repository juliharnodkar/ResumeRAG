package com.example.resumerag.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AnalysisGuard {

    private AnalysisGuard() {}

    public static String verifyOrFallback(String analysis, List<String> matched, List<String> missing) {
        if (!isConsistent(analysis, matched, missing)) return fallback(matched, missing);
        return analysis.trim();
    }

    public static boolean isConsistent(String analysis, List<String> matched, List<String> missing) {
        if (analysis == null || analysis.isBlank()) return false;
        String lower = analysis.toLowerCase(Locale.ROOT);
        int summary = lower.indexOf("match summary");
        int gaps = lower.indexOf("gaps");
        int recommendations = lower.indexOf("recommendations");
        if (summary < 0 || gaps < 0 || recommendations < 0 || !(summary < gaps && gaps < recommendations)) return false;
        String summaryText = lower.substring(summary, gaps);
        String gapsText = lower.substring(gaps, recommendations);
        String recText = lower.substring(recommendations);

        for (String skill : matched) {
            if (mentions(gapsText, skill) || mentions(recText, skill)) return false;
        }
        for (String skill : missing) {
            if (mentions(summaryText, skill)) return false;
        }
        for (String canonical : SkillRegistry.canonicalSkills()) {
            boolean allowedInSummary = matched.contains(canonical);
            boolean allowedInGaps = missing.contains(canonical);
            if (!allowedInSummary && mentions(summaryText, canonical)) return false;
            if (!allowedInGaps && mentions(gapsText, canonical)) return false;
        }
        return true;
    }

    private static boolean mentions(String text, String canonical) {
        return SkillRegistry.contains(text, canonical);
    }

    public static String fallback(List<String> matched, List<String> missing) {
        String matchedText = matched.isEmpty() ? "No verified skills matched." : String.join(", ", matched);
        String missingText = missing.isEmpty() ? "No missing verified skills." : String.join(", ", missing);
        List<String> lines = new ArrayList<>();
        lines.add("1. MATCH SUMMARY");
        lines.add("Verified matched skills: " + matchedText + ".");
        lines.add("");
        lines.add("2. GAPS");
        lines.add("Missing verified skills: " + missingText + ".");
        lines.add("");
        lines.add("3. RECOMMENDATIONS");
        if (missing.isEmpty()) lines.add("The resume already covers all verified requirements extracted from the job description.");
        else for (String skill : missing) lines.add("Add verified evidence for " + skill + " only if you genuinely have that experience.");
        return String.join("\n", lines);
    }
}
