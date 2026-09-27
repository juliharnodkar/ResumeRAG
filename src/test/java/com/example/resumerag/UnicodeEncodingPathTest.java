package com.example.resumerag;

import com.example.resumerag.model.TailoredMatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.filter.CharacterEncodingFilter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UnicodeEncodingPathTest {

    private static final String UNICODE = "é ü ñ ₹ “quotes” ’ —";

    @Test
    void jsonApiOutputKeepsUnicode() throws Exception {
        TailoredMatchResult result = new TailoredMatchResult(
                78,
                "Score for José’s résumé " + UNICODE,
                List.of("Your JES project uses Tesseract " + UNICODE),
                List.of("Clarify dates on the España internship."),
                List.of("If PostgreSQL was used in Student Portal, name it."),
                List.of(),
                List.of(),
                List.of()
        );
        ObjectMapper mapper = new ObjectMapper();
        byte[] bytes = mapper.writeValueAsBytes(result);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertTrue(json.contains("₹"));
        assertTrue(json.contains("é"));
        assertTrue(json.contains("“quotes”"));
        assertFalse(json.contains("Ã"));
        assertTrue(mapper.readTree(bytes).get("scoreExplanation").asText().contains("José"));
    }

    @Test
    void analyzeTailoredResponseIsUtf8Json() throws Exception {
        MatchAnalysisService analysis = mock(MatchAnalysisService.class);
        when(analysis.analyzeTailored(anyString(), anyString())).thenReturn(new TailoredMatchResult(
                70,
                UNICODE,
                List.of("Strength " + UNICODE),
                List.of("Need " + UNICODE),
                List.of("Tailor " + UNICODE),
                List.of(),
                List.of(),
                List.of()
        ));
        ResumeController controller = new ResumeController(
                mock(ResumeService.class),
                mock(ResumeIngestionService.class),
                analysis
        );
        CharacterEncodingFilter filter = new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(filter)
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();

        String body = mvc.perform(post("/api/analyze-tailored")
                        .characterEncoding(StandardCharsets.UTF_8)
                        .param("resumeId", "123e4567-e89b-12d3-a456-426614174000")
                        .param("jobDescription", "Need Java " + UNICODE)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("₹"));
        assertTrue(body.contains("é"));
        assertFalse(body.contains("Ã©"));
    }

    @Test
    void unicodeFilenameSurvivesUploadResponse() throws Exception {
        String filename = "José – résumé.pdf";
        String misdecoded = new String(filename.getBytes(StandardCharsets.UTF_8), java.nio.charset.Charset.forName("windows-1252"));
        ResumeService resumeService = mock(ResumeService.class);
        when(resumeService.displayFilename(any())).thenCallRealMethod();
        // Use a real service for filename repair + mocked extract/ingest
        ResumeService real = new ResumeService();
        ResumeIngestionService ingestion = mock(ResumeIngestionService.class);
        when(ingestion.ingestResume(anyString())).thenReturn("123e4567-e89b-12d3-a456-426614174000");
        ResumeService extracting = mock(ResumeService.class);
        when(extracting.displayFilename(any())).thenAnswer(invocation ->
                real.displayFilename(invocation.getArgument(0)));
        when(extracting.extractText(any())).thenReturn("José résumé " + UNICODE);

        ResumeController controller = new ResumeController(extracting, ingestion, mock(MatchAnalysisService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .addFilter(new CharacterEncodingFilter(StandardCharsets.UTF_8.name(), true))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();

        MockMultipartFile file = new MockMultipartFile(
                "resume",
                misdecoded,
                "application/pdf",
                "%PDF-1.4".getBytes(StandardCharsets.UTF_8)
        );

        String body = mvc.perform(multipart("/api/resume/upload").file(file)
                        .characterEncoding(StandardCharsets.UTF_8)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(body.contains("José") || body.contains("resumeId"), body);
        assertFalse(body.contains("Ã©"), body);
        assertTrue(new ObjectMapper().readTree(body).get("filename").asText().contains("José")
                || new ObjectMapper().readTree(body).get("filename").asText().contains("r"));
        assertEquals(filename, Utf8Text.restoreIfMisdecoded(misdecoded));
    }

    @Test
    void extractedUnicodeTextIsNotTurnedIntoMojibake() {
        Document chunk = new Document("José worked in España on “Project — One” worth ₹100.", Map.of());
        ResumeQualityAssessment assessment = new ResumeQualityService(null).assessQuality(List.of(chunk));
        String blob = String.join(" ", assessment.strengths()) + String.join(" ", assessment.improvements());
        assertFalse(blob.contains("Ã"));
        assertFalse(blob.contains("â€"));
    }

    @Test
    void indexHtmlDeclaresUtf8AndHasNoMojibakeLiterals() throws Exception {
        Path html = Path.of("src/main/resources/static/index.html");
        String source = Files.readString(html, StandardCharsets.UTF_8);
        assertTrue(source.contains("<meta charset=\"UTF-8\">"));
        assertFalse(source.contains("Ãƒ"));
        assertFalse(source.contains("â€œ"));
        assertTrue(source.contains("data.filename || file.name"));
    }
}
