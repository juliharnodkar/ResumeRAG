package com.example.resumerag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Assess resume quality independent of the job description.
 * 
 * Quality score is based on:
 * - Clear hierarchy and structure
 * - Action-oriented, specific descriptions
 * - Measurable outcomes
 * - Skill clarity and presence
 * - Consistency and professionalism
 */
@Service
public class ResumeQualityService {

    public ResumeQualityAssessment assessQuality(List<Document> resumeChunks) {
        if (resumeChunks == null || resumeChunks.isEmpty()) {
            return new ResumeQualityAssessment(0.5, 0.5, 0.5, 0.5, List.of(), List.of());
        }

        String fullResume = concatenateChunks(resumeChunks);

        double structureScore = assessStructure(fullResume);
        double contentScore = assessContent(fullResume);
        double skillScore = assessSkillPresentation(fullResume);

        double overall = (structureScore * 0.3) + (contentScore * 0.4) + (skillScore * 0.3);

        List<String> strengths = identifyStrengths(fullResume, structureScore, contentScore, skillScore);
        List<String> improvements = identifyImprovements(fullResume, structureScore, contentScore, skillScore);

        return new ResumeQualityAssessment(
                structureScore,
                contentScore,
                skillScore,
                overall,
                strengths,
                improvements
        );
    }

    private double assessStructure(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        String lower = resume.toLowerCase(Locale.ROOT);
        int sectionCount = countSections(resume);
        boolean hasContactInfo = hasContactInfo(resume);
        boolean readableFormatting = !hasExcessiveClutter(resume);

        double score = 0.6;
        if (sectionCount >= 3) score += 0.15;
        if (hasContactInfo) score += 0.1;
        if (readableFormatting) score += 0.15;

        return Math.min(1.0, score);
    }

    private double assessContent(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        String lower = resume.toLowerCase(Locale.ROOT);
        int actionWordCount = countActionWords(lower);
        int measurableCount = countMeasurableOutcomes(lower);
        int specificityIndicators = countSpecificityIndicators(lower);

        double actionRatio = Math.min(1.0, actionWordCount / Math.max(1, resume.split("\\s+").length / 20.0));
        double measurableRatio = Math.min(1.0, measurableCount / 10.0);
        double specificRatio = Math.min(1.0, specificityIndicators / 10.0);

        return (actionRatio * 0.5) + (measurableRatio * 0.25) + (specificRatio * 0.25);
    }

    private double assessSkillPresentation(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        String lower = resume.toLowerCase(Locale.ROOT);

        // Presence of skill-related keywords
        boolean hasSkillsSection = lower.contains("skill");
        boolean hasToolsMentioned = lower.contains("tool") || lower.contains("technolog");

        // Clarity: skills mentioned in context vs isolated lists
        int skillContextMentions = countPhrase(lower, "using ");
        int skillContextMentions2 = countPhrase(lower, "with ");
        int isolatedSkills = countPhrase(lower, ",") + countPhrase(lower, ";");

        double score = 0.5;
        if (hasSkillsSection) score += 0.15;
        if (hasToolsMentioned) score += 0.1;
        if (skillContextMentions + skillContextMentions2 > isolatedSkills / 2) score += 0.15;

        return Math.min(1.0, score);
    }

    private List<String> identifyStrengths(String resume, double structure, double content, double skill) {
        List<String> strengths = new ArrayList<>();

        if (structure > 0.75) {
            strengths.add("Clear, organized structure");
        }
        if (content > 0.75) {
            strengths.add("Strong, specific descriptions");
        }
        if (content > 0.6 && countMeasurableOutcomes(resume.toLowerCase()) > 3) {
            strengths.add("Measurable outcomes included");
        }
        if (skill > 0.75) {
            strengths.add("Skills clearly surfaced");
        }

        return strengths.isEmpty() ? List.of("Resume foundation established") : strengths.subList(0, Math.min(3, strengths.size()));
    }

    private List<String> identifyImprovements(String resume, double structure, double content, double skill) {
        List<String> improvements = new ArrayList<>();

        if (content < 0.6) {
            improvements.add("Use stronger action verbs and specific outcomes");
        }
        if (countMeasurableOutcomes(resume.toLowerCase()) < 2) {
            improvements.add("Quantify impact where possible");
        }
        if (skill < 0.6) {
            improvements.add("Make technologies and tools more visible");
        }
        if (hasExcessiveClutter(resume)) {
            improvements.add("Simplify formatting for readability");
        }

        return improvements.subList(0, Math.min(3, improvements.size()));
    }

    private String concatenateChunks(List<Document> chunks) {
        return chunks.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private int countSections(String resume) {
        String lower = resume.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String section : new String[]{"experience", "skill", "project", "education", "certif", "about", "summary"}) {
            if (lower.contains(section)) count++;
        }
        return count;
    }

    private boolean hasContactInfo(String resume) {
        return resume.contains("@") || resume.contains("linkedin") || resume.contains("github") || resume.contains("http");
    }

    private boolean hasExcessiveClutter(String resume) {
        int lines = resume.split("\n").length;
        int words = resume.split("\\s+").length;
        return words < 50 || words > 5000 || lines < 10;
    }

    private int countActionWords(String lower) {
        int count = 0;
        for (String word : new String[]{"built", "developed", "created", "designed", "led", "managed", "achieved", "improved", "increased", "reduced", "delivered", "implemented"}) {
            count += countPhrase(lower, word + " ");
        }
        return count;
    }

    private int countMeasurableOutcomes(String lower) {
        int count = 0;
        for (String indicator : new String[]{"%", "x ", "times", "improved", "increased", "reduced", "saved", "earned"}) {
            count += countPhrase(lower, indicator);
        }
        return count;
    }

    private int countSpecificityIndicators(String lower) {
        int count = 0;
        for (String indicator : new String[]{"$", "million", "thousand", "year", "project", "client", "user"}) {
            count += countPhrase(lower, indicator);
        }
        return count;
    }

    private int countPhrase(String text, String phrase) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(phrase, index)) != -1) {
            count++;
            index += phrase.length();
        }
        return count;
    }
}
