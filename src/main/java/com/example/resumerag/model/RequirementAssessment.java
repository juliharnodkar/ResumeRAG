package com.example.resumerag.model;

import java.util.List;

public record RequirementAssessment(
        RequirementMatch requirement,
        double score
) {}
