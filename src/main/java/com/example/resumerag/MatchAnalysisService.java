package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.example.resumerag.model.Evidence;
import com.example.resumerag.model.MatchResult;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.SkillMatch;
import com.example.resumerag.model.TailoredMatchResult;
import com.example.resumerag.skill.AnalysisGuard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class MatchAnalysisService {

    private static final Comparator<Evidence> EXPLANATION_EVIDENCE_ORDER =
            Comparator.comparing(Evidence::directMention)
                    .reversed()
                    .thenComparingDouble(evidence ->
                            evidence.directMention()
                                    ? 0.0
                                    : -evidence.relevance())
                    .thenComparing(evidence ->
                            normalizeEvidenceField(evidence.section()))
                    .thenComparing(evidence ->
                            normalizeEvidenceField(evidence.project()))
                    .thenComparing(evidence ->
                            normalizeEvidenceField(evidence.text()));

    private final JobRequirementExtractionService requirementExtractionService;
    private final RequirementMatchingService requirementMatchingService;
    private final ResumeQualityService resumeQualityService;
    private final JDTailoringService jdTailoringService;
    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    public MatchAnalysisService(
            JobRequirementExtractionService requirementExtractionService,
            RequirementMatchingService requirementMatchingService,
            ResumeQualityService resumeQualityService,
            JDTailoringService jdTailoringService,
            ChatClient.Builder chatClientBuilder,
            VectorStore vectorStore
    ) {
        this.requirementExtractionService = requirementExtractionService;
        this.requirementMatchingService = requirementMatchingService;
        this.resumeQualityService = resumeQualityService;
        this.jdTailoringService = jdTailoringService;
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
    }

    public MatchResult analyze(String resumeId, String jobDescription) {

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description cannot be empty.");
        }

        List<JobRequirement> extractedRequirements =
                requirementExtractionService.extract(jobDescription);

        if (extractedRequirements == null || extractedRequirements.isEmpty()) {
            return emptyResult();
        }

        List<RequirementMatch> requirements =
                requirementMatchingService.matchRequirements(
                        extractedRequirements,
                        resumeId
                );

        // Legacy behavior: maintain old result for backward compatibility
        int jdAlignmentScore = calculateWeightedScore(requirements);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        for (RequirementMatch requirement : requirements) {
            if (requirement == null || requirement.requirement() == null) {
                continue;
            }

            if (requirement.status() == RequirementStatus.MATCHED) {
                matched.add(requirement.requirement());
            } else if (requirement.status() == RequirementStatus.NOT_EVIDENCED) {
                missing.add(requirement.requirement());
            }
        }

        String generatedAnalysis = generateAnalysis(
                requirements,
                jdAlignmentScore
        );

        String safeAnalysis = AnalysisGuard.verifyOrFallback(
                generatedAnalysis,
                requirements
        );

        List<String> recommendations = buildRecommendations(requirements);
        List<String> nextSteps = buildNextSteps(requirements);

        List<SkillMatch> legacySkillDetails = List.of();

        return new MatchResult(
                jdAlignmentScore,
                List.copyOf(matched),
                List.copyOf(missing),
                safeAnalysis,
                recommendations,
                legacySkillDetails,
                List.copyOf(requirements),
                nextSteps
        );
    }

    /**
     * New resume-first analysis: quality baseline + bounded JD signal.
     * Returns user-facing result without requirement details.
     */
    public TailoredMatchResult analyzeTailored(String resumeId, String jobDescription) {

        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description cannot be empty.");
        }

        // Retrieve resume chunks
        List<Document> resumeChunks = retrieveResumeChunks(resumeId);

        // Assess resume quality (baseline, JD-independent)
        ResumeQualityAssessment qualityAssessment = resumeQualityService.assessQuality(resumeChunks);

        // Extract and match JD requirements
        List<JobRequirement> extractedRequirements = requirementExtractionService.extract(jobDescription);
        List<RequirementMatch> requirements = requirementMatchingService.matchRequirements(
                extractedRequirements,
                resumeId
        );

        // Calculate JD alignment signal (bounded 0-30 points out of 100)
        int jdAlignmentPoints = calculateBoundedJDAlignment(requirements);

        // Combined score: resume baseline (70) + JD alignment signal (30)
        int overallScore = (int) (qualityAssessment.overallQualityScore() * 70.0) + jdAlignmentPoints;

        // Extract matched/missing for internal tracking
        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (RequirementMatch req : requirements) {
            if (req != null && req.requirement() != null) {
                if (req.status() == RequirementStatus.MATCHED) {
                    matched.add(req.requirement());
                } else if (req.status() == RequirementStatus.NOT_EVIDENCED) {
                    missing.add(req.requirement());
                }
            }
        }

        // Generate tailoring tips
        List<String> tailoringTips = jdTailoringService.generateTailoringTips(requirements, matched, missing, jobDescription);

        String scoreExplanation = buildScoreExplanation(overallScore, qualityAssessment.overallQualityScore(), jdAlignmentPoints);

        return new TailoredMatchResult(
                overallScore,
                scoreExplanation,
                qualityAssessment.strengths(),
                qualityAssessment.improvements(),
                tailoringTips,
                List.copyOf(requirements),
                matched,
                missing
        );
    }

    private List<Document> retrieveResumeChunks(String resumeId) {
        try {
            return vectorStore.similaritySearch(
                    org.springframework.ai.vectorstore.SearchRequest.builder()
                            .query("resume experience skills")
                            .topK(50)
                            .filterExpression("resumeId == '" + resumeId + "'")
                            .build()
            );
        } catch (Exception ex) {
            return List.of();
        }
    }

    private int calculateBoundedJDAlignment(List<RequirementMatch> requirements) {
        if (requirements == null || requirements.isEmpty()) {
            return 15;
        }

        int matched = 0;
        int total = 0;

        for (RequirementMatch req : requirements) {
            if (req == null || req.status() == RequirementStatus.UNASSESSED) {
                continue;
            }
            total++;
            if (req.status() == RequirementStatus.MATCHED) {
                matched++;
            }
        }

        if (total == 0) {
            return 15;
        }

        double alignmentRatio = (double) matched / total;
        // Cap at 30 points: bounded contribution
        return Math.min(30, (int) (alignmentRatio * 40.0));
    }

    private String buildScoreExplanation(int overall, double qualityScore, int jdPoints) {
        if (overall >= 85) {
            return "Strong resume with excellent JD alignment.";
        }
        if (overall >= 70) {
            return "Strong resume foundation with several opportunities to tailor it to this role.";
        }
        if (overall >= 55) {
            return "Resume shows relevant experience. Consider the tailoring suggestions below.";
        }
        return "Focus on strengthening the resume before tailoring for specific roles.";
    }

    private int calculateWeightedScore(List<RequirementMatch> requirements) {

        if (requirements == null || requirements.isEmpty()) {
            return 0;
        }

        double earned = 0.0;
        double possible = 0.0;

        for (RequirementMatch requirement : requirements) {

            if (requirement == null ||
                    requirement.status() == RequirementStatus.UNASSESSED) {
                continue;
            }

            double weight = importanceWeight(requirement);

            switch (requirement.status()) {
                case MATCHED -> {
                    possible += weight;
                    earned += weight;
                }
                case PARTIAL -> {
                    possible += weight;
                    earned += weight * 0.5;
                }
                case NOT_EVIDENCED -> {
                    possible += weight;
                    // zero earned
                }
                case NOT_VERIFIABLE, UNASSESSED -> {
                    // excluded from scoring
                }
            }
        }

        if (possible == 0.0) {
            return 0;
        }

        return (int) Math.round((earned / possible) * 100.0);
    }

    private double importanceWeight(RequirementMatch requirement) {

        if (requirement.importance() == null) {
            return 1.0;
        }

        return switch (requirement.importance()) {
            case HIGH -> 3.0;
            case MEDIUM -> 2.0;
            case LOW -> 1.0;
        };
    }

    private String generateAnalysis(
            List<RequirementMatch> requirements,
            int score
    ) {

        String structuredAssessment = requirements.stream()
                .map(this::formatRequirement)
                .reduce("", (a, b) -> a + b);

        String prompt = """
                Analyze the candidate against the verified job requirements.

                Deterministic match score: %d/100.

                VERIFIED REQUIREMENT ASSESSMENTS:
                %s

                Rules:
                - Treat the verified requirement assessments as authoritative.
                - Do not change MATCHED, PARTIAL, MISSING, or UNASSESSED statuses.
                - Do not invent candidate experience, technologies, projects,
                  qualifications, employers, durations, or achievements.
                - Evidence listed in the assessment is the only evidence you
                  may rely on.
                - For AND requirements, do not claim the complete requirement
                  is satisfied unless the deterministic assessment says MATCHED.
                - For OR requirements, do not describe unchosen alternatives
                  as missing when the deterministic assessment is MATCHED.
                - Distinguish partial fulfillment from full fulfillment.
                - Do not introduce requirements that are absent from the
                  verified assessment.
                - Keep the answer concise and recruiter-oriented.

                Return exactly these three sections:

                SUMMARY
                <overall assessment>

                STRENGTHS
                <verified strengths>

                GAPS
                <verified gaps and partial requirements>

                %s
                """.formatted(
                score,
                structuredAssessment,
                ""
        );

        try {
            return chatClient.prompt()
                    .system("""
                            You are a resume-job-description analysis assistant.

                            Your job is to explain an already-computed deterministic
                            assessment. You are NOT the matching engine.

                            Never override deterministic statuses or scores.
                            Never invent evidence.
                            """)
                    .user(prompt)
                    .call()
                    .content();

        } catch (Exception ignored) {
            return null;
        }
    }

    private String formatRequirement(RequirementMatch requirement) {

        StringBuilder builder = new StringBuilder();

        builder.append("- Requirement: ")
                .append(requirement.requirement())
                .append("\n");

        builder.append("  Type: ")
                .append(requirement.type())
                .append("\n");

        builder.append("  Importance: ")
                .append(requirement.importance())
                .append("\n");

        builder.append("  Status: ")
                .append(requirement.status())
                .append("\n");

        builder.append("  Experience verified: ")
                .append(requirement.experienceVerified())
                .append("\n");

        if (requirement.evidence() != null &&
                !requirement.evidence().isEmpty()) {

            builder.append("  Evidence:\n");

            orderEvidenceForExplanation(requirement.evidence())
                    .stream()
                    .limit(5)
                    .forEach(evidence ->
                            builder.append("    ")
                                    .append(evidence)
                                    .append("\n"));
        }

        return builder.toString();
    }

    static List<Evidence> orderEvidenceForExplanation(
            List<Evidence> evidence) {

        if (evidence == null || evidence.isEmpty()) {
            return List.of();
        }

        return evidence.stream()
                .filter(Objects::nonNull)
                .sorted(EXPLANATION_EVIDENCE_ORDER)
                .toList();
    }

    private static String normalizeEvidenceField(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> buildRecommendations(
            List<RequirementMatch> requirements
    ) {

        Set<String> recommendations = new LinkedHashSet<>();

        for (RequirementMatch requirement : requirements) {

            if (requirement == null ||
                    requirement.requirement() == null) {
                continue;
            }

            if (requirement.status() == RequirementStatus.NOT_EVIDENCED) {
                recommendations.add(
                        "Address the missing requirement: "
                                + requirement.requirement()
                );
            } else if (requirement.status() == RequirementStatus.PARTIAL) {
                recommendations.add(
                        "Strengthen evidence for: "
                                + requirement.requirement()
                );
            }
        }

        return List.copyOf(recommendations);
    }

    private List<String> buildNextSteps(
            List<RequirementMatch> requirements
    ) {

        List<String> steps = new ArrayList<>();

        for (RequirementMatch requirement : requirements) {

            if (requirement == null ||
                    requirement.requirement() == null) {
                continue;
            }

            if (requirement.status() == RequirementStatus.NOT_EVIDENCED) {
                steps.add(
                        "If you have relevant experience for \""
                                + requirement.requirement()
                                + "\", make it explicit in the resume."
                );
            }

            if (requirement.status() == RequirementStatus.PARTIAL) {
                steps.add(
                        "Add stronger, specific evidence for \""
                                + requirement.requirement()
                                + "\" where applicable."
                );
            }
        }

        return List.copyOf(steps);
    }

    private MatchResult emptyResult() {
        return new MatchResult(
                0,
                List.of(),
                List.of(),
                """
                        SUMMARY
                        No job requirements could be assessed.

                        STRENGTHS
                        No verified requirements were available.

                        GAPS
                        No requirements were available for comparison.
                        """,
                List.of(),
                List.of(),
                List.of(),
                List.of()
        );
    }
}
