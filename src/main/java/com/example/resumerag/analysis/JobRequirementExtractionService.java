package com.example.resumerag.analysis;

import com.example.resumerag.model.ExperienceCondition;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.example.resumerag.skill.SkillRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class JobRequirementExtractionService {

    private static final Logger log =
            LoggerFactory.getLogger(JobRequirementExtractionService.class);

    private static final String EXTRACTION_PROMPT = """
            Extract concrete job requirements as a JSON array. JSON only.

            Each item:
            {"originalText":"short canonical label","expression":{"type":"Concept","name":"Git"} OR AnyOf/AllOf,"type":"SKILL|EXPERIENCE|PROJECT|DOMAIN|EDUCATION|OTHER","importance":"HIGH|MEDIUM|LOW","experienceRequirement":null,"verifiability":"VERIFIABLE|NOT_VERIFIABLE"}

            Rules:
            - Extract stated skills, tools, education, experience, and behaviors.
            - Canonicalize names (OOP, SQL, REST APIs, Git, DBMS).
            - AnyOf = alternatives. AllOf = jointly required. Nested OK.
            - Skip fluff (we are looking for, join our team, culture).
            - Do not invent requirements.
            - If the JD has ordinary requirements, do not return [].

            JD:
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public JobRequirementExtractionService(
            ChatClient.Builder builder,
            ObjectMapper objectMapper) {

        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<JobRequirement> extract(String jobDescription) {

        if (jobDescription == null || jobDescription.isBlank()) {
            log.info("JD extraction skipped: empty JD. chars=0");
            return List.of();
        }

        log.info("JD extraction start: chars={}", jobDescription.length());
        log.info("JD text received: {}", truncate(jobDescription, 800));

        List<JobRequirement> llmExtracted = extractWithLlm(jobDescription);
        if (!llmExtracted.isEmpty()) {
            log.info(
                    "JD extraction source=LLM count={} names={}",
                    llmExtracted.size(),
                    llmExtracted.stream().map(JobRequirement::originalText).toList()
            );
            return llmExtracted;
        }

        List<JobRequirement> deterministic =
                DeterministicJdRequirementExtractor.extract(jobDescription);
        log.warn(
                "JD extraction source=DETERMINISTIC_FALLBACK count={} names={} (LLM empty/failed)",
                deterministic.size(),
                deterministic.stream().map(JobRequirement::originalText).toList()
        );
        return deterministic;
    }

    private List<JobRequirement> extractWithLlm(String jobDescription) {
        try {
            String response = chatClient.prompt()
                    .user(EXTRACTION_PROMPT + jobDescription)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                log.warn("JD extraction LLM returned empty response.");
                return List.of();
            }

            log.info("JD extraction LLM response: {}", truncate(response, 800));

            String json = cleanJson(response);
            JsonNode root = objectMapper.readTree(json);

            if (!root.isArray()) {
                log.warn("JD extraction parse failure: response was not a JSON array.");
                return List.of();
            }

            List<JobRequirement> extracted = new ArrayList<>();

            for (JsonNode node : root) {
                try {
                    recoverIncompleteItem(node);
                    JobRequirement requirement = parseRequirement(node);
                    if (requirement != null && !isSectionHeaderLabel(requirement.originalText())) {
                        extracted.add(requirement);
                    }
                } catch (Exception parseEx) {
                    log.warn("JD extraction skipped one item: {}", parseEx.getMessage());
                }
            }

            return extracted;

        } catch (Exception ex) {
            log.warn(
                    "JD extraction LLM failed: {} - {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage()
            );
            return List.of();
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        String compact = value.replaceAll("\\s+", " ").trim();
        return compact.length() <= max ? compact : compact.substring(0, max) + "…";
    }

    /**
     * Local LLMs often omit verifiability or emit expression:null.
     * Fill only missing fields. Do not invent AllOf from legacy concept arrays.
     */
    private void recoverIncompleteItem(JsonNode node) {
        if (!(node instanceof ObjectNode object)) {
            return;
        }

        String originalText = text(object, "originalText");

        if (missing(object, "verifiability")) {
            object.put("verifiability", "VERIFIABLE");
        }
        if (missing(object, "importance")) {
            object.put("importance", "MEDIUM");
        }
        if (missing(object, "type")) {
            object.put("type", inferType(originalText));
        }

        JsonNode expression = object.get("expression");
        boolean expressionMissing = expression == null
                || expression.isNull()
                || (expression.isObject() && missing(expression, "type"));

        if (expressionMissing && originalText != null && !originalText.isBlank()) {
            ObjectNode concept = object.putObject("expression");
            concept.put("type", "Concept");
            concept.put("name", originalText.trim());
        }
    }

    private boolean missing(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || (value.isTextual() && value.asText().isBlank());
    }

    private String inferType(String originalText) {
        if (originalText == null) {
            return "SKILL";
        }
        String lower = originalText.toLowerCase(Locale.ROOT);
        if (lower.contains("degree") || lower.contains("bachelor") || lower.contains("education")) {
            return "EDUCATION";
        }
        if (lower.contains("intern") || lower.contains("year") || lower.contains("experience")) {
            return "EXPERIENCE";
        }
        return "SKILL";
    }

    private String cleanJson(String response) {

        String json = response.trim();

        if (json.startsWith("```")) {

            json = json
                    .replaceFirst("^```(?:json)?\\s*", "")
                    .replaceFirst("\\s*```$", "")
                    .trim();
        }

        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');

        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }

        return json.trim();
    }

    private JobRequirement parseRequirement(JsonNode node) {

        if (node == null || node.isNull()) {
            return null;
        }

        String originalText = text(node, "originalText");

        if (originalText == null || originalText.isBlank()) {
            return null;
        }

        /*
         * A requirement is valid only when the model supplies the
         * structured expression tree explicitly.
         *
         * We must never infer logical semantics from a legacy flat
         * concepts/skills array because doing so can turn alternatives
         * into jointly-required concepts.
         *
         * Validate presence here so malformed legacy payloads receive
         * the requirement-level contract error. parseExpression()
         * remains responsible for validating the expression itself.
         */
        JsonNode expressionNode = node.get("expression");

        if (expressionNode == null || expressionNode.isNull()) {
            throw new IllegalArgumentException(
                "Requirement extraction returned no structured expression for: "
                    + originalText
            );
        }

        RequirementExpression expression =
                parseExpression(expressionNode);

        RequirementType type =
                parseType(text(node, "type"));

        RequirementImportance importance =
                parseImportance(text(node, "importance"));

        ExperienceCondition experienceCondition =
                parseExperienceCondition(
                        text(node, "experienceRequirement")
                );

        Verifiability verifiability =
                parseVerifiability(text(node, "verifiability"));

        return new JobRequirement(
                originalText.trim(),
                expression,
                type,
                importance,
                experienceCondition,
                verifiability
        );
    }

    private RequirementExpression parseExpression(
            JsonNode node) {

        if (node == null
                || node.isNull()
                || !node.isObject()) {
            throw new IllegalArgumentException(
                    "Requirement expression must be a JSON object"
            );
        }

        String type = text(node, "type");

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement expression type is required"
            );
        }

        return switch (type.trim().toLowerCase()) {

            case "concept" -> {
                String name = text(node, "name");

                if (name == null || name.isBlank()) {
                    throw new IllegalArgumentException(
                            "Concept expression requires a non-blank name"
                    );
                }

                yield new RequirementExpression.Concept(
                        normalizeConcept(name)
                );
            }

            case "allof" -> {
                List<RequirementExpression> children =
                        parseChildren(node);

                if (children.isEmpty()) {
                    throw new IllegalArgumentException(
                            "AllOf expression requires at least one child"
                    );
                }

                yield new RequirementExpression.AllOf(children);
            }

            case "anyof" -> {
                List<RequirementExpression> children =
                        parseChildren(node);

                if (children.isEmpty()) {
                    throw new IllegalArgumentException(
                            "AnyOf expression requires at least one child"
                    );
                }

                yield new RequirementExpression.AnyOf(children);
            }

            default -> throw new IllegalArgumentException(
                    "Unsupported requirement expression type: " + type
            );
        };
    }


    private List<RequirementExpression> parseChildren(
            JsonNode node) {

        JsonNode childrenNode = node.get("children");

        if (childrenNode == null
                || !childrenNode.isArray()) {
            throw new IllegalArgumentException(
                    "Logical requirement expression requires a children array"
            );
        }

        if (childrenNode.isEmpty()) {
            throw new IllegalArgumentException(
                    "Logical requirement expression requires at least one child"
            );
        }

        List<RequirementExpression> children =
                new ArrayList<>();

        for (JsonNode childNode : childrenNode) {
            children.add(parseExpression(childNode));
        }

        return List.copyOf(children);
    }

    private String normalizeConcept(String concept) {

        if (concept == null || concept.isBlank()) {
            throw new IllegalArgumentException(
                    "Concept name must not be blank"
            );
        }

        String trimmed = concept.trim();
        String canonical = SkillRegistry.canonicalize(trimmed);
        return canonical != null ? canonical : trimmed;
    }

    private RequirementType parseType(String type) {

        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement type is required"
            );
        }

        try {
            return RequirementType.valueOf(
                    type.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            String normalized = type.trim().toUpperCase(Locale.ROOT);
            if (normalized.equals("BEHAVIOR") || normalized.equals("SOFT_SKILL") || normalized.equals("SOFT")) {
                return RequirementType.SKILL;
            }
            throw new IllegalArgumentException(
                    "Unsupported requirement type: " + type,
                    ex
            );
        }
    }

    private RequirementImportance parseImportance(
            String importance) {

        if (importance == null || importance.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement importance is required"
            );
        }

        try {
            return RequirementImportance.valueOf(
                    importance.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unsupported requirement importance: " + importance,
                    ex
            );
        }
    }

    private Verifiability parseVerifiability(String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Requirement verifiability is required"
            );
        }

        try {
            return Verifiability.valueOf(
                    value.trim().toUpperCase()
            );
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unsupported requirement verifiability: " + value,
                    ex
            );
        }
    }
    private ExperienceCondition parseExperienceCondition(
            String experienceRequirement) {

        if (experienceRequirement == null
                || experienceRequirement.isBlank()) {
            return null;
        }

        return new ExperienceCondition(
                null,
                null,
                experienceRequirement.trim()
        );
    }

    private String text(
            JsonNode node,
            String field) {

        if (node == null) {
            return null;
        }

        JsonNode value = node.get(field);

        if (value == null
                || value.isNull()
                || !value.isValueNode()) {
            return null;
        }

        String valueText = value.asText();

        return valueText == null || valueText.isBlank()
                ? null
                : valueText.trim();
    }

    /**
     * Detect JD section headers that the LLM may extract as requirement names.
     * These are classification labels (e.g., "PREFERRED", "REQUIRED"), not
     * actual skills or experience requirements.
     */
    private static boolean isSectionHeaderLabel(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .trim();
        return normalized.matches(
                "preferred|required|nice to have|bonus|desired|mandatory"
                + "|minimum|optional|requirements|qualifications"
                + "|responsibilities|duties|technical skills"
                + "|preferred qualifications|required qualifications"
                + "|minimum qualifications|desired qualifications"
                + "|about the role|about us|who we are"
        );
    }
}
