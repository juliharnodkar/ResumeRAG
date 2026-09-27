package com.example.resumerag;

import java.util.List;

/**
 * Observable facts taken from resume chunks. Used to phrase strengths,
 * weaknesses, and JD tailoring without inventing experience.
 */
record ResumeEvidenceSnapshot(
        String fullText,
        List<String> sections,
        List<ProjectEvidence> projects,
        List<String> skillItems,
        List<String> quantifiedSnippets,
        List<String> employers,
        boolean hasContact,
        boolean hasLinkedInOrGithub,
        boolean hasDates,
        boolean hasEducation,
        boolean educationLooksComplete,
        boolean responsibilityHeavy,
        int wordCount,
        int actionVerbCount
) {
    record ProjectEvidence(String name, String text, List<String> technologies, int wordCount) {
    }
}
