package com.example.resumerag;

import com.example.resumerag.model.RequirementExpression;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementExpressionSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void conceptIncludesTypeDiscriminator() throws Exception {
        var expression = new RequirementExpression.Concept("Python");

        var json = objectMapper.writeValueAsString(expression);

        assertTrue(json.contains("\"type\":\"Concept\""));
        assertTrue(json.contains("\"name\":\"Python\""));
    }

    @Test
    void allOfIncludesTypeDiscriminator() throws Exception {
        var expression = new RequirementExpression.AllOf(List.of(
                new RequirementExpression.Concept("Python"),
                new RequirementExpression.Concept("Pandas")
        ));

        var json = objectMapper.writeValueAsString(expression);

        assertTrue(json.contains("\"type\":\"AllOf\""));
        assertTrue(json.contains("\"children\""));
        assertTrue(json.contains("\"name\":\"Python\""));
        assertTrue(json.contains("\"name\":\"Pandas\""));
    }

    @Test
    void anyOfIncludesTypeDiscriminator() throws Exception {
        var expression = new RequirementExpression.AnyOf(List.of(
                new RequirementExpression.Concept("PostgreSQL"),
                new RequirementExpression.Concept("MySQL")
        ));

        var json = objectMapper.writeValueAsString(expression);

        assertTrue(json.contains("\"type\":\"AnyOf\""));
        assertTrue(json.contains("\"children\""));
        assertTrue(json.contains("\"name\":\"PostgreSQL\""));
        assertTrue(json.contains("\"name\":\"MySQL\""));
    }

    @Test
    void nestedExpressionsPreserveEachOperator() throws Exception {
        var expression = new RequirementExpression.AllOf(List.of(
                new RequirementExpression.Concept("Python"),
                new RequirementExpression.AnyOf(List.of(
                        new RequirementExpression.Concept("PostgreSQL"),
                        new RequirementExpression.Concept("MySQL")
                ))
        ));

        var json = objectMapper.writeValueAsString(expression);

        assertEquals(1, json.split("\"type\":\"AllOf\"", -1).length - 1);
        assertEquals(1, json.split("\"type\":\"AnyOf\"", -1).length - 1);
    }
}
