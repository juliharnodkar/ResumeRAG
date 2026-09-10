package com.example.resumerag.skill;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {
    @Test void javaDoesNotMatchJavaScript() { assertFalse(SkillRegistry.contains("JavaScript", "Java")); assertTrue(SkillRegistry.contains("Java", "Java")); }
    @Test void cDoesNotMatchCSharp() { assertFalse(SkillRegistry.contains("C#", "C")); assertTrue(SkillRegistry.contains("C", "C")); }
    @Test void aliasesWork() {
        assertTrue(SkillRegistry.contains("Postgres", "PostgreSQL"));
        assertTrue(SkillRegistry.contains("Spring-Boot", "Spring Boot"));
        assertTrue(SkillRegistry.contains("RESTful APIs", "REST API"));
        assertTrue(SkillRegistry.contains("NodeJS", "Node.js"));
        assertTrue(SkillRegistry.contains("NextJS", "Next.js"));
        assertTrue(SkillRegistry.contains("LLMs", "LLM"));
        assertTrue(SkillRegistry.contains("Amazon Web Services", "AWS"));
        assertTrue(SkillRegistry.contains("Amazon EC2", "AWS EC2"));
    }
}

