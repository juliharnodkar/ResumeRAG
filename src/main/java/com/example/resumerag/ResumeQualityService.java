package com.example.resumerag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assess resume quality independent of the job description.
 *
 * <p>Quality score is derived from observable signals in the resume text:
 * <ul>
 *   <li>Structure / section organisation (named sections present)</li>
 *   <li>Professional completeness (contact / online presence)</li>
 *   <li>Genuinely quantified impact (numbers with %, $, or explicit multipliers near verbs)</li>
 *   <li>Accomplishment orientation (action verbs present)</li>
 *   <li>Skill presence and section visibility</li>
 * </ul>
 *
 * <p>Deliberately excluded weak heuristics:
 * <ul>
 *   <li>Outcome words such as "improved/increased/reduced" without a number are NOT counted
 *       as measurable outcomes — they are too common and do not prove quantification.</li>
 *   <li>Comma / semicolon counts are NOT used as skill-isolation signals; punctuation is
 *       unrelated to resume quality after chunk concatenation.</li>
 *   <li>Word frequency of "using" / "with" is NOT used as a context signal for skills.</li>
 *   <li>Line count is NOT used to detect formatting clutter because chunks are concatenated
 *       with spaces, so line structure is not preserved after ingestion.</li>
 *   <li>Action-word count is NOT divided by resume length; longer resumes are not penalised.</li>
 * </ul>
 *
 * <p>Formula: overall = (structure * 0.30) + (content * 0.40) + (skill * 0.30)
 * Each sub-score ∈ [0.0, 1.0]; overall ∈ [0.0, 1.0].
 */
@Service
public class ResumeQualityService {

    private final ChatClient chatClient;

    public ResumeQualityService(ChatClient.Builder builder) {
        this.chatClient = builder == null ? null : builder.build();
    }

    // Regex: a bare number followed immediately by % or x (multiplier) or $ before a number.
    // We treat these as genuine quantification signals only when a numeric figure is present.
    private static final Pattern PERCENT_PATTERN =
            Pattern.compile("\\b\\d+\\s*%");

    private static final Pattern DOLLAR_PATTERN =
            Pattern.compile("\\$\\s*\\d+");

    private static final Pattern MULTIPLIER_PATTERN =
            Pattern.compile("\\b\\d+\\s*[xX]\\b");

    // Genuine quantity words that require a number nearby (e.g. "3 million", "5 thousand").
    private static final Pattern MAGNITUDE_PATTERN =
            Pattern.compile("\\b\\d+\\s*(million|thousand|billion|k\\b)");

    public ResumeQualityAssessment assessQuality(List<Document> resumeChunks) {
        if (resumeChunks == null || resumeChunks.isEmpty()) {
            return new ResumeQualityAssessment(0.5, 0.5, 0.5, 0.5, List.of(), List.of());
        }

        String fullResume = concatenateChunks(resumeChunks);

        double structureScore = assessStructure(fullResume);
        double contentScore   = assessContent(fullResume);
        double skillScore     = assessSkillPresentation(fullResume);

        // Formula documented at class level:
        // overall = (structure * 0.30) + (content * 0.40) + (skill * 0.30)
        double overall = (structureScore * 0.30) + (contentScore * 0.40) + (skillScore * 0.30);

        ResumeEvidenceSnapshot evidence = ResumeEvidenceExtractor.extract(resumeChunks);
        List<String> strengths    = identifyStrengths(evidence);
        List<String> improvements = identifyImprovements(evidence);

        return new ResumeQualityAssessment(
                structureScore,
                contentScore,
                skillScore,
                overall,
                strengths,
                improvements
        );
    }

    // -------------------------------------------------------------------------
    // Sub-dimension assessors
    // -------------------------------------------------------------------------

