package com.example.resumerag;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ResumeEvidenceExtractor {

    private static final Pattern YEAR = Pattern.compile("\\b(?:19|20)\\d{2}\\b");
    private static final Pattern MONTH_YEAR = Pattern.compile(
            "\\b(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|"
                    + "Aug(?:ust)?|Sep(?:tember)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"
                    + "\\s+(?:19|20)\\d{2}\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern QUANTIFIED = Pattern.compile(
            "\\$\\s*\\d[\\d,]*(?:\\.\\d+)?(?:\\s*(?:M|K|million|thousand|billion))?|"
                    + "\\b\\d+(?:\\.\\d+)?\\s*%|"
                    + "\\b\\d+\\s*[xX]\\b|"
                    + "\\b\\d+\\s*(?:million|thousand|billion|k)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RESPONSIBILITY = Pattern.compile(
            "\\bresponsible for\\b|\\bduties included\\b|\\btasked with\\b|\\bhelped with\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Set<String> SECTION_NAMES = Set.of(
            "experience", "skills", "skill", "projects", "project", "education",
            "summary", "about", "certifications", "certif", "coursework", "achievements"
    );
    private static final Set<String> TECH_NOISE = Set.of(
            "the", "and", "with", "using", "from", "that", "this", "built", "developed",
            "created", "designed", "led", "managed", "experience", "project", "projects",
            "skills", "education", "summary", "responsible", "worked", "team",
            "university", "college", "bachelor", "resume", "section"
    );

    private ResumeEvidenceExtractor() {
    }

    static ResumeEvidenceSnapshot extract(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return empty();
        }

        String fullText = concatenate(chunks);
        List<String> sections = detectSections(chunks, fullText);
        List<String> skillItems = extractSkillItems(chunks, fullText);
        List<ResumeEvidenceSnapshot.ProjectEvidence> projects = extractProjects(chunks, skillItems);
        List<String> metrics = extractMetrics(fullText);
        List<String> employers = extractEmployers(fullText);
        boolean hasContact = fullText.contains("@")
                || fullText.toLowerCase(Locale.ROOT).contains("linkedin")
                || fullText.toLowerCase(Locale.ROOT).contains("github")
                || fullText.toLowerCase(Locale.ROOT).contains("http");
        boolean hasLinks = fullText.toLowerCase(Locale.ROOT).contains("linkedin")
                || fullText.toLowerCase(Locale.ROOT).contains("github");
        boolean hasDates = YEAR.matcher(fullText).find() || MONTH_YEAR.matcher(fullText).find();
        boolean hasEducation = containsIgnoreCase(fullText, "education")
                || containsIgnoreCase(fullText, "bachelor")
                || containsIgnoreCase(fullText, "b.sc")
                || containsIgnoreCase(fullText, "b.tech")
                || containsIgnoreCase(fullText, "m.tech")
                || containsIgnoreCase(fullText, "b.e.")
                || containsIgnoreCase(fullText, "m.e.")
                || containsIgnoreCase(fullText, "bba")
                || containsIgnoreCase(fullText, "mba")
                || containsIgnoreCase(fullText, "diploma")
                || containsIgnoreCase(fullText, "master")
                || hasEducationSectionMetadata(chunks);
        boolean educationComplete = hasEducation && hasDates
                && (containsIgnoreCase(fullText, "university")
                || containsIgnoreCase(fullText, "college")
                || containsIgnoreCase(fullText, "b.")
                || containsIgnoreCase(fullText, "bachelor")
                || containsIgnoreCase(fullText, "master"));
        boolean responsibilityHeavy = RESPONSIBILITY.matcher(fullText).find();
        int words = fullText.isBlank() ? 0 : fullText.trim().split("\\s+").length;
        int actionVerbs = countActionVerbs(fullText.toLowerCase(Locale.ROOT));

        return new ResumeEvidenceSnapshot(
                fullText,
                List.copyOf(sections),
                List.copyOf(projects),
                List.copyOf(skillItems),
                List.copyOf(metrics),
                List.copyOf(employers),
                hasContact,
                hasLinks,
                hasDates,
                hasEducation,
                educationComplete,
                responsibilityHeavy,
                words,
                actionVerbs
        );
    }

    static String findRelatedLocation(ResumeEvidenceSnapshot snapshot, String requirementLabel) {
        if (snapshot == null || requirementLabel == null || requirementLabel.isBlank()) {
            return null;
        }
        List<String> needles = relatedNeedles(requirementLabel);
        if (needles.isEmpty()) {
            return null;
        }
        for (ResumeEvidenceSnapshot.ProjectEvidence project : snapshot.projects()) {
            if (containsAny(project.text() + " " + project.name(), needles)) {
                return project.name() + " project";
            }
        }
        String lower = snapshot.fullText().toLowerCase(Locale.ROOT);
        if (containsAny(lower, needles) && snapshot.sections().stream().anyMatch(s -> s.equalsIgnoreCase("Experience"))) {
            return "experience section";
        }
        if (containsAny(lower, needles) && snapshot.sections().stream().anyMatch(s -> s.equalsIgnoreCase("Projects"))) {
            return "Projects section";
        }
        if (containsAny(lower, needles) && snapshot.sections().stream().anyMatch(s -> s.equalsIgnoreCase("Skills"))) {
            return "Skills section";
        }
        return null;
    }

    /**
     * Returns short, verbatim experience statements that can be shown as resume
     * strengths.  This is deliberately industry-neutral: rather than mapping a
     * role to a canned strength, it lets the resume's own evidence speak.
     */
    static List<String> experienceHighlights(ResumeEvidenceSnapshot snapshot, int limit) {
        if (snapshot == null || snapshot.fullText().isBlank() || limit <= 0) {
            return List.of();
        }

        Pattern action = Pattern.compile(
                "\\b(?:worked|managed|led|supervised|trained|handled|provided|served|"
                        + "coordinated|resolved|maintained|communicated|supported|developed|"
                        + "implemented|delivered|improved|achieved)\\b",
                Pattern.CASE_INSENSITIVE
        );
        Pattern experienceMarker = Pattern.compile(
                "\\b(?:\\d+\\+?\\s+years?|experience\\s*(?::|in|with|of))\\b",
                Pattern.CASE_INSENSITIVE
        );
        LinkedHashSet<String> highlights = new LinkedHashSet<>();
        for (String candidate : snapshot.fullText().split("[\\r\\n•]+|(?<=[.!?])\\s+")) {
            String statement = candidate.replaceAll("\\s+", " ").trim();
            if (statement.length() < 24
                    || (!action.matcher(statement).find() && !experienceMarker.matcher(statement).find())) {
                continue;
            }
            if (statement.length() > 260) {
                statement = statement.substring(0, 257).trim() + "…";
            }
            highlights.add(statement);
            if (highlights.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(highlights);
    }

    private static ResumeEvidenceSnapshot empty() {
        return new ResumeEvidenceSnapshot(
                "", List.of(), List.of(), List.of(), List.of(), List.of(),
                false, false, false, false, false, false, 0, 0
        );
    }

    /**
     * Concatenate chunks into a single text, inserting newlines between chunks
     * from different sections. This prevents cross-section text merging that
     * causes education text to appear inside experience highlights.
     */
    private static String concatenate(List<Document> chunks) {
        StringBuilder builder = new StringBuilder();
        String previousSection = null;
        for (Document chunk : chunks) {
            if (chunk == null || chunk.getText() == null || chunk.getText().isBlank()) {
                continue;
            }
            String currentSection = chunk.getMetadata() == null ? null
                    : chunk.getMetadata().get("section") == null ? null
                    : String.valueOf(chunk.getMetadata().get("section"));
            if (!builder.isEmpty()) {
                // Use newline between different sections so experienceHighlights()
                // does not merge Education text with Project/Experience text.
                boolean sectionChanged = currentSection != null
                        && previousSection != null
                        && !currentSection.equalsIgnoreCase(previousSection);
                builder.append(sectionChanged ? '\n' : ' ');
            }
            builder.append(chunk.getText().trim());
            if (currentSection != null) {
                previousSection = currentSection;
            }
        }
        return builder.toString();
    }

    /**
     * Check whether any chunk has section metadata indicating Education.
     * The section heading text "Education" is consumed by ingestion and not
     * included in chunk text, so this metadata check is necessary.
     */
    private static boolean hasEducationSectionMetadata(List<Document> chunks) {
        for (Document chunk : chunks) {
            if (chunk == null || chunk.getMetadata() == null) {
                continue;
            }
            Object section = chunk.getMetadata().get("section");
            if (section != null && "Education".equalsIgnoreCase(String.valueOf(section).trim())) {
                return true;
            }
        }
        return false;
    }

    private static List<String> detectSections(List<Document> chunks, String fullText) {
        LinkedHashSet<String> sections = new LinkedHashSet<>();
        for (Document chunk : chunks) {
            Object section = chunk.getMetadata() == null ? null : chunk.getMetadata().get("section");
            if (section != null) {
                String name = String.valueOf(section).trim();
                if (!name.isBlank() && !"Resume".equalsIgnoreCase(name) && !"Test".equalsIgnoreCase(name)) {
                    sections.add(canonicalSection(name));
                }
            }
        }
        String lower = fullText.toLowerCase(Locale.ROOT);
        if (lower.contains("experience")) sections.add("Experience");
        if (lower.contains("skill")) sections.add("Skills");
        if (lower.contains("project")) sections.add("Projects");
        if (lower.contains("education")) sections.add("Education");
        if (lower.contains("summary") || lower.contains("about")) sections.add("Summary");
        if (lower.contains("certif")) sections.add("Certifications");
        return new ArrayList<>(sections);
    }

    private static String canonicalSection(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("skill")) return "Skills";
        if (lower.contains("project")) return "Projects";
        if (lower.contains("experience")) return "Experience";
        if (lower.contains("education")) return "Education";
        if (lower.contains("certif")) return "Certifications";
        if (lower.contains("summary") || lower.contains("about")) return "Summary";
        return name;
    }

    private static List<String> extractSkillItems(List<Document> chunks, String fullText) {
        LinkedHashSet<String> items = new LinkedHashSet<>();
        for (Document chunk : chunks) {
            Object section = chunk.getMetadata() == null ? null : chunk.getMetadata().get("section");
            String text = chunk.getText() == null ? "" : chunk.getText();
            if (section != null && String.valueOf(section).toLowerCase(Locale.ROOT).contains("skill")) {
                addSkillTokens(items, text);
            }
        }
        Matcher skillsHeader = Pattern.compile("(?:technical\\s+)?skills\\s*[:\\-]\\s*(.+)", Pattern.CASE_INSENSITIVE)
                .matcher(fullText);
        if (skillsHeader.find()) {
            addSkillTokens(items, skillsHeader.group(1));
        }
        return new ArrayList<>(items);
    }

    private static void addSkillTokens(Set<String> items, String text) {
        if (text == null || text.isBlank()) {
            return;
        }
        String cleaned = text.replaceAll("(?i)(?:technical\\s+)?skills\\s*[:\\-]\\s*", "");
        for (String part : cleaned.split("[,;|/•\\n]")) {
            String token = part.replaceAll("\\s+", " ").trim();
            if (token.length() < 2 || token.length() > 40) {
                continue;
            }
            if (TECH_NOISE.contains(token.toLowerCase(Locale.ROOT))) {
                continue;
            }
            if (token.split("\\s+").length > 4) {
                continue;
            }
            items.add(token);
        }
    }

    private static List<ResumeEvidenceSnapshot.ProjectEvidence> extractProjects(
            List<Document> chunks,
            List<String> skillItems
    ) {
        List<ResumeEvidenceSnapshot.ProjectEvidence> projects = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Document chunk : chunks) {
            if (chunk == null || chunk.getText() == null || chunk.getText().isBlank()) {
                continue;
            }
            Object projectMeta = chunk.getMetadata() == null ? null : chunk.getMetadata().get("project");
            Object sectionMeta = chunk.getMetadata() == null ? null : chunk.getMetadata().get("section");
            String text = chunk.getText().trim();
            String name = projectMeta == null ? null : String.valueOf(projectMeta).trim();
            boolean projectSection = sectionMeta != null
                    && String.valueOf(sectionMeta).toLowerCase(Locale.ROOT).contains("project");
            if ((name == null || name.isBlank()) && projectSection) {
                name = inferProjectName(text);
            }
            if (name == null || name.isBlank()) {
                continue;
            }
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            List<String> techs = technologiesIn(text, skillItems);
            int words = text.split("\\s+").length;
            projects.add(new ResumeEvidenceSnapshot.ProjectEvidence(name, text, techs, words));
        }
        return projects;
    }

    private static String inferProjectName(String text) {
        String first = text.split("[.\\n]")[0].trim();
        if (first.length() > 80) {
            first = first.substring(0, 80).trim();
        }
        int pipe = first.indexOf('|');
        if (pipe > 0) {
            first = first.substring(0, pipe).trim();
        }
        if (first.split("\\s+").length > 8) {
            return first.split("\\s+")[0];
        }
        return first.isBlank() ? null : first;
    }

    private static List<String> technologiesIn(String text, List<String> skillItems) {
        LinkedHashSet<String> found = new LinkedHashSet<>();
        String lower = text.toLowerCase(Locale.ROOT);
        for (String skill : skillItems) {
            if (skill != null && skill.length() >= 2 && containsAsWord(lower, skill.toLowerCase(Locale.ROOT))) {
                found.add(skill);
            }
        }
        Matcher matcher = Pattern.compile("\\b([A-Z][A-Za-z0-9+#.]{1,24})\\b").matcher(text);
        while (matcher.find()) {
            String token = matcher.group(1);
            if (!TECH_NOISE.contains(token.toLowerCase(Locale.ROOT)) && !SECTION_NAMES.contains(token.toLowerCase(Locale.ROOT))) {
                if (token.length() >= 3) {
                    found.add(token);
                }
            }
        }
        return new ArrayList<>(found);
    }

    private static List<String> extractMetrics(String text) {
        LinkedHashSet<String> snippets = new LinkedHashSet<>();
        Matcher matcher = QUANTIFIED.matcher(text);
        while (matcher.find()) {
            int start = text.lastIndexOf('.', matcher.start());
            start = start < 0 ? 0 : start + 1;
            int end = text.indexOf('.', matcher.end());
            end = end < 0 ? Math.min(text.length(), matcher.end() + 48) : end;
            String snippet = text.substring(start, end).replaceAll("\\s+", " ").trim();
            snippet = snippet.replaceAll("^[,:;\\-\\s]+", "").replaceAll("[,:;\\-\\s]+$", "");
            if (!snippet.isBlank()) {
                snippets.add(snippet);
            }
        }
        return new ArrayList<>(snippets);
    }

    private static List<String> extractEmployers(String text) {
        LinkedHashSet<String> employers = new LinkedHashSet<>();
        Matcher at = Pattern.compile("\\bat\\s+([A-Z][A-Za-z0-9&.\\-]*(?:\\s+[A-Z][A-Za-z0-9&.\\-]*){0,3})").matcher(text);
        while (at.find()) {
            String name = at.group(1).trim();
            if (!SECTION_NAMES.contains(name.toLowerCase(Locale.ROOT))) {
                employers.add(name);
            }
        }
        return new ArrayList<>(employers);
    }

    private static int countActionVerbs(String lower) {
        String[] verbs = {
                "built", "developed", "created", "designed", "led", "managed",
                "achieved", "delivered", "implemented", "architected", "launched",
                "deployed", "migrated", "optimised", "optimized", "automated",
                "integrated", "collaborated", "mentored", "refactored", "scaled"
        };
        int count = 0;
        for (String verb : verbs) {
            if (lower.contains(verb)) {
                count++;
            }
        }
        return count;
    }

    private static List<String> relatedNeedles(String label) {
        String lower = label.toLowerCase(Locale.ROOT);
        LinkedHashSet<String> needles = new LinkedHashSet<>();
        for (String token : lower.split("[^a-z0-9+#]+")) {
            if (token.length() >= 3 && !TECH_NOISE.contains(token)) {
                needles.add(token);
            }
        }
        // Safe related language for databases only — not used for matching/scoring.
        if (lower.contains("postgres") || lower.contains("mysql") || lower.contains("sqlite")
                || lower.contains("sql")) {
            needles.add("sql");
            needles.add("database");
        }
        if (lower.contains("spring")) {
            needles.add("spring");
            needles.add("backend");
        }
        // Keep Java / JavaScript and cloud providers distinct.
        needles.removeIf(token -> token.equals("java") && lower.contains("javascript"));
        needles.removeIf(token -> token.equals("script") && lower.contains("javascript"));
        if (isCloud(lower)) {
            needles.removeIf(token -> !lower.contains(token) && isCloud(token));
        }
        return new ArrayList<>(needles);
    }

    private static boolean isCloud(String value) {
        return value.contains("aws") || value.contains("azure") || value.contains("gcp")
                || value.contains("google cloud");
    }

    private static boolean containsAny(String text, List<String> needles) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (containsAsWord(lower, needle)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAsWord(String lowerHaystack, String lowerNeedle) {
        if (lowerNeedle == null || lowerNeedle.isBlank()) {
            return false;
        }
        // Distinctness: "java" must not match "javascript".
        Pattern pattern = Pattern.compile("(?<!\\p{Alnum})" + Pattern.quote(lowerNeedle) + "(?!\\p{Alnum})");
        return pattern.matcher(lowerHaystack).find();
    }

    private static boolean containsIgnoreCase(String text, String needle) {
        return text.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
}
