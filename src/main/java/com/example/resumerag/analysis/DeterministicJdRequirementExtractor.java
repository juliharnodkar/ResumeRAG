package com.example.resumerag.analysis;

import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.example.resumerag.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * LLM-independent JD extraction from bullets/sections and known skill aliases.
 * Used when ChatClient fails or returns no usable requirements.
 */
public final class DeterministicJdRequirementExtractor {

    private static final Pattern FLUFF = Pattern.compile(
            "(?i)^(we are looking for|join our team|responsibilities include|"
                    + "the candidate|good company culture|about us|who we are)\\b.*"
    );

    private static final List<ConceptPattern> EXTRA = List.of(
            new ConceptPattern("OOP", RequirementType.SKILL, RequirementImportance.HIGH,
                    "\\bobject[- ]oriented programming\\b", "\\boop\\b"),
            new ConceptPattern("HTTP", RequirementType.SKILL, RequirementImportance.HIGH,
                    "\\bhttp\\b"),
            new ConceptPattern("Version Control", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bversion control\\b"),
            new ConceptPattern("Web Technologies", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bweb technologies\\b", "\\bweb development\\b"),
            new ConceptPattern("SDLC", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bsoftware development life\\s*cycle\\b", "\\bsdlc\\b"),
            new ConceptPattern("Problem Solving", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bproblem[- ]solving\\b"),
            new ConceptPattern("Debugging", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bdebugging\\b", "\\bdebug\\b"),
            new ConceptPattern("Communication", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bcommunication\\b"),
            new ConceptPattern("Teamwork", RequirementType.SKILL, RequirementImportance.MEDIUM,
                    "\\bteamwork\\b", "\\bcollaborate with\\b", "\\bteam members\\b"),
            new ConceptPattern("Software Development Internship/Project", RequirementType.EXPERIENCE, RequirementImportance.HIGH,
                    "\\binternship\\b", "\\bacademic project\\b", "\\bsoftware development\\b.*\\bexperience\\b")
    );

    private DeterministicJdRequirementExtractor() {}

    public static List<JobRequirement> extract(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) {
            return List.of();
        }

        String text = jobDescription;
        String lower = text.toLowerCase(Locale.ROOT);
        Map<String, JobRequirement> found = new LinkedHashMap<>();

        for (String canonical : SkillRegistry.canonicalSkills()) {
            if (SkillRegistry.contains(text, canonical)) {
                found.putIfAbsent(canonical, concept(canonical, RequirementType.SKILL, RequirementImportance.HIGH));
            }
        }

        for (ConceptPattern pattern : EXTRA) {
            if (pattern.matches(lower) && !found.containsKey(pattern.canonical)) {
                found.put(pattern.canonical, concept(pattern.canonical, pattern.type, pattern.importance));
            }
        }

        if (lower.contains("bachelor") || lower.contains("degree")) {
            found.putIfAbsent(
                    "Bachelor's Degree",
                    educationRequirement(text)
            );
        }

        for (String line : text.split("\\r?\\n")) {
            String cleaned = cleanBullet(line);
            if (cleaned.isBlank() || FLUFF.matcher(cleaned).matches()) {
                continue;
            }
            if (cleaned.length() < 2 || cleaned.length() > 80) {
                continue;
            }
            if (looksLikeSectionHeader(cleaned)) {
                continue;
            }
            // Short bullet skills already covered by registry; skip inventing long prose.
        }

        // Merge related SQL/DBMS into one AnyOf when both appear.
        if (found.containsKey("SQL") && found.containsKey("DBMS")) {
            JobRequirement sql = found.remove("SQL");
            JobRequirement dbms = found.remove("DBMS");
            found.put(
                    "SQL / Database",
                    new JobRequirement(
                            "SQL / Database",
                            new RequirementExpression.AnyOf(List.of(
                                    new RequirementExpression.Concept("SQL"),
                                    new RequirementExpression.Concept("DBMS")
                            )),
                            RequirementType.SKILL,
                            RequirementImportance.HIGH,
                            null,
                            Verifiability.VERIFIABLE
                    )
            );
            // keep ordering hints unused
            if (sql == null || dbms == null) {
                // no-op
            }
        }

        // Merge REST API + HTTP when both present.
        if (found.containsKey("REST API") && found.containsKey("HTTP")) {
            found.remove("REST API");
            found.remove("HTTP");
            found.put(
                    "REST APIs / HTTP",
                    new JobRequirement(
                            "REST APIs / HTTP",
                            new RequirementExpression.AllOf(List.of(
                                    new RequirementExpression.Concept("REST APIs"),
                                    new RequirementExpression.Concept("HTTP")
                            )),
                            RequirementType.SKILL,
                            RequirementImportance.HIGH,
                            null,
                            Verifiability.VERIFIABLE
                    )
            );
        }

        // Merge Git + Version Control
        if (found.containsKey("Git") && found.containsKey("Version Control")) {
            found.remove("Git");
            found.remove("Version Control");
            found.put(
                    "Git / Version Control",
                    new JobRequirement(
                            "Git / Version Control",
                            new RequirementExpression.AnyOf(List.of(
                                    new RequirementExpression.Concept("Git"),
                                    new RequirementExpression.Concept("Version Control")
                            )),
                            RequirementType.SKILL,
                            RequirementImportance.MEDIUM,
                            null,
                            Verifiability.VERIFIABLE
                    )
            );
        } else if (found.containsKey("Version Control") && !found.containsKey("Git")) {
            // keep Version Control as-is
        }

        return List.copyOf(found.values());
    }

    private static JobRequirement concept(
            String name,
            RequirementType type,
            RequirementImportance importance) {
        return new JobRequirement(
                name,
                new RequirementExpression.Concept(name),
                type,
                importance,
                null,
                Verifiability.VERIFIABLE
        );
    }

    private static JobRequirement educationRequirement(String text) {
        List<RequirementExpression> fields = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("computer science")) {
            fields.add(new RequirementExpression.Concept("Computer Science"));
        }
        if (lower.contains("computer engineering")) {
            fields.add(new RequirementExpression.Concept("Computer Engineering"));
        }
        if (Pattern.compile("\\bit\\b|information technology").matcher(lower).find()) {
            fields.add(new RequirementExpression.Concept("IT"));
        }
        if (lower.contains("related field") || lower.contains("related discipline")) {
            fields.add(new RequirementExpression.Concept("related field"));
        }

        RequirementExpression expression;
        if (fields.isEmpty()) {
            expression = new RequirementExpression.Concept("Bachelor's degree");
        } else {
            expression = new RequirementExpression.AllOf(List.of(
                    new RequirementExpression.Concept("Bachelor's degree"),
                    new RequirementExpression.AnyOf(fields)
            ));
        }

        return new JobRequirement(
                "Bachelor's Degree",
                expression,
                RequirementType.EDUCATION,
                RequirementImportance.HIGH,
                null,
                Verifiability.VERIFIABLE
        );
    }

    private static String cleanBullet(String line) {
        return line == null
                ? ""
                : line.replaceFirst("^[\\s•\\-*\\d.]+", "").trim();
    }

    private static boolean looksLikeSectionHeader(String line) {
        String lower = line.toLowerCase(Locale.ROOT);
        return lower.endsWith(":")
                || lower.equals("qualifications")
                || lower.equals("requirements")
                || lower.equals("technical skills")
                || lower.equals("responsibilities")
                || lower.equals("about the role");
    }

    private record ConceptPattern(
            String canonical,
            RequirementType type,
            RequirementImportance importance,
            String... regexes
    ) {
        boolean matches(String lower) {
            for (String regex : regexes) {
                if (Pattern.compile(regex).matcher(lower).find()) {
                    return true;
                }
            }
            return false;
        }
    }
}