    /**
     * Structure score: presence of named resume sections + contact information.
     * Does NOT use line count (unreliable after chunk concatenation).
     */
    private double assessStructure(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        int sectionCount     = countSections(resume);
        boolean hasContact   = hasContactInfo(resume);
        boolean notTooShort  = resume.split("\\s+").length >= 50;

        double score = 0.4; // baseline: minimal text present

        // Named sections are the most reliable structural signal.
        if (sectionCount >= 4) {
            score += 0.30;
        } else if (sectionCount >= 2) {
            score += 0.15;
        } else if (sectionCount >= 1) {
            score += 0.05;
        }

        // Contact / online presence
        if (hasContact) {
            score += 0.15;
        }

        // Minimal content present (not nearly empty)
        if (notTooShort) {
            score += 0.15;
        }

        return Math.min(1.0, score);
    }

    /**
     * Content score: accomplishment orientation + genuinely quantified impact.
     *
     * <p>Accomplishment orientation: counts action verbs present in the text.
     * This is a presence check (boolean per verb), not a frequency ratio — so
     * longer resumes with more descriptions are not penalised.
     *
     * <p>Quantified impact: only counted when an actual number appears with
     * a quantifier (%, $, multiplier, or magnitude word). Vague outcome words
     * such as "improved" without a number are NOT counted.
     */
    private double assessContent(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        String lower = resume.toLowerCase(Locale.ROOT);

        // Accomplishment orientation: count distinct, field-neutral action verbs.
        int actionVerbsPresent = countDistinctActionVerbsPresent(lower);

        // Genuinely quantified impact: number + quantifier present in text.
        int quantifiedCount = countGenuinelyQuantified(resume);

        // Score accomplishment orientation: max credit at 4+ distinct verbs.
        double actionScore = Math.min(1.0, actionVerbsPresent / 4.0);

        // Score quantified impact: max credit at 3+ genuine quantifications.
        double quantScore = Math.min(1.0, quantifiedCount / 3.0);

        // Content is weighted: accomplishment orientation 60%, quantification 40%.
        return (actionScore * 0.60) + (quantScore * 0.40);
    }

    /**
     * Skill presentation score: presence of a skills/capabilities section and
     * context showing where those capabilities were used.
     *
     * <p>Does NOT use comma / semicolon counts or "using" / "with" frequency.
     * Punctuation is unrelated to presentation quality after concatenation.
     */
    private double assessSkillPresentation(String resume) {
        if (resume == null || resume.isBlank()) {
            return 0.5;
        }

        String lower = resume.toLowerCase(Locale.ROOT);

        boolean hasSkillsSection     = lower.contains("skill") || lower.contains("competenc")
                                       || lower.contains("expertise") || lower.contains("capabilit");
        boolean hasCapabilityContent = lower.contains("tool") || lower.contains("technolog")
                                       || lower.contains("framework") || lower.contains("platform")
                                       || lower.contains("language") || lower.contains("competenc")
                                       || lower.contains("expertise") || lower.contains("capabilit");
        boolean hasProjectOrExperience = lower.contains("project") || lower.contains("experience");

        double score = 0.40; // baseline

        if (hasSkillsSection) {
            score += 0.25;
        }
        if (hasCapabilityContent) {
            score += 0.20;
        }
        if (hasProjectOrExperience) {
            score += 0.15;
        }

        return Math.min(1.0, score);
    }

    // -------------------------------------------------------------------------
    // Strengths and improvements
    // -------------------------------------------------------------------------

