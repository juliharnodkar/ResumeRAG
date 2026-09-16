package com.example.resumerag;

import com.example.resumerag.model.TailoredMatchResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TailoredMatchResultSerializationTest {

    @Test
    void serializesUserFacingFieldsWithExactNames() throws Exception {
        TailoredMatchResult result = new TailoredMatchResult(
                72,
                "Strong resume foundation with several opportunities to tailor it to this role.",
                List.of("Clear, organized structure"),
                List.of("Quantify impact where possible"),
                List.of("If you have docker, mention it in the relevant project description."),
                List.of(),
                List.of("Java"),
                List.of("Kubernetes")
        );

        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(result);
        System.out.println("TAILORED_MATCH_RESULT_JSON=" + json);

        JsonNode node = objectMapper.readTree(json);
        List<String> fieldNames = StreamSupport.stream(
                ((Iterable<String>) node::fieldNames).spliterator(),
                false
        ).toList();
        System.out.println("TAILORED_MATCH_RESULT_FIELDS=" + fieldNames);

        assertEquals(72, node.get("score").asInt());
        assertEquals(
                "Strong resume foundation with several opportunities to tailor it to this role.",
                node.get("scoreExplanation").asText()
        );
        assertEquals("Clear, organized structure", node.get("resumeStrengths").get(0).asText());
        assertEquals("Quantify impact where possible", node.get("needsAttention").get(0).asText());
        assertEquals(
                "If you have docker, mention it in the relevant project description.",
                node.get("jdTailoringTips").get(0).asText()
        );
        assertTrue(node.has("score"));
        assertTrue(node.has("resumeStrengths"));
        assertTrue(node.has("needsAttention"));
        assertTrue(node.has("jdTailoringTips"));
    }
}
