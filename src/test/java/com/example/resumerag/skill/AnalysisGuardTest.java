package com.example.resumerag.skill;

import com.example.resumerag.model.RequirementImportance;
import com.example.resumerag.model.RequirementMatch;
import com.example.resumerag.model.RequirementStatus;
import com.example.resumerag.model.RequirementType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnalysisGuardTest {

    private static RequirementMatch requirement(
            String text,
            RequirementStatus status) {

        return new RequirementMatch(
                text,
                RequirementType.SKILL,
                RequirementImportance.HIGH,
                status,
                null,
                List.of(),
                List.of(),
                null,
                false
        );
    }

    @Test
    void contradictionFallsBack() {
        List<RequirementMatch> requirements = List.of(
                requirement("Java", RequirementStatus.MATCHED),
                requirement("Flask", RequirementStatus.MATCHED),
                requirement("Spring Boot", RequirementStatus.NOT_EVIDENCED)
        );

        String bad =
                "SUMMARY\n" +
                "Spring Boot is a strong match.\n\n" +
                "STRENGTHS\n" +
                "- Java\n" +
                "- Flask\n\n" +
                "GAPS\n" +
                "- Spring Boot";

        String out = AnalysisGuard.verifyOrFallback(bad, requirements);

        assertTrue(out.contains("Java"));
        assertTrue(out.contains("Flask"));
        assertTrue(out.contains("Spring Boot"));
        assertTrue(out.contains("NOT_EVIDENCED"));
    }

    @Test
    void consistentAnalysisPasses() {
        List<RequirementMatch> requirements = List.of(
                requirement("Java", RequirementStatus.MATCHED),
                requirement("Flask", RequirementStatus.MATCHED),
                requirement("Spring Boot", RequirementStatus.NOT_EVIDENCED)
        );

        String ok =
                "SUMMARY\n" +
                "Java and Flask match the verified resume.\n\n" +
                "STRENGTHS\n" +
                "- Java\n" +
                "- Flask\n\n" +
                "GAPS\n" +
                "- Spring Boot is missing.";

        assertEquals(
                ok,
                AnalysisGuard.verifyOrFallback(ok, requirements)
        );
    }

    @Test
    void malformedFallsBack() {
        List<RequirementMatch> requirements = List.of(
                requirement("Java", RequirementStatus.MATCHED),
                requirement("PostgreSQL", RequirementStatus.NOT_EVIDENCED)
        );

        String out = AnalysisGuard.verifyOrFallback(
                "hello",
                requirements
        );

        assertTrue(out.startsWith("SUMMARY"));
        assertTrue(out.contains("Java"));
        assertTrue(out.contains("PostgreSQL"));
    }
}