    private List<String> identifyStrengths(ResumeEvidenceSnapshot evidence) {
        List<String> strengths = new ArrayList<>();
        if (evidence == null || evidence.fullText().isBlank()) {
            return List.of();
        }

        Set<String> usedTitles = new java.util.LinkedHashSet<>();
        for (ResumeEvidenceSnapshot.ProjectEvidence project : evidence.projects()) {
            String body = project.technologies().isEmpty()
                    ? "Your " + project.name() + " project includes documented implementation work."
                    : "Your " + project.name() + " project documents hands-on work with "
                    + joinOxford(project.technologies(), 3) + ".";
            strengths.add(card("Project implementation evidence", body));
            break;
        }

        // Surface actual work statements without inferring an industry-specific
        // strength from a job title.
        for (String highlight : ResumeEvidenceExtractor.experienceHighlights(evidence, 3)) {
            if (strengths.size() >= 3) {
                break;
            }
            strengths.add(card(uniqueTitle(strengthTitle(highlight), usedTitles), highlight));
        }

        if (strengths.size() < 3 && evidence.quantifiedSnippets().size() >= 1) {
            strengths.add(card("Measurable outcomes", "Your project descriptions include "
                    + joinOxford(evidence.quantifiedSnippets(), 2) + "."));
        }

        if (strengths.size() < 3 && !evidence.employers().isEmpty() && evidence.actionVerbCount() >= 3) {
            strengths.add(card("Specific work evidence", "Experience at " + evidence.employers().getFirst()
                    + " is described with concrete work rather than only a job title."));
        }

        if (strengths.size() < 3 && evidence.sections().size() >= 4) {
            strengths.add(card("Clear resume structure", "The resume is organised into "
                    + joinOxford(evidence.sections(), 6) + " sections."));
        }

        if (strengths.size() < 3 && !evidence.skillItems().isEmpty() && !evidence.projects().isEmpty()) {
            strengths.add(card("Skills supported by experience", "Items such as "
                    + joinOxford(evidence.skillItems(), 3)
                    + " appear in both the skills list and experience descriptions."));
        }

        if (strengths.isEmpty() && evidence.actionVerbCount() >= 2) {
            strengths.add(card("Action-oriented writing", "The resume uses concrete action language rather than only listing responsibilities."));
        }

        // These cards are composed from extracted resume text and metadata;
        // unlike optional model output, they do not require a second grounding pass.
        return List.copyOf(strengths.subList(0, Math.min(3, strengths.size())));
    }

    private List<String> identifyImprovements(ResumeEvidenceSnapshot evidence) {
        List<String> improvements = new ArrayList<>();
        if (evidence == null || evidence.fullText().isBlank()) {
            return List.of(card("Resume content unavailable", "The extracted resume is empty, so there is not enough content to evaluate."));
        }

        ResumeEvidenceSnapshot.ProjectEvidence thinProject = evidence.projects().stream()
                .filter(project -> project.wordCount() < 28)
                .findFirst()
                .orElse(null);
        if (thinProject != null) {
            improvements.add(card("Expand project detail", "The " + thinProject.name()
                    + " project has limited detail. Add the tools used and your specific contribution where accurate."));
        } else if (evidence.sections().stream().anyMatch(section -> section.equalsIgnoreCase("Projects"))
                && evidence.projects().isEmpty()) {
            improvements.add(card("Clarify project detail", "A Projects heading is present, but individual projects lack names or implementation detail. Add accurate specifics."));
        }

        if (evidence.quantifiedSnippets().isEmpty()
                && (evidence.wordCount() >= 40)
                && (evidence.actionVerbCount() >= 1 || evidence.responsibilityHeavy())) {
            String where = evidence.sections().stream().anyMatch(s -> s.equalsIgnoreCase("Experience"))
                    ? "experience"
                    : "project";
            improvements.add(card("Add measurable outcomes", "Several " + where
                    + " bullets describe duties without a measurable result. Add the outcome where accurate."));
        }

        if (evidence.responsibilityHeavy() && evidence.quantifiedSnippets().isEmpty()) {
            improvements.add(card("Clarify achievement impact", "Several experience bullets describe duties without explaining the resulting impact. Add the outcome where accurate."));
        }

        if (!evidence.hasContact()) {
            improvements.add(card("Add contact details", "An email address or professional profile link is not visible in the extracted resume. Add accurate details if they belong on this resume."));
        } else if (!evidence.hasLinkedInOrGithub() && evidence.wordCount() >= 40) {
            improvements.add(card("Add professional profile", "An email is present, but a professional profile link is not visible. Add one if it belongs on this resume."));
        }

        if (!evidence.hasDates() && evidence.wordCount() >= 40) {
            improvements.add(card("Clarify work dates", "Dates are missing or unclear. Add accurate month/year ranges so the timeline is easy to evaluate."));
        }

        if (evidence.skillItems().size() >= 3 && evidence.projects().isEmpty()
                && !evidence.fullText().toLowerCase(Locale.ROOT).contains("experience")) {
            improvements.add(card("Connect skills to experience", "Skills are listed without project or experience context. Show where they were used when accurate."));
        }

        if (evidence.sections().size() < 2 && evidence.wordCount() >= 20) {
            improvements.add(card("Use clear section headings", "Key section headings are unclear or missing, making the resume harder to scan. Add headings that match the content."));
        }

        if (!evidence.hasEducation() && evidence.wordCount() >= 40) {
            improvements.add(card("Add education information", "Education information is missing or unclear. Add the degree, institution, and year if accurate."));
        }

        if (evidence.wordCount() < 50) {
            improvements.add(card("Add resume detail", "The extracted resume is very short, leaving little evidence of your work. Add accurate experience detail."));
        }

        if (improvements.isEmpty()) {
            return List.of(card("No major resume issues", "Use JD tailoring for role-specific changes."));
        }

        List<String> limited = improvements.subList(0, Math.min(5, improvements.size()));
        List<String> grounded = EvidenceGrounding.keepGrounded(limited, evidence.fullText(), Set.of("LinkedIn", "GitHub"));
        if (grounded.isEmpty()) {
            return List.of(card("No major resume issues", "Use JD tailoring for role-specific changes."));
        }
        return grounded;
    }

