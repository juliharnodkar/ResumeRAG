package com.example.resumerag.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;

/**
 * User-facing result: resume-first scoring + JD tailoring.
 *
 * <p>The three {@code _internal*} fields are retained in the Java record for
 * auditability and internal reasoning, but are annotated with {@link JsonIgnore}
 * so they are never serialised in the API response. The client-facing JSON
 * contains only: score, scoreExplanation, resumeStrengths, needsAttention,
 * and jdTailoringTips.
 */
public record TailoredMatchResult(
        int score,
        String scoreExplanation,
        List<String> resumeStrengths,
        List<String> needsAttention,
        List<String> jdTailoringTips,

        // Retained internally for reasoning/auditability — NOT rendered in API response.
        @JsonIgnore List<RequirementMatch> _internalRequirements,
        @JsonIgnore List<String> _internalMatched,
        @JsonIgnore List<String> _internalMissing
) {
}
