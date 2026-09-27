package com.example.resumerag.analysis;

import com.example.resumerag.model.ExperienceCondition;
import com.example.resumerag.model.RequirementExpression;
import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementType;
import com.example.resumerag.model.Verifiability;
import com.example.resumerag.skill.ExperienceDurationParser;
import com.example.resumerag.skill.SkillRegistry;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * LLM-independent JD extraction from bullets/sections and known skill aliases.
 * Used when ChatClient fails or returns no usable requirements.
 */
public final class DeterministicJdRequirementExtractor {

    private static final int MAX_STRUCTURAL = 12;
    private static final int MAX_LABEL_WORDS = 8;

    private static final Pattern FLUFF = Pattern.compile(
            "(?i)^(we are looking for|join our team|responsibilities include|"
                    + "the candidate|good company culture|about us|who we are)\\b.*"
    );

    private static final Pattern SECTION_HEADER = Pattern.compile(
            "(?i)^(responsibilities|requirements|qualifications|duties|"
                    + "technical skills|about the role|about us)\\s*:?$"
    );

    /**
     * Words that are common in role titles, used to detect whether the first
     * non-blank JD line is a job title rather than a scorable requirement.
     * This list is deliberately short — only unambiguous role-title words.
     */
    private static final Pattern ROLE_TITLE_WORD = Pattern.compile(
            "(?i)\\b(intern|manager|analyst|engineer|developer|designer|"
                    + "coordinator|associate|officer|specialist|director|"
                    + "executive|assistant|lead|head|architect|consultant|"
                    + "administrator|technician|supervisor|representative|"
                    + "staff|trainee|graduate|junior|senior|principal)\\b"
    );

    private static final Pattern LEADING_FILLER = Pattern.compile(
            "(?i)^(assist(?:ing)?(?:\\s+with|\\s+in)?|support(?:ing)?|"
                    + "handle|handling|maintain(?:ing)?|"
                    + "coordinat(?:e|ing)(?:\\s+with)?|manag(?:e|ing)|"
                    + "perform(?:ing)?|provid(?:e|ing)|ensur(?:e|ing)|"
                    + "help(?:ing)?(?:\\s+with)?|respond(?:ing)?(?:\\s+to)?|"
                    + "work(?:ing)?\\s+with|ability to|able to|"
                    + "responsible for|proficiency (?:with|in)|proficient (?:with|in)|"
                    + "experience (?:with|in)|strong|excellent|outstanding|"
                    + "proven|solid|basic|good|must (?:have|demonstrate))\\s+"
    );

    private static final Pattern COMPOUND_DUTY = Pattern.compile(
            "(?i)\\s+and\\s+(?:assist|support|handle|maintain|coordinate|"
                    + "manage|perform|provide|ensure|help|respond|work|escalate)\\b.*"
    );

    private static final Pattern TRAILING_FLUFF = Pattern.compile(
            "(?i)\\s+(?:professionally|when necessary|as needed|as required|"
                    + "and other\\b.*|to support\\b.*|in order to\\b.*)$"
    );

    private static final Pattern INCLUDING = Pattern.compile(
            "(?i)\\bincluding\\s+"
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
                    "\\bteamwork\\b", "\\bcollaborate with\\b", "\\bteam members\\b",
                    "\\bwork effectively\\b"),
            new ConceptPattern("Customer Service", RequirementType.SKILL, RequirementImportance.HIGH,
                    "\\bcustomer service\\b"),
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

        // Identify the role title (first non-blank line) so we can exclude it
        // from requirement matching. A job title is not a candidate requirement.
        String roleTitleLine = detectRoleTitleLine(text);

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

        int structural = 0;
        RequirementImportance defaultImportance = RequirementImportance.MEDIUM;
        for (String fragment : splitFragments(text)) {
            if (SECTION_HEADER.matcher(fragment).matches()) {
                String header = fragment.toLowerCase(Locale.ROOT);
                defaultImportance = header.startsWith("requirement")
                        || header.startsWith("qualification")
                        ? RequirementImportance.HIGH
                        : RequirementImportance.MEDIUM;
                continue;
            }
            // Skip the role title — it describes the position, not a candidate requirement.
            if (roleTitleLine != null && fragment.equalsIgnoreCase(roleTitleLine)) {
                continue;
            }
            if (structural >= MAX_STRUCTURAL) {
                break;
            }
            List<String> labels = labelsFromFragment(fragment);
            for (String label : labels) {
                if (structural >= MAX_STRUCTURAL) {
                    break;
                }
                if (addStructural(found, label, inferType(label, fragment), defaultImportance, fragment)) {
                    structural++;
                }
            }
        }

