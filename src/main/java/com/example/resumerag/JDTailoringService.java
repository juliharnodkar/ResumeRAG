package com.example.resumerag;

import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grounded tailoring tips from RAG evidence status.
 * Deterministic first; LLM is optional phrasing only and cannot invent facts.
 */
@Service
public class JDTailoringService {

    private static final int MAX_TIPS = 5;

    private final ChatClient chatClient;

    public JDTailoringService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    public List<String> generateTailoringTips(
            List<RequirementMatch> requirements,
            List<String> matchedSkills,
            List<String> missingSkills,
            String jobDescription
    ) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }

        List<RequirementMatch> ranked = requirements.stream()
                .filter(req -> req != null && req.requirement() != null)
                .filter(req -> req.status() != RequirementStatus.UNASSESSED)
                .filter(req -> req.status() != RequirementStatus.NOT_VERIFIABLE)
                .sorted(Comparator.comparingInt(JDTailoringService::priority))
                .toList();

        Set<String> tips = new LinkedHashSet<>();

        for (RequirementMatch req : ranked) {
            String tip = buildTip(req);
            if (tip != null && !tip.isBlank()) {
                tips.add(tip);
            }
            if (tips.size() >= MAX_TIPS) {
                break;
            }
        }

        // Deterministic tips only — no LLM rephrase (token cost; tips already one sentence).
        return new ArrayList<>(tips);
    }

    private String buildTip(RequirementMatch req) {
        String label = label(req);

        return switch (req.status()) {
            case MATCHED ->
                    "Your resume already shows " + label
                            + ". Consider placing the strongest related bullet earlier if relevant.";
            case PARTIAL ->
                    "Related evidence exists for " + label
                            + ", but it is not explicit. If you have this experience, state it clearly in the relevant project.";
            case NOT_EVIDENCED ->
                    "If you have genuinely used " + label
                            + ", consider adding it to the relevant project. Do not add it otherwise.";
            default -> null;
        };
    }

    private static int priority(RequirementMatch req) {
        return switch (req.status()) {
            case PARTIAL -> 0;
            case NOT_EVIDENCED -> 1;
            case MATCHED -> 2;
            default -> 3;
        };
    }

    static String label(RequirementMatch req) {
        String fromExpression = labelExpression(req.expression());
        if (fromExpression != null && !fromExpression.isBlank() && fromExpression.length() <= 40) {
            return fromExpression;
        }
        String original = sanitize(req.requirement());
        if (!original.isBlank()) {
            return original;
        }
        return fromExpression == null ? "" : fromExpression;
    }

    private static String labelExpression(RequirementExpression expression) {
        if (expression instanceof RequirementExpression.Concept concept) {
            return sanitize(concept.name());
        }
        if (expression instanceof RequirementExpression.AnyOf anyOf) {
            return join(anyOf.children(), " / ");
        }
        if (expression instanceof RequirementExpression.AllOf allOf) {
            return join(allOf.children(), " + ");
        }
        return null;
    }

    private static String join(List<RequirementExpression> children, String separator) {
        if (children == null || children.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (RequirementExpression child : children) {
            String part = labelExpression(child);
            if (part != null && !part.isBlank()) {
                parts.add(part);
            }
        }
        if (parts.isEmpty()) {
            return null;
        }
        String joined = String.join(separator, parts);
        return joined.length() > 80 ? joined.substring(0, 80) : joined;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replaceAll("\\s+", " ").trim();
        cleaned = cleaned.replaceAll("[.]+$", "").trim();
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 57).trim() + "…";
        }
        return cleaned;
    }
}
