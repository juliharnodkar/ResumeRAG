package com.example.resumerag.model;

import java.util.List;

public record MatchResult(
        int score,
        List<String> matchedSkills,
        List<String> missingSkills,
        String analysis,
        List<String> recommendations,
        List<SkillMatch> skillDetails
) {
}