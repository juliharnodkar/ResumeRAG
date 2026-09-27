package com.example.resumerag.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SkillRegistryTest {

    @Test
    void javaDoesNotMatchJavaScript() {
        assertTrue(SkillRegistry.contains("Java", "Java"));
        assertFalse(SkillRegistry.contains("JavaScript", "Java"));
    }

    @Test
    void cDoesNotMatchCSharp() {
        assertTrue(SkillRegistry.contains("C programming", "C"));
        assertFalse(SkillRegistry.contains("C#", "C"));
    }

    @Test
    void cppIsDistinctFromC() {
        assertTrue(SkillRegistry.contains("C++", "C++"));
        assertTrue(SkillRegistry.contains("cpp", "C++"));
        assertFalse(SkillRegistry.contains("C++", "C"));
    }

    @Test
    void aliasesCanonicalizeCorrectly() {
        assertEquals("C++", SkillRegistry.canonicalize("cpp"));
        assertEquals("C++", SkillRegistry.canonicalize("C plus plus"));
        assertEquals("C#", SkillRegistry.canonicalize("csharp"));
        assertEquals("Next.js", SkillRegistry.canonicalize("nextjs"));
        assertEquals("PostgreSQL", SkillRegistry.canonicalize("postgres"));
        assertEquals("Spring Boot", SkillRegistry.canonicalize("springboot"));
        assertEquals("REST API", SkillRegistry.canonicalize("rest apis"));
        assertEquals("LLM", SkillRegistry.canonicalize("llms"));
    }

    @Test
    void unknownSkillReturnsNull() {
        assertNull(SkillRegistry.canonicalize("Terraform"));
        assertNull(SkillRegistry.canonicalize("Rust"));
    }
}
