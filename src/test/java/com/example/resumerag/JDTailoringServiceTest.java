package com.example.resumerag;

import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.RequirementType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(tips.get(0).startsWith("If you have genuinely used"));
        assertTrue(tips.get(0).contains("Docker"));
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
        assertTrue(tips.get(0).contains("not explicit"));
    }

    @Test
    void emptyRequirementsYieldNoTips() {
        assertEquals(List.of(), service().generateTailoringTips(List.of(), List.of(), List.of(), "JD"));
    }

    private JDTailoringService service() {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(mock(ChatClient.class));
        return new JDTailoringService(builder);
    }
}
