package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class JobRequirementExtractionDomainIndependenceTest {

    @Test
    void extractHandlesNonTechnicalJobRequirements() {

        String modelResponse = """
            [
              {
                "originalText": "3+ years of project management experience",
                "expression": {
                  "type": "Concept",
                  "name": "project management"
                },
                "type": "EXPERIENCE",
                "importance": "HIGH",
                "experienceRequirement": "3+ years",
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "Experience with stakeholder communication and budget planning",
                "expression": {
                  "type": "AllOf",
                  "children": [
                    {"type": "Concept", "name": "stakeholder communication"},
                    {"type": "Concept", "name": "budget planning"}
                  ]
                },
                "type": "SKILL",
                "importance": "HIGH",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "Strong presentation or public speaking skills",
                "expression": {
                  "type": "AnyOf",
                  "children": [
                    {"type": "Concept", "name": "presentation"},
                    {"type": "Concept", "name": "public speaking"}
                  ]
                },
                "type": "SKILL",
                "importance": "MEDIUM",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "Willingness to travel up to 25% of the time",
                "expression": {
                  "type": "Concept",
                  "name": "willingness to travel"
                },
                "type": "OTHER",
                "importance": "LOW",
                "experienceRequirement": null,
                "verifiability": "NOT_VERIFIABLE"
              },
              {
                "originalText": "Bachelor's degree in Business Administration or Finance",
                "expression": {
                  "type": "AllOf",
                  "children": [
                    {"type": "Concept", "name": "Bachelor's degree"},
                    {
                      "type": "AnyOf",
                      "children": [
                        {"type": "Concept", "name": "Business Administration"},
                        {"type": "Concept", "name": "Finance"}
                      ]
                    }
                  ]
                },
                "type": "EDUCATION",
                "importance": "MEDIUM",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              }
            ]
            """;

        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec =
                mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec =
                mock(ChatClient.CallResponseSpec.class);

        when(builder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(modelResponse);

        JobRequirementExtractionService service =
                new JobRequirementExtractionService(
                        builder,
                        new ObjectMapper()
                );

        List<JobRequirement> requirements =
                service.extract("""
                    We are hiring a project manager responsible for
                    stakeholder communication, budget planning, presentations,
                    and public speaking. Candidates should have 3+ years of
                    project management experience and a Bachelor's degree in
                    Business Administration or Finance. The role requires
                    willingness to travel up to 25% of the time.
                    """);

        assertEquals(5, requirements.size());

        JobRequirement experience = requirements.get(0);

        assertEquals(
                RequirementType.EXPERIENCE,
                experience.type()
        );

        assertEquals(
                RequirementImportance.HIGH,
                experience.importance()
        );

        assertEquals(
                "3+ years",
                experience.experienceCondition().originalText()
        );

        JobRequirement communication = requirements.get(1);

        assertInstanceOf(
                RequirementExpression.AllOf.class,
                communication.expression()
        );

        var communicationAllOf =
                (RequirementExpression.AllOf) communication.expression();

        assertEquals(2, communicationAllOf.children().size());

        assertEquals(
                "stakeholder communication",
                ((RequirementExpression.Concept)
                        communicationAllOf.children().get(0)).name()
        );

        assertEquals(
                "budget planning",
                ((RequirementExpression.Concept)
                        communicationAllOf.children().get(1)).name()
        );

        JobRequirement presentation = requirements.get(2);

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                presentation.expression()
        );

        var presentationAnyOf =
                (RequirementExpression.AnyOf) presentation.expression();

        assertEquals(2, presentationAnyOf.children().size());

        assertEquals(
                "presentation",
                ((RequirementExpression.Concept)
                        presentationAnyOf.children().get(0)).name()
        );

        assertEquals(
                "public speaking",
                ((RequirementExpression.Concept)
                        presentationAnyOf.children().get(1)).name()
        );

        JobRequirement travel = requirements.get(3);

        assertEquals(
                Verifiability.NOT_VERIFIABLE,
                travel.verifiability()
        );

        assertEquals(
                "willingness to travel",
                ((RequirementExpression.Concept)
                        travel.expression()).name()
        );

        JobRequirement education = requirements.get(4);

        assertEquals(
                RequirementType.EDUCATION,
                education.type()
        );

        assertInstanceOf(
                RequirementExpression.AllOf.class,
                education.expression()
        );

        var educationAllOf =
                (RequirementExpression.AllOf) education.expression();

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                educationAllOf.children().get(1)
        );
    }
}