        mergeRelatedSkills(found);

        return List.copyOf(found.values());
    }

    /**
     * Returns the first non-blank line of the job description if it looks like a
     * role title (short, title-case, contains a role-title word, no colon).
     * Returns null if the first line does not match the heuristic.
     *
     * <p>This is intentionally conservative: a line must both be short AND contain
     * a recognized role-level word to be excluded. Responsibility bullets that
     * happen to contain the word "manager" are unaffected because they are longer
     * and typically start with an action verb.
     */
    private static String detectRoleTitleLine(String text) {
        for (String rawLine : text.split("\\r?\\n")) {
            String line = cleanBullet(rawLine);
            if (line.isBlank()) {
                continue;
            }
            // Must be short (≤ 8 words), contain no colon, and contain a role-title word.
            int wordCount = line.trim().split("\\s+").length;
            if (wordCount <= 8
                    && !line.contains(":")
                    && ROLE_TITLE_WORD.matcher(line).find()) {
                return line;
            }
            // Only inspect the very first non-blank line.
            break;
        }
        return null;
    }

    private static void mergeRelatedSkills(Map<String, JobRequirement> found) {
        if (found.containsKey("SQL") && found.containsKey("DBMS")) {
            found.remove("SQL");
            found.remove("DBMS");
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
        }

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
        }
    }

    private static List<String> splitFragments(String text) {
        String prepared = text
                // A role title that precedes a section header describes the job,
                // not a candidate requirement (for example, "Coordinator Duties").
                .replaceFirst("(?im)^\\s*[^\\r\\n]{3,80}?\\s+(responsibilities|requirements|duties)\\s*:", "$1:")
                .replaceAll("(?i)\\s+(responsibilities|requirements|qualifications|duties|technical skills)\\s*:", "\n$1:\n")
                .replaceAll("[•●▪]", "\n")
                .replaceAll("\\s+-\\s+", "\n")
                .replace('\r', '\n');

        List<String> fragments = new ArrayList<>();
        for (String line : prepared.split("\n")) {
            String cleaned = cleanBullet(line);
            if (cleaned.isBlank()) {
                continue;
            }
            if (cleaned.length() > 140) {
                for (String sentence : cleaned.split("(?<=[.?;])\\s+")) {
                    String part = cleanBullet(sentence);
                    if (!part.isBlank()) {
                        fragments.add(part);
                    }
                }
            } else {
                fragments.add(cleaned);
            }
        }
        return fragments;
    }

    private static List<String> labelsFromFragment(String fragment) {
        if (fragment == null || fragment.isBlank() || FLUFF.matcher(fragment).matches()) {
            return List.of();
        }
        if (SECTION_HEADER.matcher(fragment).matches() || looksLikeSectionHeader(fragment)) {
            return List.of();
        }

        List<String> labels = new ArrayList<>();
        String working = fragment.trim();

        // "X including A, B and C" → extract prefix + each item.
        Matcher including = INCLUDING.matcher(working);
        if (including.find()) {
            String prefix = working.substring(0, including.start()).trim();
            String rest = working.substring(including.end()).trim();
            addIfPresent(labels, toLabel(prefix));
            for (String item : rest.split("\\s*(?:,|/|\\band\\b|\\bor\\b)\\s*")) {
                addIfPresent(labels, toLabel(item));
            }
            return labels;
        }

        // Strip leading filler verbs, then check whether the remainder is a
        // comma-separated list of independently-testable items.
        // E.g. "Coordinate with housekeeping, food and beverage departments"
        //   → strip "Coordinate with" → "housekeeping, food and beverage departments"
        //   → split → ["housekeeping", "food and beverage departments"]
        String stripped = stripLeadingFiller(working);
        if (!stripped.equals(working) && stripped.contains(",")) {
            // Split on commas; keep "and" conjunctions within items
            String[] parts = stripped.split("\\s*,\\s*");
            if (parts.length >= 2) {
                for (String part : parts) {
                    // Further split the last item on " and " if it joins exactly two nouns
                    // (e.g. "food and beverage departments" should stay together as a concept)
                    addIfPresent(labels, toLabel(part));
                }
                return labels;
            }
        }

        addIfPresent(labels, toLabel(working));
        return labels;
    }

    /**
     * Strips leading filler verb phrases from {@code text} and returns the remainder.
     * Returns the original text unchanged if no filler was found.
     */
    private static String stripLeadingFiller(String text) {
        String s = text;
        Matcher filler = LEADING_FILLER.matcher(s);
        int guard = 0;
        while (filler.find() && filler.start() == 0 && guard++ < 6) {
            s = s.substring(filler.end()).trim();
            filler = LEADING_FILLER.matcher(s);
        }
        return s;
    }

    private static void addIfPresent(List<String> labels, String label) {
        if (label != null && !label.isBlank()) {
            labels.add(label);
        }
    }

    private static String toLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        text = text.replaceAll("[.]+$", "").trim();
        if (text.isBlank()) {
            return null;
        }

        Matcher filler = LEADING_FILLER.matcher(text);
        int guard = 0;
        while (filler.find() && filler.start() == 0 && guard++ < 6) {
            text = text.substring(filler.end()).trim();
            filler = LEADING_FILLER.matcher(text);
        }

        text = COMPOUND_DUTY.matcher(text).replaceFirst("").trim();
        text = TRAILING_FLUFF.matcher(text).replaceFirst("").trim();
        text = text.replaceAll("(?i)\\s+skills$", "").trim();
        text = text.replaceAll("[.]+$", "").trim();
        text = text.replaceAll(",$", "").trim();

        if (text.isBlank() || FLUFF.matcher(text).matches()) {
            return null;
        }

        String[] words = text.split("\\s+");
        if (words.length > MAX_LABEL_WORDS) {
            text = String.join(" ", java.util.Arrays.copyOf(words, MAX_LABEL_WORDS));
        }

        if (text.length() < 3 || text.length() > 80) {
            return null;
        }
        if (isLowContent(text)) {
            return null;
        }
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    private static boolean isLowContent(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9+# ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.isBlank()
                || normalized.matches("tasks|duties|things|role|candidate|other|various");
    }

    private static boolean addStructural(
            Map<String, JobRequirement> found,
            String label,
            RequirementType type,
            RequirementImportance importance
    ) {
        return addStructural(found, label, type, importance, label);
    }

    private static boolean addStructural(
            Map<String, JobRequirement> found,
            String label,
            RequirementType type,
            RequirementImportance importance,
            String sourceFragment
    ) {
        if (label == null || label.isBlank() || isLowContent(label)) {
            return false;
        }
        if (alreadyRepresented(found, label)) {
            return false;
        }

        ExperienceCondition experience = null;
        Integer months = ExperienceDurationParser.parseMonths(sourceFragment);
        if (months != null) {
            experience = new ExperienceCondition(months, null, sourceFragment.trim());
            type = RequirementType.EXPERIENCE;
        }

        found.put(label, new JobRequirement(
                label,
                expressionFromLabel(label),
                type,
                importance,
                experience,
                Verifiability.VERIFIABLE
        ));
        return true;
    }

    private static boolean alreadyRepresented(Map<String, JobRequirement> found, String label) {
        String needle = normalizeKey(label);
        if (needle.isBlank()) {
            return true;
        }
        if (found.containsKey(label) || found.containsKey(needle)) {
            return true;
        }
        for (String existing : found.keySet()) {
            String hay = normalizeKey(existing);
            if (hay.equals(needle) || hay.contains(needle) || needle.contains(hay) && hay.length() >= 4) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeKey(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#]+", " ").trim();
    }

    private static RequirementExpression expressionFromLabel(String label) {
        String[] alternatives = label.split("(?i)\\s+or\\s+");
        if (alternatives.length == 2
                && wordCount(alternatives[0]) <= 4
                && wordCount(alternatives[1]) <= 4) {
            return new RequirementExpression.AnyOf(List.of(
                    new RequirementExpression.Concept(alternatives[0].trim()),
                    new RequirementExpression.Concept(alternatives[1].trim())
            ));
        }
        return new RequirementExpression.Concept(label);
    }

    private static int wordCount(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            return 0;
        }
        return trimmed.split("\\s+").length;
    }

    private static RequirementType inferType(String label, String fragment) {
        String lower = (label + " " + fragment).toLowerCase(Locale.ROOT);
        if (lower.contains("degree") || lower.contains("bachelor") || lower.contains("education")) {
            return RequirementType.EDUCATION;
        }
        if (ExperienceDurationParser.parseMonths(fragment) != null
                || lower.contains("intern")
                || lower.contains("experience")) {
            return RequirementType.EXPERIENCE;
        }
        return RequirementType.SKILL;
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
                || SECTION_HEADER.matcher(lower).matches();
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
