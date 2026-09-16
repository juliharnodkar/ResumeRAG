package com.example.resumerag;

import java.util.List;

/**
 * Baseline resume quality assessment, independent of any specific JD.
 * 
 * Evaluates:
 * - Structure clarity (readability, hierarchy)
 * - Content quality (action-oriented, concrete, specific)
 * - Skill presentation (surfacing, clarity, consistency)
 * - Communication quality (scannability, impact expression)
 */
public record ResumeQualityAssessment(
        double structureScore,
        double contentQualityScore,
        double skillPresentationScore,
        double overallQualityScore,
        List<String> strengths,
        List<String> improvements
) {
}
