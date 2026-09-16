package com.example.resumerag.analysis;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicJdRequirementExtractorTest {

    private static final String CURRENT_JD = """
            Qualifications:
            Bachelor's degree in Computer Science, Computer Engineering, IT, or related field.
            Internship or academic project experience in software development.

            Technical Skills:
            - Object-Oriented Programming (OOP)
            - DBMS
            - SQL
            - REST APIs
            - HTTP
            - web technologies
            - Git/version control
            - software development lifecycle

            Responsibilities:
            Debug applications and solve problems.
            Collaborate with team members.
            Strong communication skills.
            """;

    @Test
    void currentTestJdExtractsMeaningfulRequirements() {
        List<JobRequirement> reqs = DeterministicJdRequirementExtractor.extract(CURRENT_JD);

        assertFalse(reqs.isEmpty());
        assertTrue(reqs.size() >= 8, "expected multiple requirements, got " + reqs.size());
        assertTrue(contains(reqs, "OOP"));
        assertTrue(contains(reqs, "SQL") || contains(reqs, "Database") || contains(reqs, "DBMS"));
        assertTrue(contains(reqs, "REST") || contains(reqs, "HTTP"));
        assertTrue(contains(reqs, "Git") || contains(reqs, "Version"));
        assertTrue(contains(reqs, "Bachelor") || contains(reqs, "degree"));
        assertTrue(contains(reqs, "Team") || contains(reqs, "Communication") || contains(reqs, "Debug"));
    }

    @Test
    void bulletAndSectionJdWorks() {
        List<JobRequirement> reqs = DeterministicJdRequirementExtractor.extract("""
                Requirements:
                - Java
                - Spring Boot
                - PostgreSQL
                """);
        assertTrue(contains(reqs, "Java"));
        assertTrue(contains(reqs, "Spring"));
        assertTrue(contains(reqs, "PostgreSQL") || contains(reqs, "SQL"));
    }

    @Test
    void behavioralRequirementsExtracted() {
        List<JobRequirement> reqs = DeterministicJdRequirementExtractor.extract("""
                Must demonstrate strong communication and teamwork.
                Excellent problem-solving and debugging skills.
                """);
        assertTrue(contains(reqs, "Communication"));
        assertTrue(contains(reqs, "Team"));
        assertTrue(contains(reqs, "Problem") || contains(reqs, "Debug"));
    }

    @Test
    void emptyJdReturnsEmpty() {
        assertEquals(List.of(), DeterministicJdRequirementExtractor.extract(""));
        assertEquals(List.of(), DeterministicJdRequirementExtractor.extract(null));
    }

    @Test
    void skipsFluffWithoutInventingSkills() {
        List<JobRequirement> reqs = DeterministicJdRequirementExtractor.extract("""
                We are looking for a great teammate.
                Join our team.
                Good company culture.
                Responsibilities include many things.
                """);
        assertTrue(reqs.isEmpty() || reqs.stream().noneMatch(r ->
                r.originalText().toLowerCase().contains("looking")));
    }

    private static boolean contains(List<JobRequirement> reqs, String needle) {
        String n = needle.toLowerCase();
        return reqs.stream().anyMatch(r ->
                r.originalText().toLowerCase().contains(n)
                        || String.valueOf(r.expression()).toLowerCase().contains(n));
    }
}