    private Narrative polishNarrative(
            ResumeEvidenceSnapshot evidence,
            List<String> strengths,
            List<String> improvements
    ) {
        if (chatClient == null || evidence == null || evidence.fullText().isBlank()) {
            return new Narrative(strengths, improvements);
        }

        try {
            String prompt = """
                    Write personalized resume feedback using ONLY the supplied resume facts.
                    Do not invent projects, employers, technologies, metrics, education, or experience.
                    Strengths must cite actual evidence from the facts.
                    Weaknesses must be about the resume itself, not missing job-description skills.
                    Return at most 3 strengths and at most 5 needs-attention items.

                    FACTS:
                    %s

                    DETERMINISTIC DRAFT STRENGTHS:
                    %s

                    DETERMINISTIC DRAFT NEEDS ATTENTION:
                    %s
                    """.formatted(
                    factsBlock(evidence),
                    strengths,
                    improvements
            );

            Narrative generated = chatClient.prompt()
                    .system("""
                            You personalize resume feedback. You may only use supplied facts.
                            Never invent. Never mention job-description skills as resume weaknesses.
                            """)
                    .user(prompt)
                    .call()
                    .entity(Narrative.class);

            if (generated == null) {
                return new Narrative(strengths, improvements);
            }
            List<String> groundedStrengths = EvidenceGrounding.keepGrounded(
                    generated.strengths(), evidence.fullText(), Set.of());
            List<String> groundedWeaknesses = EvidenceGrounding.keepGrounded(
                    generated.improvements(), evidence.fullText(), Set.of("LinkedIn", "GitHub"));
            if (groundedStrengths.isEmpty()) {
                groundedStrengths = strengths;
            }
            if (groundedWeaknesses.isEmpty()) {
                groundedWeaknesses = (improvements == null || improvements.isEmpty())
                        ? List.of("No major general resume weaknesses were identified. Use JD tailoring for role-specific changes.")
                        : improvements;
            }
            return new Narrative(
                    groundedStrengths.subList(0, Math.min(3, groundedStrengths.size())),
                    groundedWeaknesses.subList(0, Math.min(5, groundedWeaknesses.size()))
            );
        } catch (Exception ignored) {
            return new Narrative(strengths, improvements);
        }
    }

    private static String factsBlock(ResumeEvidenceSnapshot evidence) {
        return """
                sections=%s
                projects=%s
                skills=%s
                metrics=%s
                employers=%s
                hasContact=%s
                hasDates=%s
                hasEducation=%s
                responsibilityHeavy=%s
                wordCount=%d
                """.formatted(
                evidence.sections(),
                evidence.projects().stream().map(ResumeEvidenceSnapshot.ProjectEvidence::name).toList(),
                evidence.skillItems(),
                evidence.quantifiedSnippets(),
                evidence.employers(),
                evidence.hasContact(),
                evidence.hasDates(),
                evidence.hasEducation(),
                evidence.responsibilityHeavy(),
                evidence.wordCount()
        );
    }

