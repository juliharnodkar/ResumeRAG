package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.model.ConceptAssessment;
import com.example.resumerag.model.Evidence;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.Verifiability;
import com.example.resumerag.skill.ExperienceDurationParser;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

@Service
public class RequirementMatchingService {

    private static final int TOP_K = 20;

    /*
     * PGVector is configured with cosine distance.
     *
     * distance:
     *   0.0 = identical
     *   1.0 = very dissimilar
     *
     * Convert distance into similarity:
     *
     *   similarity = 1 - distance
     */
    private static final double SEMANTIC_SIMILARITY_THRESHOLD = 0.50;

    private final VectorStore vectorStore;
    private final LexicalCandidateRetriever lexicalCandidateRetriever;

    public RequirementMatchingService(
            VectorStore vectorStore,
            LexicalCandidateRetriever lexicalCandidateRetriever) {
        this.vectorStore = vectorStore;
        this.lexicalCandidateRetriever = lexicalCandidateRetriever;
    }

    public List<RequirementMatch> matchRequirements(
            List<JobRequirement> requirements,
            String resumeId) {

        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }

        List<RequirementMatch> results = new ArrayList<>();

        for (JobRequirement requirement : requirements) {
            results.add(matchRequirement(requirement, resumeId));
        }

        return results;
    }

    private RequirementMatch matchRequirement(
            JobRequirement requirement,
            String resumeId) {

        if (requirement.verifiability() == Verifiability.NOT_VERIFIABLE) {

            return new RequirementMatch(
                    requirement.originalText(),
                    requirement.type(),
                    requirement.importance(),
                    RequirementStatus.NOT_VERIFIABLE,
                    requirement.expression(),
                    List.of(),
                    List.of(),
                    requirement.experienceCondition(),
                    false
            );
        }

        Map<String, List<Document>> candidateCache =
                new LinkedHashMap<>();

        Evaluation evaluation = evaluateExpression(
                requirement.expression(),
                resumeId,
                candidateCache);

        boolean experienceVerified =
                verifyExperience(
                        requirement,
                        evaluation.concepts(),
                        candidateCache);

        RequirementStatus status = evaluation.status();

        /*
         * Experience requirements have two independent parts:
         *
         * 1. Concept evidence exists.
         * 2. Required duration is verified.
         *
         * If concept evidence exists but duration cannot be verified,
         * classify the requirement as PARTIAL.
         */
        if (requirement.experienceCondition() != null) {

            Integer requiredMonths =
                    getRequiredExperienceMonths(requirement);

            if (requiredMonths != null) {

                if (experienceVerified) {

                    if (status == RequirementStatus.PARTIAL) {
                        status = RequirementStatus.MATCHED;
                    }

                } else if (status == RequirementStatus.MATCHED) {

                    status = RequirementStatus.PARTIAL;
                }
            }
        }

        return new RequirementMatch(
                requirement.originalText(),
                requirement.type(),
                requirement.importance(),
                status,
                requirement.expression(),
                evaluation.concepts(),
                evaluation.evidence(),
                requirement.experienceCondition(),
                experienceVerified);
    }

    private Evaluation evaluateExpression(
            RequirementExpression expression,
            String resumeId,
            Map<String, List<Document>> candidateCache) {

        if (expression == null) {
            return new Evaluation(
                    RequirementStatus.UNASSESSED,
                    List.of(),
                    List.of());
        }

        if (expression instanceof RequirementExpression.Concept concept) {
            return evaluateConcept(
                    concept.name(),
                    resumeId,
                    candidateCache);
        }

        if (expression instanceof RequirementExpression.AllOf allOf) {
            return evaluateAllOf(
                    allOf.children(),
                    resumeId,
                    candidateCache);
        }

        if (expression instanceof RequirementExpression.AnyOf anyOf) {
            return evaluateAnyOf(
                    anyOf.children(),
                    resumeId,
                    candidateCache);
        }

        return new Evaluation(
                RequirementStatus.UNASSESSED,
                List.of(),
                List.of());
    }

    private Evaluation evaluateConcept(
            String concept,
            String resumeId,
            Map<String, List<Document>> candidateCache) {

        if (concept == null || concept.isBlank()) {
            return new Evaluation(
                    RequirementStatus.UNASSESSED,
                    List.of(),
                    List.of());
        }

        List<Document> candidates =
                candidateCache.computeIfAbsent(
                        concept,
                        key -> searchRequirement(key, resumeId));

        List<Evidence> evidence =
                findEvidence(concept, candidates);

        RequirementStatus status =
                evidence.isEmpty()
                        ? RequirementStatus.NOT_EVIDENCED
                        : RequirementStatus.MATCHED;

        return new Evaluation(
                status,
                List.of(
                        new ConceptAssessment(
                                concept,
                                status,
                                evidence)),
                evidence);
    }

    private Evaluation evaluateAllOf(
            List<RequirementExpression> expressions,
            String resumeId,
            Map<String, List<Document>> candidateCache) {

        if (expressions == null || expressions.isEmpty()) {
            return new Evaluation(
                    RequirementStatus.UNASSESSED,
                    List.of(),
                    List.of());
        }

        List<ConceptAssessment> concepts =
                new ArrayList<>();

        List<Evidence> evidence =
                new ArrayList<>();

        int matched = 0;

        for (RequirementExpression expression : expressions) {

            Evaluation child =
                    evaluateExpression(
                    expression,
                    resumeId,
                    candidateCache);

            concepts.addAll(child.concepts());
            evidence.addAll(child.evidence());

            if (child.status() ==
                    RequirementStatus.MATCHED) {

                matched++;
            }
        }

        RequirementStatus status;

        if (matched == expressions.size()) {

            status = RequirementStatus.MATCHED;

        } else if (matched > 0) {

            status = RequirementStatus.PARTIAL;

        } else {

            status = RequirementStatus.NOT_EVIDENCED;
        }

        return new Evaluation(
                status,
                concepts,
                evidence);
    }

    private Evaluation evaluateAnyOf(
            List<RequirementExpression> expressions,
            String resumeId,
            Map<String, List<Document>> candidateCache) {

        if (expressions == null || expressions.isEmpty()) {
            return new Evaluation(
                    RequirementStatus.UNASSESSED,
                    List.of(),
                    List.of());
        }

        List<ConceptAssessment> concepts =
                new ArrayList<>();

        List<Evidence> evidence =
                new ArrayList<>();

        boolean anyMatched = false;
        boolean anyPartial = false;

        for (RequirementExpression expression : expressions) {

            Evaluation child =
                    evaluateExpression(
                    expression,
                    resumeId,
                    candidateCache);

            concepts.addAll(child.concepts());
            evidence.addAll(child.evidence());

            if (child.status() ==
                    RequirementStatus.MATCHED) {

                anyMatched = true;

            } else if (child.status() ==
                    RequirementStatus.PARTIAL) {

                anyPartial = true;
            }
        }

        RequirementStatus status;

        if (anyMatched) {

            status = RequirementStatus.MATCHED;

        } else if (anyPartial) {

            status = RequirementStatus.PARTIAL;

        } else {

            status = RequirementStatus.NOT_EVIDENCED;
        }

        return new Evaluation(
                status,
                concepts,
                evidence);
    }

    private List<Evidence> findEvidence(
            String concept,
            List<Document> candidates) {

        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<Evidence> matches =
                new ArrayList<>();

        String normalizedConcept =
                normalize(concept);

        for (Document document : candidates) {

            String text =
                    document.getText();

            if (text == null || text.isBlank()) {
                continue;
            }

            String normalizedText =
                    normalize(text);

            /*
             * First check for explicit textual evidence.
             *
             * Example:
             *
             * Resume:
             * "Built REST APIs using Spring Boot"
             *
             * Requirement:
             * "REST APIs"
             *
             * This is strong deterministic evidence.
             */
            boolean directMention = false;
            for (String query : ConceptQueryExpander.expand(concept)) {
                if (containsConcept(normalizedText, normalize(query))) {
                    directMention = true;
                    break;
                }
            }

            /*
             * PGVector exposes cosine DISTANCE.
             *
             * Lower distance = closer.
             *
             * Therefore:
             *
             * similarity = 1 - distance
             */
            double distance =
                    getDistance(document);

            double similarity =
                    calculateSimilarity(document);

            boolean semanticMatch =
                    similarity >=
                            SEMANTIC_SIMILARITY_THRESHOLD;

            if (directMention || semanticMatch) {

                matches.add(
                        buildEvidence(
                                document,
                                similarity,
                                directMention));
            }
        }

        return matches;
    }

    private Evidence buildEvidence(
            Document document,
            double similarity,
            boolean directMention) {

        Object sectionValue =
                document.getMetadata()
                        .get("section");

        Object projectValue =
                document.getMetadata()
                        .get("project");

        return new Evidence(
                document.getText(),
                sectionValue == null
                        ? null
                        : sectionValue.toString(),
                projectValue == null
                        ? null
                        : projectValue.toString(),
                similarity,
                directMention);
    }

    private boolean verifyExperience(
            JobRequirement requirement,
            List<ConceptAssessment> concepts,
            Map<String, List<Document>> candidateCache) {

        if (requirement.experienceCondition() == null) {
            return false;
        }

        Integer requiredMonths =
                getRequiredExperienceMonths(
                        requirement);

        if (requiredMonths == null) {
            return false;
        }

        /*
         * Duration evidence is independent from concept evidence.
         *
         * A resume may have:
         *
         *   "Software Engineer"
         *
         * in one chunk and:
         *
         *   "2021 - 2024"
         *
         * in another chunk.
         */
        int maximumMonths = 0;

        for (ConceptAssessment concept : concepts) {

            if (concept.status() != RequirementStatus.MATCHED) {
                continue;
            }

            for (Document document : candidateCache.getOrDefault(
                    concept.concept(), List.of())) {

                String text =
                        document.getText();

                if (text == null || text.isBlank()) {
                    continue;
                }

                Integer months =
                        ExperienceDurationParser
                                .parseMonths(text);

                if (months != null) {

                    maximumMonths =
                            Math.max(
                                    maximumMonths,
                                    months);
                }
            }
        }

        return maximumMonths >= requiredMonths;
    }

    private Integer getRequiredExperienceMonths(
            JobRequirement requirement) {

        Integer requiredMonths =
                requirement.experienceCondition()
                        .minimumMonths();

        if (requiredMonths != null) {
            return requiredMonths;
        }

        requiredMonths =
                ExperienceDurationParser.parseMonths(
                        requirement.experienceCondition()
                                .originalText());

        if (requiredMonths != null) {
            return requiredMonths;
        }

        return ExperienceDurationParser.parseMonths(
                requirement.originalText());
    }

    private List<Document> searchRequirement(
            String requirementText,
            String resumeId) {

        if (requirementText == null
                || requirementText.isBlank()) {

            return List.of();
        }

        List<String> queries = ConceptQueryExpander.expand(requirementText);
        Map<String, Document> mergedCandidates = new LinkedHashMap<>();

        try {
            for (String query : queries) {
                SearchRequest request =
                        SearchRequest.builder()
                                .query(query)
                                .topK(TOP_K)
                                .similarityThreshold(0.0)
                                .filterExpression(
                                        "resumeId == '"
                                        + escape(resumeId)
                                        + "'")
                                .build();

                List<Document> documents =
                        vectorStore.similaritySearch(
                                request);

                if (documents != null) {
                    for (Document document : documents) {
                        mergedCandidates.putIfAbsent(document.getId(), document);
                    }
                }
            }
        } catch (Exception ex) {
        }

        try {
            for (String query : queries) {
                List<Document> documents =
                        lexicalCandidateRetriever.search(
                                query,
                                resumeId,
                                TOP_K);

                if (documents != null) {
                    for (Document document : documents) {
                        mergedCandidates.putIfAbsent(document.getId(), document);
                    }
                }
            }
        } catch (Exception ex) {
        }

        List<Document> finalDocuments = List.copyOf(mergedCandidates.values());

        return finalDocuments;
    }

    private double calculateSimilarity(
            Document document) {

        Object distanceValue =
                document.getMetadata()
                        .get("distance");

        if (distanceValue instanceof Number number) {

            double distance =
                    number.doubleValue();

            return clamp(
                    1.0 - distance);
        }

        /*
         * Fallback in case a VectorStore implementation
         * provides a score but does not expose distance.
         */
        Double score =
                document.getScore();

        if (score != null) {
            return clamp(score);
        }

        return 0.0;
    }

    private double getDistance(
            Document document) {

        Object distanceValue =
                document.getMetadata()
                        .get("distance");

        if (distanceValue instanceof Number number) {
            return number.doubleValue();
        }

        return -1.0;
    }

    private boolean containsConcept(String normalizedText, String normalizedConcept) {
        if (normalizedText == null || normalizedConcept == null || normalizedConcept.isBlank()) {
            return false;
        }

        String text = " " + normalizedText.trim() + " ";
        String concept = " " + normalizedConcept.trim() + " ";

        return text.contains(concept);
    }

    private String normalize(String value) {

        return value
                .toLowerCase(Locale.ROOT)
                .replaceAll(
                        "[^a-z0-9+#]+",
                        " ")
                .replaceAll(
                        "\\s+",
                        " ")
                .trim();
    }

    private double clamp(double value) {

        if (value < 0.0) {
            return 0.0;
        }

        if (value > 1.0) {
            return 1.0;
        }

        return value;
    }

    private String escape(String value) {

        return value == null
                ? ""
                : value.replace(
                        "'",
                        "''");
    }

    private record Evaluation(
            RequirementStatus status,
            List<ConceptAssessment> concepts,
            List<Evidence> evidence) {
    }
}
