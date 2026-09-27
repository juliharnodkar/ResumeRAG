package com.example.resumerag;

import com.example.resumerag.analysis.JobRequirementExtractionService;
import com.example.resumerag.model.MatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EndToEndPipelineTest {

    @Test
    void validatesStructuredExtractionHybridEvidenceScoringAndExplanation() {
        String extraction = """
                [
                  {"originalText":"Java and AWS, Azure, or GCP", "expression":{"type":"AllOf","children":[{"type":"Concept","name":"Java"},{"type":"AnyOf","children":[{"type":"Concept","name":"AWS"},{"type":"Concept","name":"Azure"},{"type":"Concept","name":"GCP"}]}]}, "type":"SKILL", "importance":"HIGH", "experienceRequirement":null, "verifiability":"VERIFIABLE"},
                  {"originalText":"3 years of C++ or C# experience", "expression":{"type":"AnyOf","children":[{"type":"Concept","name":"C++"},{"type":"Concept","name":"C#"}]}, "type":"EXPERIENCE", "importance":"MEDIUM", "experienceRequirement":"3 years", "verifiability":"VERIFIABLE"},
                  {"originalText":"QuantumFlux orchestration", "expression":{"type":"Concept","name":"QuantumFlux orchestration"}, "type":"SKILL", "importance":"HIGH", "experienceRequirement":null, "verifiability":"VERIFIABLE"},
                  {"originalText":"Willingness to relocate", "expression":{"type":"Concept","name":"Willingness to relocate"}, "type":"OTHER", "importance":"LOW", "experienceRequirement":null, "verifiability":"NOT_VERIFIABLE"}
                ]
                """;
        Document javaEvidence = new Document("Built Java services.", java.util.Map.of("section", "Projects"));
        VectorStore vectorStore = mock(VectorStore.class);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenAnswer(invocation -> {
            String query = invocation.<SearchRequest>getArgument(0).getQuery();
            return switch (query) {
                case "Java" -> List.of(javaEvidence);
                case "AWS" -> List.of(new Document("Cloud infrastructure delivery.", java.util.Map.of("distance", 0.1, "section", "Experience")));
                case "C++" -> List.of(new Document("C++ engineer with 4 years of experience.", java.util.Map.of("section", "Experience")));
                default -> List.of();
            };
        });
        LexicalCandidateRetriever lexical = mock(LexicalCandidateRetriever.class);
        when(lexical.search(any(), any(), anyInt())).thenAnswer(invocation ->
                invocation.<String>getArgument(0).equals("Java") ? List.of(javaEvidence) : List.of());

        JobRequirementExtractionService extractionService = extractionService(extraction);
        ChatClient.Builder analysisBuilder = mock(ChatClient.Builder.class);
        ChatClient analysisClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec analysisRequest = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec analysisResponse = mock(ChatClient.CallResponseSpec.class);
        when(analysisBuilder.build()).thenReturn(analysisClient);
        when(analysisClient.prompt()).thenReturn(analysisRequest);
        when(analysisRequest.system(anyString())).thenReturn(analysisRequest);
        when(analysisRequest.user(anyString())).thenReturn(analysisRequest);
        when(analysisRequest.call()).thenReturn(analysisResponse);
        when(analysisResponse.content()).thenReturn("SUMMARY\nVerified fit.\n\nSTRENGTHS\nJava, cloud, and C++.\n\nGAPS\nQuantumFlux orchestration.");

        MatchResult result = new MatchAnalysisService(
                extractionService,
                new RequirementMatchingService(vectorStore, lexical),
                mock(ResumeQualityService.class),
                mock(JDTailoringService.class),
                analysisBuilder,
                vectorStore).analyze("resume-1", "Java cloud engineer with C++ experience");

        assertEquals(63, result.score());
        assertEquals(4, result.requirements().size());
        assertEquals("MATCHED", result.requirements().get(0).status().name());
        assertEquals("MATCHED", result.requirements().get(1).status().name());
        assertEquals("NOT_EVIDENCED", result.requirements().get(2).status().name());
        assertEquals("NOT_VERIFIABLE", result.requirements().get(3).status().name());
        assertTrue(result.analysis().startsWith("SUMMARY"));
        verify(lexical).search("Java", "resume-1", 20);
    }

    private JobRequirementExtractionService extractionService(String response) {
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient client = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.call()).thenReturn(call);
        when(call.content()).thenReturn(response);
        return new JobRequirementExtractionService(builder, new ObjectMapper());
    }
}
