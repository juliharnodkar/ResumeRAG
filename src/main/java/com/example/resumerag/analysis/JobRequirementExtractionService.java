package com.example.resumerag.analysis;

import com.example.resumerag.model.ExperienceCondition;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class JobRequirementExtractionService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public JobRequirementExtractionService(
            ChatClient.Builder builder,
            ObjectMapper objectMapper) {

        this.chatClient = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<JobRequirement> extract(String jobDescription) {

        String prompt = """
                You are extracting job requirements.

                Read the job description below and identify every concrete
                requirement, qualification, capability, technology,
                responsibility, domain, education requirement, or experience
                requirement that a candidate is expected to have.

                IMPORTANT:
                - Do NOT return an empty array if the job description contains
                  ordinary job requirements.
                - Preserve uncommon or unknown concepts.
                - Do not restrict extraction to software engineering.
                - Do not invent requirements.
                - Extract requirements directly stated or clearly implied
                  by the job description.
                - Preserve the logical relationship between concepts.
                - An explicit "or", "either ... or ...", or equivalent
                  alternative means ANY OF those concepts is acceptable.
                - An explicit "and", or concepts that are jointly required,
                  means ALL OF those concepts are required.
                - Do not convert alternatives into jointly required concepts.
                - Expressions may be nested.

                Return ONLY valid JSON.

                Return exactly this format:

                [
                  {
                    "originalText": "Strong proficiency in Java or Python.",
                    "expression": {
                      "type": "AnyOf",
                      "children": [
                        {
                          "type": "Concept",
                          "name": "Java"
                        },
                        {
                          "type": "Concept",
                          "name": "Python"
                        }
                      ]
                    },
                    "type": "SKILL",
                    "importance": "HIGH",
                    "experienceRequirement": null,
                    "verifiability": "VERIFIABLE"
                  }
                ]

                Expression rules:

                1. A single concept:
                {
                  "type": "Concept",
                  "name": "Java"
                }

                2. Jointly required concepts:
                {
                  "type": "AllOf",
                  "children": [
                    {"type": "Concept", "name": "clean code"},
                    {"type": "Concept", "name": "maintainable code"},
                    {"type": "Concept", "name": "unit testing"}
                  ]
                }

                3. Alternative concepts:
                {
                  "type": "AnyOf",
                  "children": [
                    {"type": "Concept", "name": "Java"},
                    {"type": "Concept", "name": "Python"}
                  ]
                }

                4. Nested logic is allowed. For example:

                "Bachelor's degree in Computer Science, Computer Engineering,
                or a related field"

                can be represented as:

                {
                  "type": "AllOf",
                  "children": [
                    {
                      "type": "Concept",
                      "name": "Bachelor's degree"
                    },
                    {
                      "type": "AnyOf",
                      "children": [
                        {
                          "type": "Concept",
                          "name": "Computer Science"
                        },
                        {
                          "type": "Concept",
                          "name": "Computer Engineering"
                        },
                        {
                          "type": "Concept",
                          "name": "related field"
                        }
                      ]
                    }
                  ]
                }

                Another example:

                "SQL and PostgreSQL or MySQL"

                should preserve the distinction between the required SQL
                capability and the PostgreSQL/MySQL alternative rather than
                requiring PostgreSQL AND MySQL.

                Allowed expression types:
                Concept
                AllOf
                AnyOf

                Allowed requirement type values:
                SKILL
                EXPERIENCE
                PROJECT
                DOMAIN
                EDUCATION
                OTHER

                Allowed importance values:
                HIGH
                MEDIUM
                LOW

                "experienceRequirement" should contain the explicit experience
                condition when one exists, otherwise null.

                "verifiability" must be VERIFIABLE when the requirement can
                reasonably be supported or assessed from resume evidence.
                Use NOT_VERIFIABLE when the requirement depends primarily
                on information a resume cannot establish, such as current
                availability, willingness to work specific shifts, willingness
                to relocate, salary expectations, or other future/personal
                conditions that are not established by resume content.

                Allowed verifiability values:
                VERIFIABLE
                NOT_VERIFIABLE

                JOB DESCRIPTION:
                %s
                """.formatted(jobDescription);

        try {

            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            if (response == null || response.isBlank()) {
                System.err.println("JD extraction returned an empty response.");
                return List.of();
            }

            String json = cleanJson(response);

            JsonNode root = objectMapper.readTree(json);

            if (!root.isArray()) {
                System.err.println("JD extraction response was not an array.");
                return List.of();
            }

            List<JobRequirement> extracted = new ArrayList<>();

            for (JsonNode node : root) {

                JobRequirement requirement = parseRequirement(node);

                if (requirement != null) {
                    extracted.add(requirement);
                }
            }

            System.out.println(
                    "JD extraction produced "
                            + extracted.size()
                            + " requirements."
            );

            return extracted;

        } catch (Exception ex) {

            return List.of();
        }
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

        return concept.trim();
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
}
