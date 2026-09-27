package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.model.ExperienceCondition;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeterministicEvaluationTest {

    @Test
    void labeledRetrievalAndMatchingBenchmark() {
        List<BenchmarkCase> cases = List.of(
                match("direct-java", concept("Java"), RequirementImportance.HIGH,
                        Map.of("Java", docs("Built Java services.")), RequirementStatus.MATCHED),
                match("java-not-javascript", concept("Java"), RequirementImportance.HIGH,
                        Map.of("Java", docs("Built JavaScript interfaces.")), RequirementStatus.NOT_EVIDENCED),
                match("aws-semantic-paraphrase", concept("AWS"), RequirementImportance.MEDIUM,
                        Map.of("AWS", docs("Cloud infrastructure delivery.", 0.1)), RequirementStatus.MATCHED),
                match("cloud-anyof", anyOf("AWS", "Azure", "GCP"), RequirementImportance.MEDIUM,
                        Map.of("Azure", docs("Deployed workloads to Azure.")), RequirementStatus.MATCHED),
                match("nested-allof-anyof", new RequirementExpression.AllOf(List.of(
                        concept("Java"), anyOf("Azure", "GCP"))), RequirementImportance.HIGH,
                        Map.of("Java", docs("Java backend."), "GCP", docs("GCP platform.")), RequirementStatus.MATCHED),
                match("unknown-concept", concept("QuantumFlux orchestration"), RequirementImportance.LOW,
                        Map.of(), RequirementStatus.NOT_EVIDENCED),
                match("cpp-not-csharp", anyOf("C++", "C#"), RequirementImportance.HIGH,
                        Map.of("C++", docs("C++ systems engineer.")), RequirementStatus.MATCHED),
                experience("anyof-duration", anyOf("Java", "Python"), Map.of(
                        "Java", docs("Java developer with 3 years of experience.")), RequirementStatus.MATCHED),
                experience("anyof-duration-bound", anyOf("Java", "Python"), Map.of(
                        "Java", docs("Built Java services."),
                        "Python", docs("Senior engineer with 3 years of experience.")), RequirementStatus.PARTIAL),
                new BenchmarkCase("not-verifiable", requirement("Relocate", concept("relocate"),
                        RequirementImportance.LOW, null, Verifiability.NOT_VERIFIABLE), Map.of(), RequirementStatus.NOT_VERIFIABLE)
        );

        int truePositives = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int retrievalTargets = 0;
        int retrievedTargets = 0;

        for (BenchmarkCase benchmark : cases) {
            RequirementMatch actual = run(benchmark);
            assertEquals(benchmark.expected(), actual.status(), benchmark.name());

            boolean expectedMatch = benchmark.expected() == RequirementStatus.MATCHED;
            boolean actualMatch = actual.status() == RequirementStatus.MATCHED;
            if (expectedMatch) {
                retrievalTargets++;
                if (!actual.evidence().isEmpty()) {
                    retrievedTargets++;
                }
            }
            if (actualMatch && expectedMatch) truePositives++;
            if (actualMatch && !expectedMatch) falsePositives++;
            if (!actualMatch && expectedMatch) falseNegatives++;
        }

        double recallAt20 = (double) retrievedTargets / retrievalTargets;
        double precision = (double) truePositives / (truePositives + falsePositives);
        double verificationAccuracy = (double) (cases.size() - falsePositives - falseNegatives) / cases.size();
        System.out.printf("EVAL recall@20=%.2f precision=%.2f verificationAccuracy=%.2f fp=%d fn=%d%n",
                recallAt20, precision, verificationAccuracy, falsePositives, falseNegatives);
        assertEquals(1.0, recallAt20);
        assertEquals(1.0, precision);
        assertEquals(1.0, verificationAccuracy);
    }

    private RequirementMatch run(BenchmarkCase benchmark) {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation ->
                benchmark.candidates().getOrDefault(invocation.<SearchRequest>getArgument(0).getQuery(), List.of()));
        LexicalCandidateRetriever lexical = mock(LexicalCandidateRetriever.class);
        when(lexical.search(any(), any(), anyInt())).thenReturn(List.of());
        return new RequirementMatchingService(vectorStore, lexical)
                .matchRequirements(List.of(benchmark.requirement()), "evaluation-resume").get(0);
    }

    private BenchmarkCase match(String name, RequirementExpression expression,
            RequirementImportance importance, Map<String, List<Document>> candidates,
            RequirementStatus expected) {
        return new BenchmarkCase(name, requirement(name, expression, importance, null,
                Verifiability.VERIFIABLE), candidates, expected);
    }

    private BenchmarkCase experience(String name, RequirementExpression expression,
            Map<String, List<Document>> candidates, RequirementStatus expected) {
        return new BenchmarkCase(name, requirement(name, expression, RequirementImportance.MEDIUM,
                new ExperienceCondition(null, null, "3 years"), Verifiability.VERIFIABLE), candidates, expected);
    }

    private JobRequirement requirement(String text, RequirementExpression expression,
            RequirementImportance importance, ExperienceCondition experience,
            Verifiability verifiability) {
        return new JobRequirement(text, expression, experience == null ? RequirementType.SKILL : RequirementType.EXPERIENCE,
                importance, experience, verifiability);
    }

    private RequirementExpression concept(String name) { return new RequirementExpression.Concept(name); }
    private RequirementExpression anyOf(String... names) { return new RequirementExpression.AnyOf(java.util.Arrays.stream(names).map(this::concept).toList()); }
    private List<Document> docs(String text) { return docs(text, null); }
    private List<Document> docs(String text, Double distance) {
        Map<String, Object> metadata = distance == null ? Map.of("section", "Experience") : Map.of("section", "Experience", "distance", distance);
        return List.of(new Document(text, metadata));
    }

    private record BenchmarkCase(String name, JobRequirement requirement,
            Map<String, List<Document>> candidates, RequirementStatus expected) {}
}
