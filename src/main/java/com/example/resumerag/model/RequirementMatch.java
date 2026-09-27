package com.example.resumerag.model;

import java.util.List;

public record RequirementMatch(
        String requirement,
        RequirementType type,
        RequirementImportance importance,
        RequirementStatus status,
        RequirementExpression expression,
        List<ConceptAssessment> concepts,
        List<Evidence> evidence,
        ExperienceCondition experienceCondition,
        boolean experienceVerified
) {}
