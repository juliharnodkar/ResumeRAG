package com.example.resumerag;

import com.example.resumerag.model.Evidence;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Grounded tailoring tips from verified JD status plus actual resume evidence.
 * Deterministic first; LLM is optional phrasing only and cannot invent facts.
 */
@Service
public class JDTailoringService {

    private static final int MAX_TIPS = 5;

    private final ChatClient chatClient;

    public JDTailoringService(ChatClient.Builder builder) {
        this.chatClient = builder == null ? null : builder.build();
    }

    public List<String> generateTailoringTips(
            List<RequirementMatch> requirements,
            List<String> matchedSkills,
            List<String> missingSkills,
            String jobDescription
    ) {
        return generateTailoringTips(requirements, matchedSkills, missingSkills, jobDescription, List.of());
    }

    public List<String> generateTailoringTips(
            List<RequirementMatch> requirements,
            List<String> matchedSkills,
            List<String> missingSkills,
            String jobDescription,
            List<Document> resumeChunks
    ) {
        if (requirements == null || requirements.isEmpty()) {
            return List.of();
        }

        ResumeEvidenceSnapshot snapshot = ResumeEvidenceExtractor.extract(
                resumeChunks == null ? List.of() : resumeChunks
        );

        List<RequirementMatch> ranked = requirements.stream()
                .filter(req -> req != null && req.requirement() != null)
                .filter(req -> req.status() != RequirementStatus.UNASSESSED)
                .filter(req -> req.status() != RequirementStatus.NOT_VERIFIABLE)
                .sorted(Comparator.comparingInt(JDTailoringService::priority))
                .toList();

        Set<String> tips = new LinkedHashSet<>();

        for (RequirementMatch req : ranked) {
            String tip = buildTip(req, snapshot);
            if (tip != null && !tip.isBlank()) {
                tips.add(tip);
            }
            if (tips.size() >= MAX_TIPS) {
                break;
            }
        }

        List<String> deterministic = new ArrayList<>(tips);
        Set<String> allowed = EvidenceGrounding.extraTerms(
                ranked.stream().map(JDTailoringService::label).toList()
        );
        List<String> grounded = EvidenceGrounding.keepGrounded(
                deterministic,
                snapshot.fullText(),
                allowed
        );
        if (grounded.isEmpty()) {
            grounded = deterministic;
        }
        return grounded;
    }

    private String buildTip(RequirementMatch req, ResumeEvidenceSnapshot snapshot) {
        String originalLabel = label(req);
        String titleLabel = formatTitleLabel(req);
        String location = locationFromEvidence(req, snapshot);

        return switch (req.status()) {
            case MATCHED -> location == null
                    ? title("Highlight", titleLabel, "")
                    + ": The resume already provides evidence. Keep the strongest related bullet easy to find."
                    : title("Highlight", titleLabel, "")
                    + ": The resume already provides evidence in the " + location
                    + ". Consider leading with the strongest related bullet.";
            case PARTIAL -> {
                String reqText = req.requirement() != null ? req.requirement().trim() : originalLabel;
                String evText = "related experience";
                if (req.evidence() != null && !req.evidence().isEmpty() && req.evidence().get(0) != null && req.evidence().get(0).text() != null) {
                    evText = req.evidence().get(0).text().trim();
                }
                yield location == null
                    ? title("Clarify", titleLabel, "")
                    + ": The JD requires " + reqText + ". Your resume shows " + evText + ", but the specific responsibility is not explicitly stated. If you have this experience, clarify it; do not add it otherwise."
                    : title("Clarify", titleLabel, "")
                    + ": The JD requires " + reqText + ". Your resume shows " + evText + " in the " + location + ", but the specific responsibility is not explicitly stated. If you have this experience, clarify it; do not add it otherwise.";
            }
            case NOT_EVIDENCED -> {
                boolean isResponsibility = req.type() == com.example.resumerag.model.RequirementType.EXPERIENCE
                        || req.type() == com.example.resumerag.model.RequirementType.OTHER;
                if (isResponsibility) {
                    yield location == null
                        ? title("Clarify", titleLabel, "")
                        + ": The JD requires this responsibility but it is not evidenced on your resume. If you have this experience, add it explicitly; do not invent it otherwise."
                        : title("Clarify", titleLabel, "")
                        + ": Evidence in the " + location + " shows related work, but this specific responsibility is not explicitly stated. If you handled it, clarify it; do not add it otherwise.";
                } else {
                    yield location == null
                        ? title("Add", originalLabel, "if applicable")
                        + ": This skill is not explicitly mentioned on your resume. If you have it, add it where relevant; do not add it otherwise."
                        : title("Add", originalLabel, "if applicable")
                        + ": Evidence in the " + location + " demonstrates related work, but " + originalLabel + " is not explicitly stated. If you have this skill, add it; do not add it otherwise.";
                }
            }
            default -> null;
        };
    }


