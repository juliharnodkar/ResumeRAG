package com.example.resumerag.skill;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class AnalysisGuardTest {
    @Test void contradictionFallsBack() {
        String bad = "1. MATCH SUMMARY\nFlask and Java are strong.\n2. GAPS\nSpring Boot and Flask are missing.\n3. RECOMMENDATIONS\nLearn Spring Boot.";
        String out = AnalysisGuard.verifyOrFallback(bad, List.of("Java", "Flask"), List.of("Spring Boot"));
        assertTrue(out.contains("Verified matched skills: Java, Flask"));
        assertTrue(out.contains("Missing verified skills: Spring Boot"));
    }
    @Test void consistentAnalysisPasses() {
        String ok = "1. MATCH SUMMARY\nJava and Flask match the verified resume.\n2. GAPS\nSpring Boot is missing.\n3. RECOMMENDATIONS\nAdd Spring Boot only if genuinely experienced.";
        assertEquals(ok, AnalysisGuard.verifyOrFallback(ok, List.of("Java", "Flask"), List.of("Spring Boot")));
    }
    @Test void malformedFallsBack() {
        String out = AnalysisGuard.verifyOrFallback("hello", List.of("Java"), List.of("PostgreSQL"));
        assertTrue(out.startsWith("1. MATCH SUMMARY"));
    }
}
