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
import static org.mockito.Mockito.*;

class JobRequirementExtractionBoundaryTest {

    @Test
    void extractPreservesStructuredRequirementSemanticsFromModelResponse()
            throws Exception {

        String modelResponse = """
            [
              {
                "originalText": "Strong proficiency in Java or Python.",
                "expression": {
                  "type": "AnyOf",
                  "children": [
                    {"type": "Concept", "name": "Java"},
                    {"type": "Concept", "name": "Python"}
                  ]
                },
                "type": "SKILL",
                "importance": "HIGH",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "SQL and PostgreSQL or MySQL",
                "expression": {
                  "type": "AllOf",
                  "children": [
                    {"type": "Concept", "name": "SQL"},
                    {
                      "type": "AnyOf",
                      "children": [
                        {"type": "Concept", "name": "PostgreSQL"},
                        {"type": "Concept", "name": "MySQL"}
                      ]
                    }
                  ]
                },
                "type": "SKILL",
                "importance": "HIGH",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "Bachelor's degree in Computer Science, Computer Engineering, or a related field",
                "expression": {
                  "type": "AllOf",
                  "children": [
                    {"type": "Concept", "name": "Bachelor's degree"},
                    {
                      "type": "AnyOf",
                      "children": [
                        {"type": "Concept", "name": "Computer Science"},
                        {"type": "Concept", "name": "Computer Engineering"},
                        {"type": "Concept", "name": "related field"}
                      ]
                    }
                  ]
                },
                "type": "EDUCATION",
                "importance": "HIGH",
                "experienceRequirement": null,
                "verifiability": "VERIFIABLE"
              },
              {
                "originalText": "Willingness to relocate",
                "expression": {
                  "type": "Concept",
                  "name": "Willingness to relocate"
                },
                "type": "OTHER",
                "importance": "LOW",
                "experienceRequirement": null,
                "verifiability": "NOT_VERIFIABLE"
              },
              {
                "originalText": "Experience with QuantumFlux orchestration",
                "expression": {
                  "type": "Concept",
                  "name": "QuantumFlux orchestration"
                },
                "type": "SKILL",
                "importance": "MEDIUM",
                "experienceRequirement": "2 years",
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
                    We need a software engineer with strong proficiency
                    in Java or Python, SQL and PostgreSQL or MySQL,
                    a Bachelor's degree in Computer Science, Computer
                    Engineering, or a related field, and 2 years of
                    experience with QuantumFlux orchestration.
                    Willingness to relocate is required.
                    """);

        assertEquals(5, requirements.size());

        // 1. Java OR Python
        JobRequirement javaPython = requirements.get(0);

        assertEquals(
                RequirementType.SKILL,
                javaPython.type()
        );

        assertEquals(
                RequirementImportance.HIGH,
                javaPython.importance()
        );

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                javaPython.expression()
        );

        var javaPythonAnyOf =
                (RequirementExpression.AnyOf) javaPython.expression();

        assertEquals(2, javaPythonAnyOf.children().size());
        assertEquals(
                "Java",
                ((RequirementExpression.Concept)
                        javaPythonAnyOf.children().get(0)).name()
        );
        assertEquals(
                "Python",
                ((RequirementExpression.Concept)
                        javaPythonAnyOf.children().get(1)).name()
        );

        // 2. SQL AND (PostgreSQL OR MySQL)
        JobRequirement database =
                requirements.get(1);

        assertInstanceOf(
                RequirementExpression.AllOf.class,
                database.expression()
        );

        var databaseAllOf =
                (RequirementExpression.AllOf) database.expression();

        assertEquals(2, databaseAllOf.children().size());

        assertEquals(
                "SQL",
                ((RequirementExpression.Concept)
                        databaseAllOf.children().get(0)).name()
        );

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                databaseAllOf.children().get(1)
        );

        var databaseAnyOf =
                (RequirementExpression.AnyOf)
                        databaseAllOf.children().get(1);

        assertEquals(2, databaseAnyOf.children().size());

        // 3. Bachelor's degree AND (CS OR CE OR related field)
        JobRequirement education =
                requirements.get(2);

        assertEquals(
                RequirementType.EDUCATION,
                education.type()
        );

        assertInstanceOf(
                RequirementExpression.AllOf.class,
                education.expression()
        );

        var educationAllOf =
                (RequirementExpression.AllOf)
                        education.expression();

        assertEquals(2, educationAllOf.children().size());

        assertEquals(
                "Bachelor's degree",
                ((RequirementExpression.Concept)
                        educationAllOf.children().get(0)).name()
        );

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                educationAllOf.children().get(1)
        );

        var educationAnyOf =
                (RequirementExpression.AnyOf)
                        educationAllOf.children().get(1);

        assertEquals(3, educationAnyOf.children().size());

        // 4. NOT_VERIFIABLE survives the complete extraction path
        JobRequirement relocation =
                requirements.get(3);

        assertEquals(
                Verifiability.NOT_VERIFIABLE,
                relocation.verifiability()
        );

        assertEquals(
                "Willingness to relocate",
                ((RequirementExpression.Concept)
                        relocation.expression()).name()
        );

        // 5. Unknown concept survives and experience text survives
        JobRequirement unknown =
                requirements.get(4);

        assertEquals(
                "QuantumFlux orchestration",
                ((RequirementExpression.Concept)
                        unknown.expression()).name()
        );

        assertNotNull(unknown.experienceCondition());
        assertEquals(
                "2 years",
                unknown.experienceCondition().originalText()
        );

        verify(chatClient).prompt();
        verify(requestSpec).user(anyString());
        verify(requestSpec).call();
        verify(responseSpec).content();
    }
}