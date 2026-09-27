package com.example.resumerag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class LexicalCandidateRetriever {

    static final String SEARCH_SQL = """
            SELECT id, content, metadata
            FROM public.vector_store
            WHERE metadata::jsonb ->> 'resumeId' = ?
              AND content ~* ?
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RowMapper<Document> documentMapper = this::mapRow;

    public LexicalCandidateRetriever(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = new ObjectMapper();
    }

    public List<Document> search(String query, String resumeId, int topK) {
        if (query == null || query.isBlank()
                || resumeId == null || resumeId.isBlank()
                || topK <= 0) {
            return List.of();
        }

        Optional<String> pattern = LexicalQueryPattern.fromQuery(query);

        if (pattern.isEmpty()) {
            return List.of();
        }

        return jdbcTemplate.query(
                SEARCH_SQL,
                documentMapper,
                resumeId,
                pattern.get(),
                topK);
    }

    private Document mapRow(ResultSet resultSet, int rowNum) throws SQLException {
        String id = resultSet.getString("id");
        String content = resultSet.getString("content");
        Map<String, Object> metadata = parseMetadata(resultSet.getString("metadata"));

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .build();
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }

        try {
            Map<String, Object> metadata = objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    });

            return metadata == null
                    ? new HashMap<>()
                    : new HashMap<>(metadata);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }
}
