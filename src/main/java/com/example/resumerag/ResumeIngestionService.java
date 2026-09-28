package com.example.resumerag;

import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ResumeIngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(300).withMinChunkSizeChars(100)
            .withMinChunkLengthToEmbed(20).withMaxNumChunks(100).build();

    public ResumeIngestionService(VectorStore vectorStore) { this.vectorStore = vectorStore; }

    public String ingestResume(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) throw new IllegalArgumentException("Resume text is empty.");
        String resumeId = UUID.randomUUID().toString();
        List<Document> chunks = new ArrayList<>();
        for (Document document : buildDocuments(resumeText, resumeId)) chunks.addAll(splitter.split(List.of(document)));
        if (chunks.isEmpty()) throw new IllegalArgumentException("Resume did not produce any searchable content.");
        System.out.println("INGESTION: processed chunks = " + chunks.size());
        vectorStore.add(chunks);
        return resumeId;
    }

    private List<Document> buildDocuments(String resumeText, String resumeId) {
        List<Document> documents = new ArrayList<>();
        String currentSection = "Resume";
        String currentProject = null;
        StringBuilder currentText = new StringBuilder();
        for (String rawLine : resumeText.split("\\r?\\n")) {
            String line = rawLine.trim();
            if (line.isBlank()) continue;
            if (isSectionHeading(line)) {
                addDocument(documents, currentText, resumeId, currentSection, currentProject);
                currentText.setLength(0); currentSection = normalizeSection(line); currentProject = null; continue;
            }
            if (!"Education".equalsIgnoreCase(currentSection) && isLikelyEducationEntry(line)) {
                addDocument(documents, currentText, resumeId, currentSection, currentProject);
                currentText.setLength(0); currentSection = "Education"; currentProject = null;
            }
            if ("Projects".equalsIgnoreCase(currentSection) && isLikelyProjectHeading(line)) {
                addDocument(documents, currentText, resumeId, currentSection, currentProject);
                currentText.setLength(0); currentProject = cleanProjectName(line); currentText.append(line).append(' '); continue;
            }
            currentText.append(line).append(' ');
        }
        addDocument(documents, currentText, resumeId, currentSection, currentProject);
        return documents;
    }

    private boolean isLikelyProjectHeading(String line) {
        if (line.length() < 3 || line.length() > 120) return false;
        if (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) return false;
        if (line.endsWith(".")) return false;
        int words = line.trim().split("\\s+").length;
        if (words > 14) return false;
        if (isLikelyEducationEntry(line)) return false;
        return line.contains("|") || line.contains("—") || line.contains("–") || Character.isUpperCase(line.charAt(0));
    }

    /**
     * Reject lines that look like education entries so they are not mistaken
     * for project headings. Education entries share formatting cues with
     * project headings (em-dashes, uppercase starts, pipe separators) but
     * contain degree-related keywords that projects almost never use.
     */
    private boolean isLikelyEducationEntry(String line) {
        String lower = line.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("b.tech") || lower.contains("m.tech")
                || lower.contains("bachelor") || lower.contains("master")
                || lower.contains("b.sc") || lower.contains("m.sc")
                || lower.contains("b.s.") || lower.contains("m.s.")
                || lower.contains("bba") || lower.contains("mba")
                || lower.contains("b.e.") || lower.contains("m.e.")
                || lower.contains("b.a.") || lower.contains("m.a.")
                || lower.contains("ph.d") || lower.contains("phd")
                || lower.contains("diploma") || lower.contains("associate degree")
                || lower.contains("university") || lower.contains("college")
                || lower.contains("institute of technology");
    }

    private String cleanProjectName(String line) {
        int pipe = line.indexOf('|');
        if (pipe > 0) return line.substring(0, pipe).trim();
        int dash = line.indexOf('—'); if (dash > 0) return line.substring(0, dash).trim();
        int endash = line.indexOf('–'); if (endash > 0) return line.substring(0, endash).trim();
        return line.trim();
    }

    private void addDocument(List<Document> documents, StringBuilder text, String resumeId, String section, String project) {
        if (text.length() == 0) return;
        Map<String,Object> metadata = new HashMap<>(); metadata.put("resumeId", resumeId); metadata.put("section", section);
        if (project != null && !project.isBlank()) metadata.put("project", project);
        documents.add(new Document(text.toString().trim(), metadata));
    }

    private boolean isSectionHeading(String line) {
        String normalized = line.toLowerCase().replaceAll("[^a-z ]", "").trim();
        return normalized.equals("skills") || normalized.equals("technical skills") || normalized.equals("projects") ||
                normalized.equals("experience") || normalized.equals("education") || normalized.equals("relevant coursework") ||
                normalized.equals("coursework") || normalized.equals("certifications");
    }

    private String normalizeSection(String line) {
        String n = line.toLowerCase().trim();
        if (n.contains("skill")) return "Skills";
        if (n.contains("project")) return "Projects";
        if (n.contains("coursework")) return "Coursework";
        if (n.contains("experience")) return "Experience";
        if (n.contains("education")) return "Education";
        if (n.contains("certification")) return "Certifications";
        return line;
    }
}
