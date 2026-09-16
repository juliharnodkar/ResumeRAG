package com.example.resumerag;

import java.lang.reflect.InvocationTargetException;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobRequirementExtractionContractTest {

    private JobRequirementExtractionService service() {
        ChatClient.Builder builder = org.mockito.Mockito.mock(ChatClient.Builder.class);
        return new JobRequirementExtractionService(
                builder,
                new ObjectMapper()
        );
    }

    private JobRequirement parse(String json) throws Exception {
        JobRequirementExtractionService service = service();

        Method method =
                JobRequirementExtractionService.class
                        .getDeclaredMethod(
                                "parseRequirement",
                                com.fasterxml.jackson.databind.JsonNode.class
                        );

        method.setAccessible(true);

        var node = new ObjectMapper().readTree(json);

        return (JobRequirement) method.invoke(service, node);
    }

    @Test
    void preservesAnyOfLogic() throws Exception {
        JobRequirement requirement = parse("""
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
                }
                """);

        assertNotNull(requirement);

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                requirement.expression()
        );

        var anyOf =
                (RequirementExpression.AnyOf) requirement.expression();

        assertEquals(2, anyOf.children().size());

        assertEquals(
                "Java",
                ((RequirementExpression.Concept) anyOf.children().get(0)).name()
        );

        assertEquals(
                "Python",
                ((RequirementExpression.Concept) anyOf.children().get(1)).name()
        );
    }

    @Test
    void preservesNestedAllOfAndAnyOfLogic() throws Exception {
        JobRequirement requirement = parse("""
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
                }
                """);

        assertNotNull(requirement);

        assertInstanceOf(
                RequirementExpression.AllOf.class,
                requirement.expression()
        );

        var allOf =
                (RequirementExpression.AllOf) requirement.expression();

        assertEquals(2, allOf.children().size());

        assertInstanceOf(
                RequirementExpression.Concept.class,
                allOf.children().get(0)
        );

        assertInstanceOf(
                RequirementExpression.AnyOf.class,
                allOf.children().get(1)
        );

        var anyOf =
                (RequirementExpression.AnyOf) allOf.children().get(1);

        assertEquals(2, anyOf.children().size());
    }

    @Test
    void preservesUnknownConceptsWithoutRegistryRequirement() throws Exception {
        JobRequirement requirement = parse("""
                {
                  "originalText": "Experience with QuantumFlux orchestration",
                  "expression": {
                    "type": "Concept",
                    "name": "QuantumFlux orchestration"
                  },
                  "type": "SKILL",
                  "importance": "MEDIUM",
                  "experienceRequirement": null,
                  "verifiability": "VERIFIABLE"
                }
                """);

        assertNotNull(requirement);

        assertInstanceOf(
                RequirementExpression.Concept.class,
                requirement.expression()
        );

        assertEquals(
                "QuantumFlux orchestration",
                ((RequirementExpression.Concept) requirement.expression()).name()
        );
    }

    @Test
    void preservesConceptTextInsteadOfForcingCanonicalSkillName() throws Exception {
        JobRequirement requirement = parse("""
                {
                  "originalText": "Experience building HTTP APIs",
                  "expression": {
                    "type": "Concept",
                    "name": "HTTP APIs"
                  },
                  "type": "SKILL",
                  "importance": "HIGH",
                  "experienceRequirement": null,
                  "verifiability": "VERIFIABLE"
                }
                """);

        assertNotNull(requirement);

        assertEquals(
                "HTTP APIs",
                ((RequirementExpression.Concept) requirement.expression()).name()
        );
    }

    @Test
    void preservesNotVerifiableRequirement() throws Exception {
        JobRequirement requirement = parse("""
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
                }
                """);

        assertNotNull(requirement);

        assertEquals(
                Verifiability.NOT_VERIFIABLE,
                requirement.verifiability()
        );
    }

    @Test
    void preservesOriginalRequirementText() throws Exception {
        JobRequirement requirement = parse("""
                {
                  "originalText": "  Strong proficiency in Java or Python.  ",
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
                }
                """);

        assertNotNull(requirement);
        assertEquals(
                "Strong proficiency in Java or Python.",
                requirement.originalText()
        );
    }
    @Test
void rejectsLegacyFlatConceptsInsteadOfFabricatingAllOf() {
        String json = """
            {
              "originalText": "Java or Python",
              "concepts": ["Java", "Python"],
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """;

        InvocationTargetException thrown = assertThrows(
            InvocationTargetException.class,
            () -> parse(json)
        );

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("structured expression"));
    }

    @Test
    void rejectsRequirementWithoutExpression() {
        String json = """
            {
              "originalText": "Experience with Java",
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """;

        InvocationTargetException thrown = assertThrows(
            InvocationTargetException.class,
            () -> parse(json)
        );

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("structured expression"));
    }

    @Test
    void rejectsLegacySkillsArrayInsteadOfExpression() {
        String json = """
            {
              "originalText": "Java or Python",
              "skills": ["Java", "Python"],
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """;

        InvocationTargetException thrown = assertThrows(
            InvocationTargetException.class,
            () -> parse(json)
        );

        assertInstanceOf(IllegalArgumentException.class, thrown.getCause());
        assertTrue(thrown.getCause().getMessage().contains("structured expression"));
    }

    @Test
    void strictParserNegativeCasesAreRejected() {

        List<String> payloads = List.of(

            """
            {
              "originalText": "Java",
              "expression": null,
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": ["Java"],
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "   ",
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "XOR",
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "   "
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java and Python",
              "expression": {
                "type": "ALL_OF"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java and Python",
              "expression": {
                "type": "ALL_OF",
                "children": []
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java or Python",
              "expression": {
                "type": "ANY_OF"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java or Python",
              "expression": {
                "type": "ANY_OF",
                "children": []
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java and Python",
              "expression": {
                "type": "ALL_OF",
                "children": [
                  {
                    "type": "CONCEPT",
                    "name": "Java"
                  },
                  {
                    "type": "CONCEPT"
                  }
                ]
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "type": "INVALID",
              "importance": "HIGH",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "type": "SKILL",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "CRITICAL",
              "verifiability": "VERIFIABLE"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "HIGH"
            }
            """,

            """
            {
              "originalText": "Java",
              "expression": {
                "type": "CONCEPT",
                "name": "Java"
              },
              "type": "SKILL",
              "importance": "HIGH",
              "verifiability": "UNKNOWN"
            }
            """
        );

        for (String json : payloads) {
            InvocationTargetException thrown = assertThrows(
                InvocationTargetException.class,
                () -> parse(json)
            );

            assertInstanceOf(
                IllegalArgumentException.class,
                thrown.getCause(),
                "Expected IllegalArgumentException for payload: " + json
            );
        }
    }

    // Prevent duplicate insertion of the strict parser negative-case suite.
    private static final String strictParserNegativeCasesAreRejected =
            "strictParserNegativeCasesAreRejected";
}
