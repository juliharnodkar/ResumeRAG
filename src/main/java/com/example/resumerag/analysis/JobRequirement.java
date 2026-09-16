package com.example.resumerag.analysis;

import com.example.resumerag.model.ExperienceCondition;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;

public record JobRequirement(
        String originalText,
        RequirementExpression expression,
        RequirementType type,
        RequirementImportance importance,
        ExperienceCondition experienceCondition,
        Verifiability verifiability
) {
    public JobRequirement {
        if (originalText == null || originalText.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement text cannot be blank."
            );
        }

        if (expression == null) {
            throw new IllegalArgumentException(
                    "Requirement expression cannot be null."
            );
        }

        if (type == null) {
            type = RequirementType.OTHER;
        }

        if (importance == null) {
            importance = RequirementImportance.MEDIUM;
        }

        if (verifiability == null) {
            verifiability = Verifiability.VERIFIABLE;
        }

        originalText = originalText.trim();
    }

    public JobRequirement(
            String originalText,
            RequirementExpression expression,
            RequirementType type,
            RequirementImportance importance,
            ExperienceCondition experienceCondition
    ) {
        this(
                originalText,
                expression,
                type,
                importance,
                experienceCondition,
                Verifiability.VERIFIABLE
        );
    }
}
