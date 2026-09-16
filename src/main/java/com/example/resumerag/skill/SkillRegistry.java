package com.example.resumerag.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class SkillRegistry {

    private static final Map<String, List<String>> SKILLS = new LinkedHashMap<>();

    static {
        register("Java", "java");
        register("Python", "python");
        register("C++", "c++", "cpp", "c plus plus");
        register("C", "c");
        register("C#", "c#", "csharp", "c sharp");
        register("JavaScript", "javascript");
        register("TypeScript", "typescript");
        register("SQL", "sql");
        register("React", "react");
        register("Next.js", "next.js", "nextjs");
        register("Node.js", "node.js", "nodejs");
        register("Flask", "flask");
        register("Spring Boot", "spring boot", "springboot", "spring-boot");
        register("Docker", "docker");
        register("Kubernetes", "kubernetes");
        register("AWS EC2", "aws ec2", "amazon ec2");
        register("AWS", "aws", "amazon web services");
        register("Jenkins", "jenkins");
        register("GitHub", "github");
        register("Git", "git");
        register("MySQL", "mysql");
        register("PostgreSQL", "postgresql", "postgres");
        register("Supabase", "supabase");
        register("MongoDB", "mongodb");
        register("Machine Learning", "machine learning", "machine-learning");
        register("Deep Learning", "deep learning", "deep-learning");
        register("Generative AI", "generative ai", "generative artificial intelligence");
        register("LLM", "llm", "llms", "large language model", "large language models");
        register("REST API", "rest api", "rest apis", "restful api", "restful apis");
        register("Data Structures", "data structure", "data structures");
        register("Algorithms", "algorithm", "algorithms");
        register("Computer Networks", "computer network", "computer networks");
        register("Operating Systems", "operating system", "operating systems");
        register("DBMS", "dbms", "database management system", "database management systems");
    }

    private SkillRegistry() {}

    private static void register(String canonical, String... aliases) {
        SKILLS.put(canonical, List.of(aliases));
    }

    public static List<String> canonicalSkills() {
        return List.copyOf(SKILLS.keySet());
    }

    public static List<String> aliasesFor(String canonical) {
        List<String> aliases = SKILLS.get(canonical);
        return aliases == null ? List.of(canonical) : aliases;
    }

    public static boolean contains(String text, String canonical) {
        if (text == null || text.isBlank() || canonical == null || canonical.isBlank()) {
            return false;
        }

        if (!SKILLS.containsKey(canonical)) {
            return false;
        }

        String normalized = normalize(text);

        for (String alias : aliasesFor(canonical)) {
            String normalizedAlias = normalize(alias);

            if (patternForAlias(canonical, normalizedAlias).matcher(normalized).find()) {
                return true;
            }
        }

        return false;
    }

    public static String canonicalize(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        for (String canonical : SKILLS.keySet()) {
            if (contains(text, canonical)) {
                return canonical;
            }
        }

        return null;
    }

    public static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT)
                .replaceAll("[._-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static Pattern patternForCanonical(String canonical) {
        if (!SKILLS.containsKey(canonical)) {
            return Pattern.compile("(?!)");
        }

        return Pattern.compile(
                patternForAlias(canonical, normalize(canonical)).pattern()
        );
    }

    private static Pattern patternForAlias(String canonical, String alias) {
        if ("C".equals(canonical)) {
            return Pattern.compile("(?<![a-z0-9])c(?![a-z0-9#+])");
        }

        if ("C++".equals(canonical)) {
            if ("cpp".equals(alias) || "c plus plus".equals(alias)) {
                return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])");
            }

            return Pattern.compile("(?<![a-z0-9])c\\+\\+(?![a-z0-9])");
        }

        if ("C#".equals(canonical)) {
            if ("csharp".equals(alias) || "c sharp".equals(alias)) {
                return Pattern.compile("(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])");
            }

            return Pattern.compile("(?<![a-z0-9])c#(?![a-z0-9])");
        }

        return Pattern.compile(
                "(?<![a-z0-9])" + Pattern.quote(alias) + "(?![a-z0-9])"
        );
    }
}
