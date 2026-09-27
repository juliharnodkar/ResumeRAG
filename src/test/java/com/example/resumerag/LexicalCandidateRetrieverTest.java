package com.example.resumerag;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class LexicalCandidateRetrieverTest {

    private static final String RESUME_ID =
            "00000000-0000-0000-0000-000000000001";

    @Test
    void searchBindsResumeIdPatternAndLimitWithoutConcatenatingSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());

        LexicalCandidateRetriever retriever =
                new LexicalCandidateRetriever(jdbcTemplate);

        retriever.search("Java", RESUME_ID, 20);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> arg0 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg1 = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> arg2 = ArgumentCaptor.forClass(Object.class);

        verify(jdbcTemplate).query(
                sqlCaptor.capture(),
                any(RowMapper.class),
                arg0.capture(),
                arg1.capture(),
                arg2.capture());

        String sql = sqlCaptor.getValue();
        assertEquals(LexicalCandidateRetriever.SEARCH_SQL, sql);
        assertTrue(sql.contains("metadata::jsonb ->> 'resumeId' = ?"));
        assertTrue(sql.contains("content ~* ?"));
        assertTrue(sql.contains("LIMIT ?"));
        assertFalse(sql.contains(RESUME_ID));
        assertFalse(sql.contains("Java"));
        assertFalse(sql.contains("java"));

        String expectedPattern = LexicalQueryPattern.fromQuery("Java").orElseThrow();
        assertFalse(sql.contains(expectedPattern));

        assertEquals(RESUME_ID, arg0.getValue());
        assertEquals(expectedPattern, arg1.getValue());
        assertEquals(20, arg2.getValue());
    }

    @Test
    void blankInputsPerformNoSql() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        LexicalCandidateRetriever retriever =
                new LexicalCandidateRetriever(jdbcTemplate);

        assertEquals(List.of(), retriever.search(null, RESUME_ID, 20));
        assertEquals(List.of(), retriever.search("  ", RESUME_ID, 20));
        assertEquals(List.of(), retriever.search("...", RESUME_ID, 20));
        assertEquals(List.of(), retriever.search("Java", null, 20));
        assertEquals(List.of(), retriever.search("Java", "  ", 20));
        assertEquals(List.of(), retriever.search("Java", RESUME_ID, 0));

        verify(jdbcTemplate, never()).query(
                anyString(),
                any(RowMapper.class),
                any(),
                any(),
                any());
    }

    @Test
    void mapsRowsToDocumentsWithoutDistanceOrScore() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    RowMapper<Document> mapper = invocation.getArgument(1);
                    ResultSet resultSet = mock(ResultSet.class);
                    when(resultSet.getString("id")).thenReturn("doc-1");
                    when(resultSet.getString("content"))
                            .thenReturn("Built REST APIs using Java.");
                    when(resultSet.getString("metadata"))
                            .thenReturn(
                                    "{\"section\":\"Projects\",\"project\":\"API\",\"resumeId\":\""
                                            + RESUME_ID
                                            + "\"}");
                    return List.of(mapper.mapRow(resultSet, 0));
                });

        LexicalCandidateRetriever retriever =
                new LexicalCandidateRetriever(jdbcTemplate);

        List<Document> documents = retriever.search("Java", RESUME_ID, 20);

        assertEquals(1, documents.size());
        Document document = documents.get(0);
        assertEquals("doc-1", document.getId());
        assertEquals("Built REST APIs using Java.", document.getText());
        assertEquals("Projects", document.getMetadata().get("section"));
        assertEquals("API", document.getMetadata().get("project"));
        assertEquals(RESUME_ID, document.getMetadata().get("resumeId"));
        assertFalse(document.getMetadata().containsKey("distance"));
        assertNull(document.getScore());
    }

    @Test
    void alwaysScopesToTheProvidedResumeId() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(List.of());

        new LexicalCandidateRetriever(jdbcTemplate)
                .search("AWS", RESUME_ID, 20);

        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                eq(RESUME_ID),
                anyString(),
                eq(20));
        verify(jdbcTemplate, never()).query(
                anyString(),
                any(RowMapper.class),
                anyInt());
    }
}
