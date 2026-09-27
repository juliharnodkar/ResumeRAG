package com.example.resumerag.model;

import java.util.List;

public record SkillMatch(
        String skill,
        boolean matched,
        List<String> evidence
) {
}
