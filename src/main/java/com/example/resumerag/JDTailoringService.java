package com.example.resumerag;

import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Generate concise, truthful tailoring recommendations based on JD alignment.
 * 
 * - Never invent resume facts
 * - Use conditional language when resume evidence is absent
 * - Identify high-impact improvements
 * - Limit to 3-5 actionable suggestions
 */
@Service
public class JDTailoringService {

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

        Set<String> tips = new HashSet<>();

        // High-impact JD-driven suggestions
        for (RequirementMatch req : requirements) {
            if (req == null || req.requirement() == null) continue;

            if (req.status() == RequirementStatus.NOT_EVIDENCED) {
                // Conditional: "If you have X, surface it"
                String tip = buildConditionalTip(req.requirement());
                if (tip != null && !tip.isBlank()) {
                    tips.add(tip);
                }
            } else if (req.status() == RequirementStatus.PARTIAL) {
                // Suggestion: strengthen existing evidence
                String tip = buildStrengthTip(req.requirement());
                if (tip != null && !tip.isBlank()) {
                    tips.add(tip);
                }
            }
        }

        // Ensure no duplicates, cap at 5
        List<String> result = new ArrayList<>(tips);
        if (result.size() > 5) {
            result = result.subList(0, 5);
        }

        return result;
    }

    private String buildConditionalTip(String requirement) {
        String clean = sanitizeRequirement(requirement);
        if (clean.length() > 100) {
            clean = clean.substring(0, 100);
        }

        // Format: "If you have X, mention it in [context]."
        String context = inferContext(clean);
        return "If you have " + clean + ", mention it" + context + ".";
    }

    private String buildStrengthTip(String requirement) {
        String clean = sanitizeRequirement(requirement);
        if (clean.length() > 100) {
            clean = clean.substring(0, 100);
        }

        // Format: "Strengthen evidence for X by..."
        return "Strengthen evidence for " + clean + " with specific examples or quantified results.";
    }

    private String sanitizeRequirement(String req) {
        return req.replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).trim();
    }

    private String inferContext(String requirement) {
        String lower = requirement.toLowerCase(Locale.ROOT);
        if (lower.contains("project") || lower.contains("tool")) {
            return " in the relevant project description";
        }
        if (lower.contains("experience") || lower.contains("year")) {
            return " explicitly in your employment history";
        }
        if (lower.contains("skill") || lower.contains("technolog")) {
            return " in the project where it was used";
        }
        return " explicitly";
    }
}
