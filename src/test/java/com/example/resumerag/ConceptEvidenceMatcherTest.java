package com.example.resumerag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConceptEvidenceMatcherTest {

    @Test
    void slashListMatchesAndConjunction() {
        assertTrue(ConceptEvidenceMatcher.matches(
                "bookings and check-ins",
                "Managed bookings/check-ins for arriving guests."));
        assertTrue(ConceptEvidenceMatcher.matches(
                "billing and payment disputes",
                "Resolved billing/payment disputes."));
    }

    @Test
    void bachelorOfScienceMatchesBachelorsDegree() {
        assertTrue(ConceptEvidenceMatcher.matches(
                "Bachelor's degree",
                "Bachelor of Science in Hospitality Management"));
        assertTrue(ConceptEvidenceMatcher.matches(
                "Bachelor's Degree",
                "B.Sc. in Computer Science"));
        assertFalse(ConceptEvidenceMatcher.matches(
                "Bachelor's degree",
                "Hospitality Management certificate"));
        assertFalse(ConceptEvidenceMatcher.matches(
                "Bachelor's degree",
                "Completed a degree workshop"));
    }

    @Test
    void verbFormsMatchDeverbalNouns() {
        assertTrue(ConceptEvidenceMatcher.matches(
                "staff supervision and training",
                "supervised/trained hotel staff"));
        assertTrue(ConceptEvidenceMatcher.matches(
                "staff supervision and training",
                "hotel staff supervision/training"));
    }

    @Test
    void conflictResolutionRequiresHandlingEvidence() {
        assertTrue(ConceptEvidenceMatcher.matches(
                "conflict resolution",
                "Handled guest conflicts at the front desk."));
        assertTrue(ConceptEvidenceMatcher.matches(
                "conflict resolution",
                "Resolved guest conflicts during check-in."));
        assertFalse(ConceptEvidenceMatcher.matches(
                "conflict resolution",
                "Observed guest conflicts in the lobby."));
        assertFalse(ConceptEvidenceMatcher.matches(
                "conflict resolution",
                "guest conflicts"));
    }

    @Test
    void javaRemainsDistinctFromJavaScript() {
        assertTrue(ConceptEvidenceMatcher.matches("Java", "Developed applications using Java."));
        assertFalse(ConceptEvidenceMatcher.matches("Java", "Experience with JavaScript."));
        assertTrue(ConceptEvidenceMatcher.matches("JavaScript", "Experience with JavaScript."));
        assertFalse(ConceptEvidenceMatcher.matches("JavaScript", "Experience with Java."));
    }

    @Test
    void awsAzureAndGcpRemainDistinct() {
        assertTrue(ConceptEvidenceMatcher.matches("AWS", "Deployed on AWS."));
        assertFalse(ConceptEvidenceMatcher.matches("AWS", "Deployed on Azure."));
        assertFalse(ConceptEvidenceMatcher.matches("AWS", "Deployed on GCP."));
        assertFalse(ConceptEvidenceMatcher.matches("Azure", "Deployed on AWS."));
        assertFalse(ConceptEvidenceMatcher.matches("GCP", "Deployed on AWS."));
    }

    @Test
    void unrelatedTokensDoNotMatch() {
        assertFalse(ConceptEvidenceMatcher.matches(
                "customer service",
                "Assistant Hotel Manager with bookings/check-ins"));
        assertFalse(ConceptEvidenceMatcher.matches("Rust", "Developed applications using Java."));
    }

    @Test
    void agentNounsStemToMatchDeverbialForms() {
        // "communicator" should match concept "communication" via agent-noun stem
        assertTrue(ConceptEvidenceMatcher.matches(
                "communication",
                "Solid written and verbal communicator in fast-paced environments."));
        // "supervisor" should match concept "supervision"
        assertTrue(ConceptEvidenceMatcher.matches(
                "supervision",
                "Responsible for supervisor duties at the front desk."));
        // "supervisor" in text should also match "supervision" concept
        assertTrue(ConceptEvidenceMatcher.matches(
                "staff supervision",
                "Assistant Hotel Manager: supervised hotel staff, trained new team members."));
    }

    @Test
    void javaAndJavaScriptRemainDistinctAfterAgentStemFix() {
        // Regression: agent-noun fix must not collapse Java vs JavaScript
        assertTrue(ConceptEvidenceMatcher.matches("Java", "Built services in Java."));
        assertFalse(ConceptEvidenceMatcher.matches("Java", "Built services in JavaScript."));
        assertTrue(ConceptEvidenceMatcher.matches("JavaScript", "Built services in JavaScript."));
        assertFalse(ConceptEvidenceMatcher.matches("JavaScript", "Built services in Java."));
    }
}
