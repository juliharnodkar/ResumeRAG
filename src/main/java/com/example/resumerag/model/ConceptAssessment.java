package com.example.resumerag.model;

import java.util.List;

public record ConceptAssessment(
        String concept,
        RequirementStatus status,
        List<Evidence> evidence
) {}