    private static String formatTitleLabel(RequirementMatch req) {
        String label = label(req);
        if (req.type() == com.example.resumerag.model.RequirementType.EXPERIENCE) {
            String pattern = "(?i)^(?:(?:develop|build|improve|manage|design|ensure|lead|create|maintain|support|implement|drive|work|experience|knowledge|ability|responsible|tasked|demonstrate|perform|handle|provide|collaborate|assist|execute|oversee|analyze|evaluate|test|deploy|architect|write|review|use)(?:s|ed|ing)?(?:\\s+(?:and|or)\\s+(?:develop|build|improve|manage|design|ensure|lead|create|maintain|support|implement|drive|work|experience|knowledge|ability|responsible|tasked|demonstrate|perform|handle|provide|collaborate|assist|execute|oversee|analyze|evaluate|test|deploy|architect|write|review|use)(?:s|ed|ing)?)?)\\b\\s*(?:with\\s+|on\\s+|to\\s+|for\\s+|of\\s+|in\\s+)?";
            String stripped = label.replaceFirst(pattern, "").trim();
            if (!stripped.isEmpty()) {
                if (!stripped.toLowerCase().endsWith("experience")) {
                    stripped += " experience";
                }
                return stripped;
            }
        }
        return label;
    }

    private static String title(String action, String label, String suffix) {
        String cleanLabel = label == null ? "this requirement" : label.trim();
        int maximumLabelLength = Math.max(12, 70 - action.length() - suffix.length());
        if (cleanLabel.length() > maximumLabelLength) {
            cleanLabel = cleanLabel.substring(0, maximumLabelLength - 1).trim() + "…";
        }
        return (action + " " + cleanLabel + (suffix.isBlank() ? "" : " " + suffix)).trim();
    }

    private static String locationFromEvidence(RequirementMatch req, ResumeEvidenceSnapshot snapshot) {
        if (req.evidence() != null) {
            for (Evidence evidence : req.evidence()) {
                if (evidence == null) {
                    continue;
                }
                if (evidence.project() != null && !evidence.project().isBlank()) {
                    return evidence.project().trim() + " project";
                }
                if (evidence.section() != null && !evidence.section().isBlank()
                        && !"Resume".equalsIgnoreCase(evidence.section())
                        && !"Test".equalsIgnoreCase(evidence.section())) {
                    return evidence.section().trim() + " section";
                }
            }
        }
        return ResumeEvidenceExtractor.findRelatedLocation(snapshot, label(req));
    }

    private List<String> polishTips(
            List<String> tips,
            ResumeEvidenceSnapshot snapshot,
            String jobDescription,
            Set<String> allowed
    ) {
        if (chatClient == null || tips.isEmpty()) {
            return tips;
        }
        try {
            Tips generated = chatClient.prompt()
                    .system("""
                            You rewrite resume tailoring tips.
                            Use only the supplied tips, resume facts, and requirement names.
                            Keep unsupported skills conditional. Never invent experience.
                            """)
                    .user("""
                            Rewrite these tailoring tips so each is specific and actionable.
                            Do not add new skills, projects, employers, or metrics.

                            JOB DESCRIPTION (context only):
                            %s

                            RESUME FACTS:
                            %s

                            TIPS:
                            %s
                            """.formatted(
                            jobDescription == null ? "" : jobDescription,
                            snapshot == null ? "" : snapshot.fullText(),
                            tips
                    ))
                    .call()
                    .entity(Tips.class);
            if (generated == null || generated.tips() == null || generated.tips().isEmpty()) {
                return tips;
            }
            List<String> grounded = EvidenceGrounding.keepGrounded(generated.tips(), snapshot.fullText(), allowed);
            return grounded.isEmpty() ? tips : grounded.subList(0, Math.min(MAX_TIPS, grounded.size()));
        } catch (Exception ignored) {
            return tips;
        }
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

    record Tips(List<String> tips) {
    }
}
