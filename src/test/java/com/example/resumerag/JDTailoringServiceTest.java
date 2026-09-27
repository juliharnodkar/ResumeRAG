package com.example.resumerag;

import com.example.resumerag.model.Evidence;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.RequirementType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JDTailoringServiceTest {

    @Test
    void generatesConditionalTipForMissingEvidence() {
        JDTailoringService service = service();
        RequirementMatch missing = new RequirementMatch(
                "Docker",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.NOT_EVIDENCED,
                new RequirementExpression.Concept("Docker"),
                List.of(),
                List.of(),
                null,
                false
        );

        List<String> tips = service.generateTailoringTips(
                List.of(missing), List.of(), List.of("Docker"), "Need Docker"
        );

        assertEquals(1, tips.size());
        assertTrue(tips.get(0).toLowerCase().contains("docker"));
        assertTrue(tips.get(0).toLowerCase().contains("if"));
        assertTrue(tips.get(0).startsWith("Add Docker if applicable: "));
    }

    @Test
    void generatesPartialTipFromRelatedEvidence() {
        JDTailoringService service = service();
        RequirementMatch partial = new RequirementMatch(
                "REST APIs",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.PARTIAL,
                new RequirementExpression.Concept("REST APIs"),
                List.of(),
                List.of(),
                null,
                false
        );

        List<String> tips = service.generateTailoringTips(
                List.of(partial), List.of(), List.of(), "Need REST APIs"
        );

        assertEquals(1, tips.size());
        assertTrue(tips.get(0).contains("not explicit") || tips.get(0).contains("not explicitly"));
    }

    @Test
    void emptyRequirementsYieldNoTips() {
        assertEquals(List.of(), service().generateTailoringTips(List.of(), List.of(), List.of(), "JD"));
    }

    @Test
    void notEvidencedTipUsesConditionalActionTitleAndSeparateBody() {
        JDTailoringService service = service();
        RequirementMatch missing = new RequirementMatch(
                "Kubernetes",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.NOT_EVIDENCED,
                new RequirementExpression.Concept("Kubernetes"),
                List.of(),
                List.of(),
                null,
                false
        );

        List<String> tips = service.generateTailoringTips(
                List.of(missing), List.of(), List.of("Kubernetes"), "Need Kubernetes"
        );

        assertEquals(1, tips.size());
        String tip = tips.get(0);

        assertTrue(tip.startsWith("Add Kubernetes if applicable: "), tip);
        assertTrue(tip.toLowerCase().contains("if"),
                "NOT_EVIDENCED tip must remain conditional: " + tip);
        assertTrue(tip.toLowerCase().contains("do not add") || tip.toLowerCase().contains("not add"),
                "Unsupported skills must stay conditional: " + tip);
    }

    @Test
    void partialTipAcknowledgesRelatedEvidenceAndAsksToClarify() {
        JDTailoringService service = service();
        RequirementMatch partial = new RequirementMatch(
                "Docker",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.PARTIAL,
                new RequirementExpression.Concept("Docker"),
                List.of(),
                List.of(),
                null,
                false
        );

        List<String> tips = service.generateTailoringTips(
                List.of(partial), List.of(), List.of(), "Need Docker"
        );

        assertEquals(1, tips.size());
        String tip = tips.get(0);

        assertTrue(tip.contains("not explicit") || tip.contains("not explicitly") || tip.contains("state it clearly"),
                "PARTIAL tip must say evidence is not explicit or ask to state it clearly: " + tip);
        assertFalse(tip.startsWith("Add "),
                "PARTIAL tip must NOT start with 'Add': " + tip);
    }

    @Test
    void matchedTipRefersToPresentEvidence() {
        JDTailoringService service = service();
        RequirementMatch matched = new RequirementMatch(
                "Java",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.MATCHED,
                new RequirementExpression.Concept("Java"),
                List.of(),
                List.of(),
                null,
                false
        );

        List<String> tips = service.generateTailoringTips(
                List.of(matched), List.of("Java"), List.of(), "Need Java"
        );

        assertEquals(1, tips.size());
        String tip = tips.get(0);

        assertTrue(tip.contains("already shows") || tip.contains("already"),
                "MATCHED tip must acknowledge the skill is already present: " + tip);
    }

    @Test
    void partialTipCanReferenceExistingResumeProject() {
        JDTailoringService service = service();
        RequirementMatch partial = new RequirementMatch(
                "PostgreSQL",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.PARTIAL,
                new RequirementExpression.Concept("PostgreSQL"),
                List.of(),
                List.of(new Evidence(
                        "Built a SQL database for student records",
                        "Projects",
                        "Student Portal",
                        0.62,
                        false
                )),
                null,
                false
        );
        List<Document> chunks = List.of(new Document(
                "Student Portal | SQL, Java. Built a SQL database for student records.",
                Map.of("section", "Projects", "project", "Student Portal")
        ));

        List<String> tips = service.generateTailoringTips(
                List.of(partial), List.of(), List.of(), "Need PostgreSQL", chunks
        );

        assertEquals(1, tips.size());
        String tip = tips.get(0);
        assertTrue(tip.contains("Student Portal"), tip);
        assertTrue(tip.contains("PostgreSQL"), tip);
        assertTrue(tip.toLowerCase().contains("if"), tip);
        assertFalse(tip.toLowerCase().contains("if you have genuinely used postgresql, consider adding it"));
    }

    @Test
    void tailoringTipUsesASeparateTitleAndBody() {
        RequirementMatch missing = new RequirementMatch(
                "front-desk check-in/check-out", RequirementType.EXPERIENCE,
                RequirementImportance.HIGH, RequirementStatus.NOT_EVIDENCED,
                new RequirementExpression.Concept("front-desk check-in/check-out"),
                List.of(), List.of(), null, false
        );
        List<Document> chunks = List.of(new Document(
                "Hotel operations experience included guest service, requests, complaints, and team training.",
                Map.of("section", "Experience")
        ));

        String tip = service().generateTailoringTips(
                List.of(missing), List.of(), List.of(), "Front-desk duties", chunks).getFirst();

        int separator = tip.indexOf(": ");
        assertTrue(separator > 0, tip);
        assertNotEquals(tip.substring(0, separator), tip.substring(separator + 2), tip);
        assertTrue(tip.toLowerCase().contains("if applicable"), tip);
        assertTrue(tip.toLowerCase().contains("not explicitly"), tip);
        assertFalse(tip.substring(tip.indexOf(": ") + 2).toLowerCase()
                .contains("front-desk check-in/check-out"), tip);
    }

    @Test
    void unsupportedSkillRemainsConditionalAndDoesNotInventAProject() {
        JDTailoringService service = service();
        RequirementMatch missing = new RequirementMatch(
                "Kubernetes",
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                RequirementStatus.NOT_EVIDENCED,
                new RequirementExpression.Concept("Kubernetes"),
                List.of(),
                List.of(),
                null,
                false
        );
        List<Document> chunks = List.of(new Document(
                "JES | Flask, JavaScript, Tesseract. Built an OCR workflow.",
                Map.of("section", "Projects", "project", "JES")
        ));

        String tip = service.generateTailoringTips(
                List.of(missing), List.of(), List.of("Kubernetes"), "Need Kubernetes", chunks
        ).get(0);

        assertTrue(tip.toLowerCase().contains("if"));
        assertFalse(tip.contains("Your JES project demonstrates Kubernetes"));
        assertFalse(tip.toLowerCase().contains("you used kubernetes"));
    }

    @Test
    void maxFiveTipsAreGenerated() {
        JDTailoringService service = service();
        List<RequirementMatch> manyMissing = List.of(
                missing("A"), missing("B"), missing("C"),
                missing("D"), missing("E"), missing("F"), missing("G")
        );

        List<String> tips = service.generateTailoringTips(manyMissing, List.of(), List.of(), "JD");
        assertTrue(tips.size() <= 5, "At most 5 tips must be generated, got: " + tips.size());
    }

    private RequirementMatch missing(String name) {
        return new RequirementMatch(
                name, RequirementType.SKILL, RequirementImportance.MEDIUM,
                RequirementStatus.NOT_EVIDENCED,
                new RequirementExpression.Concept(name),
                List.of(), List.of(), null, false
        );
    }

    private JDTailoringService service() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new JDTailoringService(builder);
    }
}
