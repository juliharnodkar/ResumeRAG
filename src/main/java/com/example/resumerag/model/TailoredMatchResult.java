package com.example.resumerag.model;

import java.util.List;

/**
 * User-facing result: resume-first scoring + JD tailoring.
 * 
 * Internal requirements/partial/not-evidenced are NOT exposed.
 * Focus: resume quality + practical tailoring suggestions.
 */
public record TailoredMatchResult(
        int score,
        String scoreExplanation,
        List<String> resumeStrengths,
        List<String> needsAttention,
        List<String> jdTailoringTips,
        
        // Retained internally for reasoning/auditability (not rendered to user)
        List<RequirementMatch> _internalRequirements,
        List<String> _internalMatched,
        List<String> _internalMissing
) {
}
