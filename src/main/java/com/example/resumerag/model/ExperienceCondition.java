package com.example.resumerag.model;

public record ExperienceCondition(
        Integer minimumMonths,
        Integer maximumMonths,
        String originalText
) {
    public ExperienceCondition {
        if (minimumMonths != null && minimumMonths < 0) {
            throw new IllegalArgumentException(
                    "Minimum experience cannot be negative."
            );
        }

        if (maximumMonths != null && maximumMonths < 0) {
            throw new IllegalArgumentException(
                    "Maximum experience cannot be negative."
            );
        }

        if (minimumMonths != null
                && maximumMonths != null
                && minimumMonths > maximumMonths) {
            throw new IllegalArgumentException(
                    "Minimum experience cannot exceed maximum experience."
            );
        }

        if (originalText != null) {
            originalText = originalText.trim();
        }
    }
}
