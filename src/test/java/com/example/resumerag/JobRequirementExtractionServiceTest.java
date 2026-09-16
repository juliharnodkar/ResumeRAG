package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirement;
import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobRequirementExtractionServiceTest {

    @Test
    void emptyJdReturnsNoRequirementsWithoutCallingLlm() {
        ChatClient chatClient = mock(ChatClient.class);
        JobRequirementExtractionService service = service(chatClient, "[]");

        assertEquals(List.of(), service.extract(""));
        assertEquals(List.of(), service.extract("   "));
        assertEquals(List.of(), service.extract(null));
        verify(chatClient, never()).prompt();
    }

    @Test
    void bulletAndSectionJdExtractsTechnicalAndBehavioralRequirements() {
        String modelResponse = """
                [
                  {"originalText":"OOP","expression":{"type":"Concept","name":"OOP"},"type":"SKILL","importance":"HIGH","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"SQL / Database","expression":{"type":"AnyOf","children":[{"type":"Concept","name":"SQL"},{"type":"Concept","name":"DBMS"}]},"type":"SKILL","importance":"HIGH","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"REST APIs / HTTP","expression":{"type":"AllOf","children":[{"type":"Concept","name":"REST APIs"},{"type":"Concept","name":"HTTP"}]},"type":"SKILL","importance":"HIGH","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"Git / Version Control","expression":{"type":"Concept","name":"Git"},"type":"SKILL","importance":"MEDIUM","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"Teamwork","expression":{"type":"Concept","name":"teamwork"},"type":"SKILL","importance":"MEDIUM","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"Bachelor's degree","expression":{"type":"AllOf","children":[{"type":"Concept","name":"Bachelor's degree"},{"type":"AnyOf","children":[{"type":"Concept","name":"Computer Science"},{"type":"Concept","name":"Computer Engineering"},{"type":"Concept","name":"IT"}]}]},"type":"EDUCATION","importance":"HIGH","experienceRequirement":null,"verifiability":"VERIFIABLE"}
                ]
                """;

        JobRequirementExtractionService service = service(mockChatClient(modelResponse), modelResponse);

        List<JobRequirement> requirements = service.extract("""
                Qualifications:
                Bachelor's degree in Computer Science, Computer Engineering, IT, or related field.

                Technical Skills:
                - Object-Oriented Programming (OOP)
                - DBMS
                - SQL
                - REST APIs
                - HTTP
                - Git/version control

                Responsibilities:
                Debug applications.
                Collaborate with team members.
                """);

        assertEquals(6, requirements.size());
        assertTrue(requirements.stream().anyMatch(r -> r.originalText().contains("OOP")));
        assertTrue(requirements.stream().anyMatch(r -> r.originalText().contains("SQL")));
        assertTrue(requirements.stream().anyMatch(r -> r.originalText().toLowerCase().contains("team")));
        assertTrue(requirements.stream().anyMatch(r -> r.type().name().equals("EDUCATION")));
    }

    @Test
    void malformedLlmOutputFallsBackToDeterministicExtraction() {
        JobRequirementExtractionService service = service(
                mockChatClient("this is not json"),
                "this is not json"
        );

        List<JobRequirement> requirements = service.extract("Need Java and SQL experience.");
        assertTrue(requirements.size() >= 2);
        assertTrue(requirements.stream().anyMatch(r -> r.originalText().contains("Java")));
        assertTrue(requirements.stream().anyMatch(r ->
                r.originalText().contains("SQL") || r.originalText().contains("Database")));
    }

    @Test
    void llmEmptyArrayFallsBackToDeterministicExtraction() {
        JobRequirementExtractionService service = service(mockChatClient("[]"), "[]");
        List<JobRequirement> requirements = service.extract("Need Java and SQL experience.");
        assertTrue(requirements.size() >= 2);
    }

    @Test
    void llmFailureFallsBackToDeterministicExtraction() {
        ChatClient chatClient = mock(ChatClient.class);
        when(chatClient.prompt()).thenThrow(new IllegalStateException("connection refused"));
        JobRequirementExtractionService service = service(chatClient, null);

        List<JobRequirement> requirements = service.extract("Need Java and SQL experience.");
        assertTrue(requirements.size() >= 2);
        assertTrue(requirements.stream().anyMatch(r -> r.originalText().contains("Java")));
    }

    @Test
    void omittedVerifiabilityAndNullExpressionAreRecovered() {
        String modelResponse = """
                [
                  {"originalText":"OOP","expression":{"type":"Concept","name":"OOP"},"type":"SKILL","importance":"HIGH"},
                  {"originalText":"SQL","expression":null,"type":"SKILL","importance":"HIGH"},
                  {"originalText":"REST APIs","type":"SKILL","importance":"HIGH"}
                ]
                """;

        JobRequirementExtractionService service = service(mockChatClient(modelResponse), modelResponse);
        List<JobRequirement> requirements = service.extract("OOP, SQL, and REST APIs required.");

        assertEquals(3, requirements.size());
        assertEquals("OOP", requirements.get(0).originalText());
        assertEquals("SQL", requirements.get(1).originalText());
        assertEquals("REST APIs", requirements.get(2).originalText());
        assertTrue(requirements.stream().allMatch(r -> r.verifiability() != null));
    }

    @Test
    void oneInvalidItemDoesNotDiscardValidRequirements() {
        String modelResponse = """
                [
                  {"originalText":"Java","expression":{"type":"Concept","name":"Java"},"type":"SKILL","importance":"HIGH","experienceRequirement":null,"verifiability":"VERIFIABLE"},
                  {"originalText":"Broken","expression":{"type":"XOR","name":"Broken"},"type":"SKILL","importance":"HIGH","verifiability":"VERIFIABLE"},
                  {"originalText":"Git","expression":{"type":"Concept","name":"Git"},"type":"SKILL","importance":"MEDIUM","experienceRequirement":null,"verifiability":"VERIFIABLE"}
                ]
                """;

        JobRequirementExtractionService service = service(mockChatClient(modelResponse), modelResponse);
        List<JobRequirement> requirements = service.extract("Java and Git required.");

        assertEquals(2, requirements.size());
        assertEquals("Java", requirements.get(0).originalText());
        assertEquals("Git", requirements.get(1).originalText());
    }

    private JobRequirementExtractionService service(ChatClient chatClient, String ignored) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        when(builder.build()).thenReturn(chatClient);
        return new JobRequirementExtractionService(builder, new ObjectMapper());
    }

    private ChatClient mockChatClient(String response) {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec requestSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec responseSpec = mock(ChatClient.CallResponseSpec.class);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(responseSpec);
        when(responseSpec.content()).thenReturn(response);
        return chatClient;
    }
}
