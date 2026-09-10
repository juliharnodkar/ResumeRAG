package com.example.resumerag;

import com.example.resumerag.model.SkillMatch;
import com.example.resumerag.skill.SkillRegistry;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SkillMatchingService {

    private final VectorStore vectorStore;

    public SkillMatchingService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    public List<SkillMatch> matchSkills(List<String> requiredSkills, String resumeId) {
        UUID.fromString(resumeId);
        List<Document> documents = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query("resume")
                        .topK(100)
                        .filterExpression("resumeId == '" + resumeId + "'")
                        .build());
        if (documents == null) documents = List.of();
        List<SkillMatch> results = new ArrayList<>();
        for (String skill : requiredSkills) {
            Document best = findBestDocument(documents, skill);
            boolean matched = best != null;
            results.add(new SkillMatch(skill, matched, matched ? List.of(buildEvidence(best)) : List.of()));
        }
        return results;
    }

    private Document findBestDocument(List<Document> documents, String skill) {
        Document best = null;
        int bestScore = -1;
        for (Document document : documents) {
            if (!SkillRegistry.contains(document.getText(), skill)) continue;
            int score = sectionScore(document);
            if (score > bestScore) { bestScore = score; best = document; }
        }
        return best;
    }

    private int sectionScore(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        String section = String.valueOf(metadata.getOrDefault("section", ""));
        if (section.equalsIgnoreCase("Experience")) return 100;
        if (section.equalsIgnoreCase("Projects")) return 90;
        if (section.equalsIgnoreCase("Skills")) return 50;
        if (section.equalsIgnoreCase("Coursework")) return 60;
        if (section.equalsIgnoreCase("Education")) return 50;
        return 10;
    }

    private String buildEvidence(Document document) {
        Map<String, Object> metadata = document.getMetadata();
        Object project = metadata.get("project");
        if (project != null && !project.toString().isBlank()) return project.toString();
        Object section = metadata.get("section");
        if (section != null && !section.toString().isBlank()) return section.toString();
        return "Mentioned in resume.";
    }

    public int calculateScore(List<SkillMatch> matches) {
        if (matches.isEmpty()) return 0;
        long matched = matches.stream().filter(SkillMatch::matched).count();
        return (int) Math.round(matched * 100.0 / matches.size());
    }
}