    private static String joinOxford(List<String> items, int limit) {
        List<String> values = items.stream()
                .filter(item -> item != null && !item.isBlank())
                .limit(limit)
                .toList();
        if (values.isEmpty()) {
            return "";
        }
        if (values.size() == 1) {
            return values.getFirst();
        }
        if (values.size() == 2) {
            return values.get(0) + " and " + values.get(1);
        }
        return String.join(", ", values.subList(0, values.size() - 1))
                + " and " + values.getLast();
    }

    private static String card(String title, String body) {
        return title + ": " + body;
    }

    private static String strengthTitle(String highlight) {
        String lower = highlight.toLowerCase(Locale.ROOT);
        if (lower.matches(".*\\b\\d+\\+?\\s+years?.*") || lower.contains("experience")) {
            return "Career experience";
        }
        if (lower.matches(".*\\b(?:managed|led|supervised|trained|mentored)\\b.*")) {
            return "Leadership and development";
        }
        if (lower.matches(".*\\b(?:served|provided|handled|communicated|resolved|supported)\\b.*")) {
            return "Service and communication";
        }
        if (lower.matches(".*\\b(?:coordinated|maintained|delivered|implemented)\\b.*")) {
            return "Operational contribution";
        }
        return "Work contribution";
    }

    private static String uniqueTitle(String title, Set<String> usedTitles) {
        String candidate = title;
        int suffix = 2;
        while (!usedTitles.add(candidate)) {
            candidate = title + " " + suffix++;
        }
        return candidate;
    }

    record Narrative(
            List<String> strengths,
            @JsonProperty("improvements")
            @JsonAlias({"needsAttention", "weaknesses"})
            List<String> improvements
    ) {
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private String concatenateChunks(List<Document> chunks) {
        return chunks.stream()
                .map(Document::getText)
                .reduce((a, b) -> a + " " + b)
                .orElse("");
    }

    private int countSections(String resume) {
        String lower = resume.toLowerCase(Locale.ROOT);
        int count = 0;
        for (String section : new String[]{"experience", "skill", "project", "education",
                                            "certif", "about", "summary", "achievement"}) {
            if (lower.contains(section)) count++;
        }
        return count;
    }

    private boolean hasContactInfo(String resume) {
        return resume.contains("@")
                || resume.toLowerCase(Locale.ROOT).contains("linkedin")
                || resume.toLowerCase(Locale.ROOT).contains("github")
                || resume.contains("http");
    }

    /**
     * Count distinct action verbs present in the resume text (boolean per verb,
     * not frequency). Uses a bounded list of strong professional action verbs.
     * Longer resumes are not penalised because this is a presence count.
     */
    private int countDistinctActionVerbsPresent(String lower) {
        // Fixed, field-neutral vocabulary of unambiguous professional action verbs.
        String[] verbs = {
            "built", "developed", "created", "designed", "led", "managed",
            "achieved", "delivered", "implemented", "architected", "launched",
            "deployed", "migrated", "optimised", "optimized", "automated",
            "integrated", "collaborated", "mentored", "refactored", "scaled",
            "supervised", "trained", "handled", "provided", "served", "coordinated",
            "resolved", "maintained", "communicated", "supported"
        };
        int count = 0;
        for (String verb : verbs) {
            if (lower.contains(verb)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Count genuinely quantified impact statements.
     *
     * <p>A quantified statement requires an actual numeric figure paired with:
     * <ul>
     *   <li>a percentage sign (%)</li>
     *   <li>a dollar sign ($)</li>
     *   <li>a multiplier (e.g. "3x")</li>
     *   <li>a magnitude word (million, thousand, billion, k)</li>
     * </ul>
     *
     * <p>Words like "improved", "increased", or "reduced" are NOT counted
     * unless accompanied by such a figure.
     */
    int countGenuinelyQuantified(String resume) {
        if (resume == null || resume.isBlank()) return 0;
        int count = 0;
        count += countMatches(PERCENT_PATTERN, resume);
        count += countMatches(DOLLAR_PATTERN, resume);
        count += countMatches(MULTIPLIER_PATTERN, resume);
        count += countMatches(MAGNITUDE_PATTERN, resume);
        return count;
    }

    private int countMatches(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        int count = 0;
        while (m.find()) count++;
        return count;
    }
}
