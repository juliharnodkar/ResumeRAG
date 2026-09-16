package com.example.resumerag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConceptQueryExpander {

    private static final Map<String, List<String>> EXPANSION_RULES = new HashMap<>();

    static {
        // Acronyms
        addRule("AWS", "Amazon Web Services");
        addRule("GCP", "Google Cloud Platform");
        addRule("LLM", "Large Language Model");
        addRule("ML", "Machine Learning");
        addRule("AI", "Artificial Intelligence");
        addRule("CI/CD", "Continuous Integration", "Continuous Deployment");
        
        // Multi-word variants
        addRule("Spring Boot", "springboot", "spring-boot");
        addRule("Node.js", "nodejs", "node js");
        addRule("Next.js", "nextjs", "next js");
        addRule("REST API", "restful api", "rest apis");
        addRule("OOP", "object oriented programming", "object-oriented programming");
        addRule("DBMS", "database", "SQL", "Database Management System");
        addRule("SQL", "database", "relational database", "Structured Query Language");
        addRule("Git", "version control", "github");
        addRule("HTTP", "http protocol", "REST");
        addRule("REST API", "restful api", "rest apis", "REST APIs");
        addRule("REST APIs", "REST API", "restful api");
        addRule("Database Management System", "DBMS", "SQL", "database");
        addRule("Structured Query Language", "SQL", "database");
        addRule("Machine Learning", "machine-learning");
        addRule("Deep Learning", "deep-learning");
        addRule("Generative AI", "generative artificial intelligence");
        addRule("debugging", "debug", "troubleshoot");
        addRule("problem-solving", "problem solving");
        addRule("communication", "communicate");
        addRule("teamwork", "collaborate", "team members");
        addRule("web technologies", "web development", "frontend");
        addRule("software development lifecycle", "SDLC", "software lifecycle");
    }

    private static final int MAX_EXPANSION_QUERIES = 3;

    private ConceptQueryExpander() {
        // Prevent instantiation
    }

    private static void addRule(String original, String... variants) {
        List<String> list = new ArrayList<>();
        for (String variant : variants) {
            list.add(variant);
        }
        EXPANSION_RULES.put(original.toLowerCase(Locale.ROOT), Collections.unmodifiableList(list));
    }

    public static List<String> expand(String concept) {
        if (concept == null || concept.isBlank()) {
            return List.of();
        }
        
        String trimmed = concept.trim();
        String key = trimmed.toLowerCase(Locale.ROOT);
        
        List<String> result = new ArrayList<>();
        result.add(trimmed); // Always first
        
        List<String> variants = EXPANSION_RULES.get(key);
        if (variants != null) {
            for (String variant : variants) {
                if (result.size() >= MAX_EXPANSION_QUERIES) {
                    break;
                }
                if (!variant.equalsIgnoreCase(trimmed)) {
                    result.add(variant);
                }
            }
        }
        
        return Collections.unmodifiableList(result);
    }
}
