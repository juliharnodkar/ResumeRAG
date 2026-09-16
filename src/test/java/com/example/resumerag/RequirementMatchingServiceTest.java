package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.ExperienceCondition;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RequirementMatchingServiceTest {

    private static final String RESUME_ID =
            "00000000-0000-0000-0000-000000000001";

    private RequirementMatchingService matchingService(VectorStore vectorStore) {
        return matchingService(vectorStore, emptyLexicalRetriever());
    }

    private RequirementMatchingService matchingService(
            VectorStore vectorStore,
            LexicalCandidateRetriever lexicalCandidateRetriever) {
        return new RequirementMatchingService(
                vectorStore,
                lexicalCandidateRetriever);
    }

    private LexicalCandidateRetriever emptyLexicalRetriever() {
        LexicalCandidateRetriever lexical =
                mock(LexicalCandidateRetriever.class);
        when(lexical.search(any(), any(), anyInt()))
                .thenReturn(List.of());
        return lexical;
    }

    private JobRequirement skillRequirement(
            String text,
            RequirementExpression expression,
            RequirementImportance importance) {

        return new JobRequirement(
                text,
                expression,
                RequirementType.SKILL,
                importance,
                null
        );
    }

    private JobRequirement experienceRequirement(
            String text,
            RequirementExpression expression,
            RequirementImportance importance,
            String experienceText) {

        return new JobRequirement(
                text,
                expression,
                RequirementType.EXPERIENCE,
                importance,
                new ExperienceCondition(null, null, experienceText)
        );
    }

    private RequirementExpression concept(String name) {
        return new RequirementExpression.Concept(name);
    }

    private RequirementExpression allOf(String... concepts) {
        return new RequirementExpression.AllOf(
                java.util.Arrays.stream(concepts)
                        .map(this::concept)
                        .toList()
        );
    }

    private RequirementExpression anyOf(String... concepts) {
        return new RequirementExpression.AnyOf(
                java.util.Arrays.stream(concepts)
                        .map(this::concept)
                        .toList()
        );
    }

    @Test
    void andRequirementCanBePartial() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Developed applications using Java.",
                                java.util.Map.of("section", "Projects")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = skillRequirement(
                "Experience with Java and Spring Boot",
                allOf("Java", "Spring Boot"),
                RequirementImportance.HIGH
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("PARTIAL", results.get(0).status().name());
        assertFalse(results.get(0).concepts().isEmpty());
        assertFalse(results.get(0).evidence().isEmpty());
    }

    @Test
    void orRequirementIsSatisfiedByOneAlternative() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Built backend services using Python.",
                                java.util.Map.of("section", "Projects")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = skillRequirement(
                "Knowledge of Java, Python, or C++",
                anyOf("Java", "Python", "C++"),
                RequirementImportance.HIGH
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertFalse(results.get(0).concepts().isEmpty());
        assertFalse(results.get(0).evidence().isEmpty());
    }

    @Test
    void zeroMatchReturnsMissingInsteadOfFailing() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Developed applications using Java and MySQL.",
                                java.util.Map.of("section", "Projects")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = skillRequirement(
                "Experience with Rust",
                concept("Rust"),
                RequirementImportance.HIGH
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("NOT_EVIDENCED", results.get(0).status().name());
        assertTrue(results.get(0).evidence().isEmpty());
    }

    @Test
    void experienceRequirementIsPartialWhenDurationCannotBeVerified() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Built backend applications using Java.",
                                java.util.Map.of("section", "Projects")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "2+ years of Java experience",
                concept("Java"),
                RequirementImportance.HIGH,
                "2+ years"
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("PARTIAL", results.get(0).status().name());
        assertFalse(results.get(0).experienceVerified());
        assertFalse(results.get(0).concepts().isEmpty());
    }

    @Test
    void experienceRequirementMatchesWhenDurationIsVerified() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Java Developer - 3 years of experience building backend applications.",
                                java.util.Map.of("section", "Experience")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "2+ years of Java experience",
                concept("Java"),
                RequirementImportance.HIGH,
                "2+ years"
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertTrue(results.get(0).experienceVerified());
        assertFalse(results.get(0).concepts().isEmpty());
        assertFalse(results.get(0).evidence().isEmpty());
    }

    @Test
    void experienceSkillAndDurationCanComeFromDifferentChunks() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Built backend applications using Java.",
                                java.util.Map.of("section", "Projects")
                        ),
                        new Document(
                                "Software Engineer - 3 years of professional experience.",
                                java.util.Map.of("section", "Experience")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "2+ years of Java experience",
                concept("Java"),
                RequirementImportance.HIGH,
                "2+ years"
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertTrue(results.get(0).experienceVerified());
        assertFalse(results.get(0).concepts().isEmpty());
        assertFalse(results.get(0).evidence().isEmpty());
    }

    @Test
    void orExperienceRequirementMustVerifySelectedAlternativeDuration() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Built backend applications using Python.",
                                java.util.Map.of("section", "Projects")
                        ),
                        new Document(
                                "Python Developer - 1 year of professional experience.",
                                java.util.Map.of("section", "Experience")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "2+ years of experience with Java, Python, or C++",
                anyOf("Java", "Python", "C++"),
                RequirementImportance.HIGH,
                "2+ years"
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("PARTIAL", results.get(0).status().name());
        assertFalse(results.get(0).experienceVerified());
        assertFalse(results.get(0).concepts().isEmpty());
    }

    @Test
    void durationFromAnUnmatchedAnyOfAlternativeDoesNotVerifyExperience() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenAnswer(invocation -> {
                    SearchRequest request = invocation.getArgument(0);

                    if (request.getQuery().equals("Java")) {
                        return List.of(new Document(
                                "Built backend applications using Java.",
                                java.util.Map.of("section", "Projects")
                        ));
                    }

                    if (request.getQuery().equals("Python")) {
                        return List.of(new Document(
                                "Senior engineer with 3 years of professional experience.",
                                java.util.Map.of("section", "Experience")
                        ));
                    }

                    return List.of();
                });

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "3 years of experience with Java or Python",
                anyOf("Java", "Python"),
                RequirementImportance.HIGH,
                "3 years"
        );

        RequirementMatch result = service.matchRequirements(
                List.of(requirement), RESUME_ID).get(0);

        assertEquals("PARTIAL", result.status().name());
        assertFalse(result.experienceVerified());
    }

    @Test
    void orExperienceRequirementIsMatchedWhenSelectedAlternativeHasEnoughDuration() {
        VectorStore vectorStore = mock(VectorStore.class);

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "Built backend applications using Python.",
                                java.util.Map.of("section", "Projects")
                        ),
                        new Document(
                                "Python Developer - 3 years of professional experience.",
                                java.util.Map.of("section", "Experience")
                        )
                ));

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = experienceRequirement(
                "2+ years of experience with Java, Python, or C++",
                anyOf("Java", "Python", "C++"),
                RequirementImportance.HIGH,
                "2+ years"
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertTrue(results.get(0).experienceVerified());
        assertFalse(results.get(0).concepts().isEmpty());
        assertFalse(results.get(0).evidence().isEmpty());
    }

    @Test
    void queryExpansionSendsMultipleRequestsAndDeduplicatesResults() {
        VectorStore vectorStore = mock(VectorStore.class);

        org.mockito.ArgumentCaptor<SearchRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(SearchRequest.class);

        Document doc1 = new Document(
                "doc1",
                "Built microservices with Spring Boot.",
                java.util.Map.of("section", "Projects")
        );
        Document doc2 = new Document(
                "doc2",
                "Migrated to spring-boot in 2021.",
                java.util.Map.of("section", "Experience")
        );

        when(vectorStore.similaritySearch(requestCaptor.capture()))
                .thenAnswer(invocation -> {
                    SearchRequest request = invocation.getArgument(0);
                    if (request.getQuery().equals("Spring Boot")) {
                        return List.of(doc1);
                    } else if (request.getQuery().equals("springboot")) {
                        return List.of(doc1); // Duplicate document
                    } else if (request.getQuery().equals("spring-boot")) {
                        return List.of(doc2); // Unique document
                    }
                    return List.of();
                });

        RequirementMatchingService service =
                matchingService(vectorStore);

        JobRequirement requirement = skillRequirement(
                "Experience with Spring Boot",
                concept("Spring Boot"),
                RequirementImportance.HIGH
        );

        List<RequirementMatch> results =
                service.matchRequirements(List.of(requirement), RESUME_ID);

        List<SearchRequest> capturedRequests = requestCaptor.getAllValues();
        
        // Verify bounded expansion produced the 3 exact queries
        assertEquals(3, capturedRequests.size());
        assertEquals("Spring Boot", capturedRequests.get(0).getQuery());
        assertEquals("springboot", capturedRequests.get(1).getQuery());
        assertEquals("spring-boot", capturedRequests.get(2).getQuery());

        // Verify invariants on every request
        for (SearchRequest req : capturedRequests) {
            assertEquals(20, req.getTopK());
            assertEquals(0.0, req.getSimilarityThreshold());
            assertTrue(req.getFilterExpression().toString().contains(RESUME_ID), 
                    "Filter must contain the exact expected RESUME_ID");
        }

        // Verify deduplication (2 unique docs from 3 requests)
        assertEquals(1, results.size());
        assertEquals(2, results.get(0).evidence().size());
    }

    @Test
    void lexicalOnlyExactMentionIsMatchedThroughEvidenceVerification() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        LexicalCandidateRetriever lexical =
                mock(LexicalCandidateRetriever.class);
        when(lexical.search(eq("Java"), eq(RESUME_ID), eq(20)))
                .thenReturn(List.of(
                        Document.builder()
                                .id("lex-java")
                                .text("Developed applications using Java.")
                                .metadata(java.util.Map.of("section", "Projects"))
                                .build()
                ));

        RequirementMatchingService service =
                matchingService(vectorStore, lexical);

        List<RequirementMatch> results = service.matchRequirements(
                List.of(skillRequirement(
                        "Experience with Java",
                        concept("Java"),
                        RequirementImportance.HIGH)),
                RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertEquals(1, results.get(0).evidence().size());
        assertEquals(0.0, results.get(0).evidence().get(0).relevance());
        assertTrue(results.get(0).evidence().get(0).directMention());
    }

    @Test
    void semanticOnlyEvidenceIsMarkedNonDirectAndKeepsSimilarity() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        Document.builder()
                                .id("semantic-java")
                                .text("Worked extensively in the JVM ecosystem.")
                                .metadata(java.util.Map.of(
                                        "section", "Experience",
                                        "distance", 0.09))
                                .build()
                ));

        RequirementMatchingService service =
                matchingService(vectorStore, emptyLexicalRetriever());

        List<RequirementMatch> results = service.matchRequirements(
                List.of(skillRequirement(
                        "Experience with Java",
                        concept("Java"),
                        RequirementImportance.HIGH)),
                RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertEquals(1, results.get(0).evidence().size());
        assertFalse(results.get(0).evidence().get(0).directMention());
        assertEquals(0.91, results.get(0).evidence().get(0).relevance(), 1e-9);
    }

    @Test
    void lexicalOnlyHitWithoutDirectMentionIsNotEvidenced() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        LexicalCandidateRetriever lexical =
                mock(LexicalCandidateRetriever.class);
        when(lexical.search(eq("Java"), eq(RESUME_ID), eq(20)))
                .thenReturn(List.of(
                        Document.builder()
                                .id("lex-js")
                                .text("Experience with JavaScript.")
                                .metadata(java.util.Map.of("section", "Skills"))
                                .build()
                ));

        RequirementMatchingService service =
                matchingService(vectorStore, lexical);

        List<RequirementMatch> results = service.matchRequirements(
                List.of(skillRequirement(
                        "Experience with Java",
                        concept("Java"),
                        RequirementImportance.HIGH)),
                RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("NOT_EVIDENCED", results.get(0).status().name());
        assertTrue(results.get(0).evidence().isEmpty());
    }

    @Test
    void denseAndLexicalDuplicateIdsPreserveTheDenseDocument() {
        VectorStore vectorStore = mock(VectorStore.class);
        Document dense = Document.builder()
                .id("shared")
                .text("Developed applications using Java.")
                .metadata(java.util.Map.of(
                        "section", "Projects",
                        "distance", 0.3))
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(dense));

        LexicalCandidateRetriever lexical =
                mock(LexicalCandidateRetriever.class);
        when(lexical.search(any(), eq(RESUME_ID), eq(20)))
                .thenReturn(List.of(
                        Document.builder()
                                .id("shared")
                                .text("This lexical copy must not replace dense.")
                                .metadata(java.util.Map.of("section", "Skills"))
                                .build()
                ));

        RequirementMatchingService service =
                matchingService(vectorStore, lexical);

        List<RequirementMatch> results = service.matchRequirements(
                List.of(skillRequirement(
                        "Experience with Java",
                        concept("Java"),
                        RequirementImportance.HIGH)),
                RESUME_ID);

        assertEquals(1, results.size());
        assertEquals("MATCHED", results.get(0).status().name());
        assertEquals(1, results.get(0).evidence().size());
        assertEquals(
                "Developed applications using Java.",
                results.get(0).evidence().get(0).text());
        assertEquals(0.7, results.get(0).evidence().get(0).relevance(), 1e-9);
    }

    @Test
    void lexicalSearchIsScopedToResumeIdAndUsesExpandedQueries() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of());

        LexicalCandidateRetriever lexical =
                mock(LexicalCandidateRetriever.class);
        when(lexical.search(any(), any(), anyInt()))
                .thenReturn(List.of());

        matchingService(vectorStore, lexical).matchRequirements(
                List.of(skillRequirement(
                        "Experience with Node.js",
                        concept("Node.js"),
                        RequirementImportance.HIGH)),
                RESUME_ID);

        org.mockito.ArgumentCaptor<String> queryCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<String> resumeCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);

        verify(lexical, times(3)).search(
                queryCaptor.capture(),
                resumeCaptor.capture(),
                eq(20));

        assertEquals(
                ConceptQueryExpander.expand("Node.js"),
                queryCaptor.getAllValues());
        assertTrue(resumeCaptor.getAllValues().stream()
                .allMatch(RESUME_ID::equals));
        verify(lexical, never()).search(any(), isNull(), anyInt());
    }

    @Test
    void diagnosticTest() {
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        new Document(
                                "ML-based film recommendation project.",
                                java.util.Map.of("section", "Projects", "distance", 0.6))
                ));

        RequirementMatchingService service =
                matchingService(vectorStore, emptyLexicalRetriever());

        JobRequirement req1 = skillRequirement(
                "Experience building machine learning or NLP applications",
                concept("Experience building machine learning or NLP applications"),
                RequirementImportance.HIGH
        );

        RequirementMatch result = service.matchRequirements(List.of(req1), RESUME_ID).get(0);
        System.out.println("DIAGNOSTIC 1: " + result.status());
    }
}
