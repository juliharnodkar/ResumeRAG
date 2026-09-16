package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.example.resumerag.model.Evidence;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.RequirementType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MatchAnalysisServiceTest {


@Test
void allMatchedRequirementsProduceFullScore() {
    RequirementMatch skill = requirement(
            "Java",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED
    );

    RequirementMatch project = requirement(
            "Backend project experience",
            RequirementType.PROJECT,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED
    );

    MatchAnalysisService service = createService();

    assertEquals(
            100,
            calculateScore(service, List.of(skill, project))
    );
}

@Test
void missingHighImportanceRequirementReducesScore() {
    RequirementMatch matched = requirement(
            "Java",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED
    );

    RequirementMatch missing = requirement(
            "Kubernetes",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.NOT_EVIDENCED
    );

    MatchAnalysisService service = createService();

    int score = calculateScore(service, List.of(matched, missing));

    assertTrue(score > 0);
    assertTrue(score < 100);
}

@Test
void partialAndRequirementGetsPartialCredit() {
    RequirementMatch partial = requirement(
            "Java and Spring Boot",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.PARTIAL
    );

    MatchAnalysisService service = createService();

    assertEquals(
            50,
            calculateScore(service, List.of(partial))
    );
}

@Test
void orRequirementIsFullySatisfiedByOneAlternative() {
    RequirementMatch matched = requirement(
            "Java, Python, or C++",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED
    );

    MatchAnalysisService service = createService();

    assertEquals(
            100,
            calculateScore(service, List.of(matched))
    );
}

@Test
void experienceAffectsScoreWhenRoleIsExperienceHeavy() {
    RequirementMatch skill = requirement(
            "Java",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED
    );

    RequirementMatch experience = requirement(
            "2+ years of Java experience",
            RequirementType.EXPERIENCE,
            RequirementImportance.HIGH,
            RequirementStatus.NOT_EVIDENCED
    );

    MatchAnalysisService service = createService();

    int score = calculateScore(
            service,
            List.of(skill, experience)
    );

    assertTrue(score < 100);
    assertTrue(score > 0);
}

@Test
void mixedImportanceStatusesUseWeightedVerifiedRequirementStatuses() {
    List<RequirementMatch> requirements = List.of(
            requirement("High", RequirementType.SKILL,
                    RequirementImportance.HIGH, RequirementStatus.MATCHED),
            requirement("Medium", RequirementType.SKILL,
                    RequirementImportance.MEDIUM, RequirementStatus.PARTIAL),
            requirement("Low", RequirementType.SKILL,
                    RequirementImportance.LOW, RequirementStatus.NOT_EVIDENCED),
            requirement("Excluded", RequirementType.OTHER,
                    RequirementImportance.HIGH, RequirementStatus.NOT_VERIFIABLE),
            requirement("Unassessed", RequirementType.OTHER,
                    RequirementImportance.MEDIUM, RequirementStatus.UNASSESSED)
    );

    assertEquals(67, calculateScore(createService(), requirements));
}

@Test
void duplicateEvidenceDoesNotChangeRequirementStatusScore() {
    List<Evidence> duplicateEvidence = List.of(
            evidence("Java", "Skills", null, 0.9, true),
            evidence("Java", "Skills", null, 0.9, true)
    );
    RequirementMatch matched = requirement(
            "Java", RequirementType.SKILL, RequirementImportance.HIGH,
            RequirementStatus.MATCHED, duplicateEvidence);

    assertEquals(100, calculateScore(createService(), List.of(matched)));
}

@Test
void nestedExpressionScoreUsesOnlyItsVerifiedStatus() {
    RequirementExpression expression = new RequirementExpression.AllOf(
            List.of(
                    new RequirementExpression.Concept("Java"),
                    new RequirementExpression.AnyOf(List.of(
                            new RequirementExpression.Concept("PostgreSQL"),
                            new RequirementExpression.Concept("MySQL")
                    ))
            )
    );
    RequirementMatch verifiedPartial = new RequirementMatch(
            "Java and PostgreSQL or MySQL",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.PARTIAL,
            expression,
            List.of(),
            List.of(
                    evidence("Java", "Skills", null, 0.9, true),
                    evidence("PostgreSQL", "Skills", null, 0.9, true)
            ),
            null,
            false
    );

    assertEquals(50, calculateScore(
            createService(), List.of(verifiedPartial)));
}

@Test
void directLexicalEvidenceOutranksHigherScoringSemanticEvidence() {
    Evidence semantic = evidence(
            "Semantic evidence",
            "Experience",
            "Platform",
            0.91,
            false
    );
    Evidence directLexical = evidence(
            "Direct lexical evidence",
            "Skills",
            null,
            0.0,
            true
    );

    List<Evidence> original = List.of(semantic, directLexical);
    List<Evidence> ordered =
            MatchAnalysisService.orderEvidenceForExplanation(original);

    assertEquals(List.of(directLexical, semantic), ordered);
    assertEquals(List.of(semantic, directLexical), original);
}

@Test
void semanticEvidenceIsOrderedByDescendingRelevance() {
    Evidence low = evidence("Low", "Skills", null, 0.60, false);
    Evidence high = evidence("High", "Skills", null, 0.91, false);
    Evidence middle = evidence("Middle", "Skills", null, 0.75, false);

    assertEquals(
            List.of(high, middle, low),
            MatchAnalysisService.orderEvidenceForExplanation(
                    List.of(low, high, middle))
    );
}

@Test
void nullEvidenceEntriesAreIgnoredWhenOrderingExplanationContext() {
    Evidence evidence = evidence("Java", null, null, 0.91, false);

    assertEquals(
            List.of(evidence),
            MatchAnalysisService.orderEvidenceForExplanation(
                    java.util.Arrays.asList(null, evidence))
    );
}

@Test
void equalRelevanceUsesNormalizedSectionProjectAndTextTieBreakers() {
    Evidence sectionLast = evidence("Text C", "Beta", "Alpha", 0.80, false);
    Evidence projectLast = evidence("Text B", "alpha", "Zulu", 0.80, false);
    Evidence textFirst = evidence("text a", " ALPHA ", "zulu", 0.80, false);

    assertEquals(
            List.of(textFirst, projectLast, sectionLast),
            MatchAnalysisService.orderEvidenceForExplanation(
                    List.of(sectionLast, projectLast, textFirst))
    );
}

@Test
void onlyFiveHighestRankedEvidenceItemsEnterFormattedContext() {
    List<Evidence> evidence = List.of(
            evidence("Sixth", "Skills", null, 0.10, false),
            evidence("First", "Skills", null, 0.95, false),
            evidence("Second", "Skills", null, 0.90, false),
            evidence("Third", "Skills", null, 0.85, false),
            evidence("Fourth", "Skills", null, 0.80, false),
            evidence("Fifth", "Skills", null, 0.75, false)
    );
    RequirementMatch requirement = requirement(
            "Java",
            RequirementType.SKILL,
            RequirementImportance.HIGH,
            RequirementStatus.MATCHED,
            evidence
    );

    String formatted = formatRequirement(createService(), requirement);

    assertTrue(formatted.indexOf("First") < formatted.indexOf("Second"));
    assertTrue(formatted.indexOf("Second") < formatted.indexOf("Third"));
    assertTrue(formatted.indexOf("Third") < formatted.indexOf("Fourth"));
    assertTrue(formatted.indexOf("Fourth") < formatted.indexOf("Fifth"));
    assertFalse(formatted.contains("Sixth"));
    assertEquals("Sixth", requirement.evidence().get(0).text());
    assertEquals(RequirementStatus.MATCHED, requirement.status());
    assertEquals(100, calculateScore(createService(), List.of(requirement)));
}

private RequirementMatch requirement(
        String text,
        RequirementType type,
        RequirementImportance importance,
        RequirementStatus status) {

    return requirement(text, type, importance, status, List.of());
}

private RequirementMatch requirement(
        String text,
        RequirementType type,
        RequirementImportance importance,
        RequirementStatus status,
        List<Evidence> evidence) {

    return new RequirementMatch(
            text,
            type,
            importance,
            status,
            null,
            List.of(),
            evidence,
            null,
            false
    );
}

private Evidence evidence(
        String text,
        String section,
        String project,
        double relevance,
        boolean directMention) {

    return new Evidence(text, section, project, relevance, directMention);
}

private String formatRequirement(
        MatchAnalysisService service,
        RequirementMatch requirement) {

    try {
        Method method = MatchAnalysisService.class
                .getDeclaredMethod(
                        "formatRequirement",
                        RequirementMatch.class
                );

        method.setAccessible(true);

        return (String) method.invoke(service, requirement);

    } catch (ReflectiveOperationException e) {
        throw new AssertionError(
                "Could not invoke evidence formatting method",
                e
        );
    }
}

private int calculateScore(
        MatchAnalysisService service,
        List<RequirementMatch> requirements) {

    try {
        Method method = MatchAnalysisService.class
                .getDeclaredMethod(
                        "calculateWeightedScore",
                        List.class
                );

        method.setAccessible(true);

        return (int) method.invoke(service, requirements);

    } catch (ReflectiveOperationException e) {
        throw new AssertionError(
                "Could not invoke deterministic scoring method",
                e
        );
    }
}

private MatchAnalysisService createService() {
    JobRequirementExtractionService extraction =
            mock(JobRequirementExtractionService.class);

    RequirementMatchingService matching =
            mock(RequirementMatchingService.class);

    ChatClient.Builder builder =
            mock(ChatClient.Builder.class);

    ChatClient chatClient =
            mock(ChatClient.class);

    when(builder.build()).thenReturn(chatClient);
    when(builder.defaultSystem(anyString())).thenReturn(builder);

    ResumeQualityService qualityService = mock(ResumeQualityService.class);
    JDTailoringService tailoringService = mock(JDTailoringService.class);
    org.springframework.ai.vectorstore.VectorStore vectorStore = mock(org.springframework.ai.vectorstore.VectorStore.class);

    return new MatchAnalysisService(
            extraction,
            matching,
            qualityService,
            tailoringService,
            builder,
            vectorStore
    );
}

}
