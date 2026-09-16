package com.example.resumerag.skill;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExperienceDurationParserTest {

    @Test
    void parsesYears() {
        assertEquals(24, ExperienceDurationParser.parseMonths("2 years of experience"));
    }

    @Test
    void parsesPlusYears() {
        assertEquals(24, ExperienceDurationParser.parseMonths("2+ years of experience"));
    }

    @Test
    void parsesMonths() {
        assertEquals(18, ExperienceDurationParser.parseMonths("18 months of experience"));
    }

    @Test
    void returnsNullWhenDurationIsUnknown() {
        assertNull(ExperienceDurationParser.parseMonths("experience with Java"));
    }

    @Test
    void parsesYearRange() {
        assertEquals(24, ExperienceDurationParser.parseMonths("Java Developer 2023 - 2025"));
    }

    @Test
    void parsesYearRangeToPresent() {
        assertTrue(ExperienceDurationParser.parseMonths("Java Developer 2023 - Present") >= 24);
    }

    @Test
    void parsesMonthYearRange() {
        assertEquals(24, ExperienceDurationParser.parseMonths("Java Developer Jan 2023 - Dec 2024"));
    }
}