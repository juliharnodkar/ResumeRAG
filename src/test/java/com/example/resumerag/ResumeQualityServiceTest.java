package com.example.resumerag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic tests for {@link ResumeQualityService}.
 *
 * <p>All tests operate on plain text chunks — no external dependencies.
 * Quality signals tested: structure, content (action verbs, quantification),
 * and skill presentation. Edge cases: empty, very short, very long resumes.
 */
class ResumeQualityServiceTest {

    private final ResumeQualityService service = new ResumeQualityService(null);

    // -------------------------------------------------------------------------
    // Score bounds
    // -------------------------------------------------------------------------

    @Test
    void scoreIsAlwaysBetweenZeroAndOne_emptyInput() {
        ResumeQualityAssessment result = service.assessQuality(List.of());
        assertBounded(result.overallQualityScore());
    }

    @Test
    void scoreIsAlwaysBetweenZeroAndOne_nullInput() {
        ResumeQualityAssessment result = service.assessQuality(null);
        assertBounded(result.overallQualityScore());
    }

    @Test
    void scoreIsAlwaysBetweenZeroAndOne_strongResume() {
        ResumeQualityAssessment result = service.assessQuality(strongResumeChunks());
        assertBounded(result.overallQualityScore());
    }

    @Test
    void scoreIsAlwaysBetweenZeroAndOne_weakResume() {
        ResumeQualityAssessment result = service.assessQuality(weakResumeChunks());
        assertBounded(result.overallQualityScore());
    }

    private void assertBounded(double score) {
        assertTrue(score >= 0.0, "Score must be >= 0.0, was: " + score);
        assertTrue(score <= 1.0, "Score must be <= 1.0, was: " + score);
    }

    // -------------------------------------------------------------------------
    // Strong resume outscores weak resume
    // -------------------------------------------------------------------------

    @Test
    void strongResumeOutscoresWeakResume() {
        double strong = service.assessQuality(strongResumeChunks()).overallQualityScore();
        double weak   = service.assessQuality(weakResumeChunks()).overallQualityScore();
        assertTrue(strong > weak,
                "Strong resume (%.2f) must outScore weak resume (%.2f)".formatted(strong, weak));
    }

    // -------------------------------------------------------------------------
    // Genuinely quantified accomplishments
    // -------------------------------------------------------------------------

    @Test
    void percentageSignalCountsAsQuantified() {
        assertEquals(1, service.countGenuinelyQuantified("Reduced deployment time by 40%."));
    }

    @Test
    void dollarSignalCountsAsQuantified() {
        assertEquals(1, service.countGenuinelyQuantified("Saved $50,000 in infrastructure costs."));
    }

    @Test
    void multiplierSignalCountsAsQuantified() {
        assertEquals(1, service.countGenuinelyQuantified("Improved throughput by 3x."));
    }

    @Test
    void magnitudeWordCountsAsQuantified() {
        assertEquals(1, service.countGenuinelyQuantified("Served 2 million users monthly."));
    }

    @Test
    void outcomeWordsWithoutNumbersAreNOTCountedAsQuantified() {
        // "improved", "increased", "reduced" without a number must not count.
        int count = service.countGenuinelyQuantified(
                "Improved team velocity. Increased customer satisfaction. Reduced churn.");
        assertEquals(0, count,
                "Vague outcome words without numbers must NOT be counted as quantified impact.");
    }

    @Test
    void multipleQuantificationsAreAllCounted() {
        int count = service.countGenuinelyQuantified(
                "Cut latency by 30%. Processed $1M in transactions. Scaled to 5x load.");
        assertEquals(3, count);
    }

    @Test
    void quantifiedResumeScoresHigherOnContent() {
        List<Document> quantified = chunks(
                "Experience: Built REST APIs using Java. Reduced response time by 40%. " +
                "Served 2 million requests per day. Increased uptime to 99.9%.",
                "Skills: Java, Spring Boot, PostgreSQL",
                "Education: B.Sc. Computer Science"
        );
        List<Document> unquantified = chunks(
                "Experience: Built REST APIs using Java. Improved system performance. " +
                "Delivered features on time. Increased team efficiency.",
                "Skills: Java, Spring Boot, PostgreSQL",
                "Education: B.Sc. Computer Science"
        );
        double quantifiedScore   = service.assessQuality(quantified).overallQualityScore();
        double unquantifiedScore = service.assessQuality(unquantified).overallQualityScore();
        assertTrue(quantifiedScore > unquantifiedScore,
                "Quantified resume (%.2f) must outScore unquantified resume (%.2f)"
                        .formatted(quantifiedScore, unquantifiedScore));
    }

    // -------------------------------------------------------------------------
    // Skill section presence
    // -------------------------------------------------------------------------

    @Test
    void resumeWithSkillsSectionScoresHigherOnSkillDimension() {
        List<Document> withSkills = chunks(
                "Experience: Built backend services using Python.",
                "Skills: Python, Django, PostgreSQL, Docker",
                "Education: B.Sc. Computer Science"
        );
        List<Document> withoutSkills = chunks(
                "Experience: Built backend services using Python.",
                "Education: B.Sc. Computer Science"
        );
        double withScore    = service.assessQuality(withSkills).skillPresentationScore();
        double withoutScore = service.assessQuality(withoutSkills).skillPresentationScore();
        assertTrue(withScore > withoutScore,
                "Resume with explicit Skills section (%.2f) must score higher than one without (%.2f)"
                        .formatted(withScore, withoutScore));
    }

    // -------------------------------------------------------------------------
    // Very short resume
    // -------------------------------------------------------------------------

    @Test
    void veryShortResumeReceivesLowScore() {
        // Under 50 words — should signal incomplete/corrupted resume
        List<Document> tooShort = chunks("John Doe. Software Engineer.");
        ResumeQualityAssessment result = service.assessQuality(tooShort);
        assertTrue(result.overallQualityScore() < 0.70,
                "Very short resume should not score above 0.70, was: " + result.overallQualityScore());
    }

    // -------------------------------------------------------------------------
    // Action verbs orientation
    // -------------------------------------------------------------------------

    @Test
    void resumeWithActionVerbsScoresHigherOnContent() {
        List<Document> actionOriented = chunks(
                "Built and deployed microservices using Java and Spring Boot. " +
                "Led a team of 4 engineers. Delivered the product 2 weeks ahead of schedule. " +
                "Designed the database schema. Implemented CI/CD pipelines.",
                "Skills: Java, Spring Boot, Docker, Kubernetes",
                "Education: B.Sc. Computer Science"
        );
        List<Document> passive = chunks(
                "Responsible for microservices. Participated in team meetings. " +
                "Was involved in the product launch. Assisted with database work.",
                "Skills: Java, Spring Boot, Docker",
                "Education: B.Sc. Computer Science"
        );
        double actionScore  = service.assessQuality(actionOriented).contentQualityScore();
        double passiveScore = service.assessQuality(passive).contentQualityScore();
        assertTrue(actionScore > passiveScore,
                "Action-oriented resume (%.2f) must outScore passive resume (%.2f)"
                        .formatted(actionScore, passiveScore));
    }

    // -------------------------------------------------------------------------
    // Structure signals
    // -------------------------------------------------------------------------

    @Test
    void resumeWithMultipleSectionsScoresHigherOnStructure() {
        List<Document> structured = chunks(
                "Experience: Software Engineer at Acme Corp. Built Java APIs.",
                "Skills: Java, Python, Docker",
                "Education: B.Sc. Computer Science. 2021.",
                "Projects: Recommendation engine using ML.",
                "Certifications: AWS Certified Developer"
        );
        List<Document> unstructured = chunks(
                "Worked on backend, databases, and some frontend. Know Java and Python."
        );
        double structuredScore   = service.assessQuality(structured).structureScore();
        double unstructuredScore = service.assessQuality(unstructured).structureScore();
        assertTrue(structuredScore > unstructuredScore,
                "Structured resume (%.2f) must outScore unstructured resume (%.2f)"
                        .formatted(structuredScore, unstructuredScore));
    }

    // -------------------------------------------------------------------------
    // Strengths and improvements are non-empty lists
    // -------------------------------------------------------------------------

    @Test
    void strongResumeProducesAtLeastOneStrength() {
        ResumeQualityAssessment result = service.assessQuality(strongResumeChunks());
        assertFalse(result.strengths().isEmpty(),
                "A strong resume must produce at least one identified strength.");
    }

    @Test
    void weakResumeProducesAtLeastOneImprovement() {
        ResumeQualityAssessment result = service.assessQuality(weakResumeChunks());
        assertFalse(result.improvements().isEmpty(),
                "A weak resume must produce at least one improvement suggestion.");
    }

    @Test
    void strengthsCiteActualProjectAndTechnologies() {
        ResumeQualityAssessment result = service.assessQuality(jesResumeChunks());
        String joined = String.join(" ", result.strengths()).toLowerCase();
        assertTrue(joined.contains("jes"), "Strengths must mention the JES project: " + result.strengths());
        assertTrue(
                joined.contains("flask") || joined.contains("javascript") || joined.contains("tesseract"),
                "Strengths must mention technologies from the resume: " + result.strengths()
        );
        assertFalse(joined.contains("well-organised structure with clear sections"));
        assertFalse(joined.contains("spring boot"), "Must not invent Spring Boot");
        assertFalse(joined.contains("google"), "Must not invent employers");
    }

    @Test
    void strengthsCiteActualMetricsWhenPresent() {
        ResumeQualityAssessment result = service.assessQuality(jesResumeChunks());
        String joined = String.join(" ", result.strengths());
        assertTrue(joined.contains("40%") || joined.contains("200"),
                "Strengths must cite an actual metric from the resume: " + result.strengths());
        assertFalse(joined.contains("99.9%"), "Must not invent metrics");
    }

    @Test
    void strengthsUseFieldSpecificExperienceEvidenceInsteadOfGenericTemplates() {
        List<Document> hospitality = chunks(
                "Experience: Hospitality professional with 21+ years in hotel and F&B operations. "
                        + "Managed guest requests and complaints, communicated with guests, and trained team members.",
                "Skills: Customer relations, communication, teamwork"
        );

        String joined = String.join(" ", service.assessQuality(hospitality).strengths()).toLowerCase();
        assertTrue(joined.contains("hotel") || joined.contains("f&b"), joined);
        assertTrue(joined.contains("guest") || joined.contains("trained"), joined);
        assertFalse(joined.contains("hands-on work with java"), joined);
    }

    @Test
    void qualityRecognisesGeneralProfessionalActionLanguageAcrossFields() {
        List<Document> operationalResume = chunks(
                "Experience: Managed requests, coordinated daily operations, handled complaints, "
                        + "communicated with stakeholders, and trained team members.",
                "Core Competencies: Service delivery, communication, teamwork"
        );

        assertTrue(service.assessQuality(operationalResume).contentQualityScore() >= 0.60,
                "Field-neutral professional action verbs should receive accomplishment credit.");
        assertTrue(service.assessQuality(operationalResume).skillPresentationScore() >= 0.80,
                "Core Competencies should be treated as a visible capabilities section.");
    }

    @Test
    void strengthsAndImprovementsAlwaysHaveShortTitlesAndSeparateBodies() {
        ResumeQualityAssessment result = service.assessQuality(vagueProjectChunks());
        for (String card : java.util.stream.Stream.concat(result.strengths().stream(), result.improvements().stream()).toList()) {
            int separator = card.indexOf(": ");
            assertTrue(separator > 0 && separator < 50, card);
            assertTrue(card.length() > separator + 2, card);
            assertFalse(card.substring(separator + 2).equalsIgnoreCase(card.substring(0, separator)), card);
        }
    }

    @Test
    void weaknessesAreResumeSpecificAndDoNotMentionMissingJdSkills() {
        ResumeQualityAssessment result = service.assessQuality(vagueProjectChunks());
        assertFalse(result.improvements().isEmpty());
        String joined = String.join(" ", result.improvements()).toLowerCase();
        assertTrue(joined.contains("jes") || joined.contains("project") || joined.contains("impact"),
                "Weaknesses must refer to this resume: " + result.improvements());
        assertFalse(joined.contains("postgresql"),
                "Missing JD skills must not become resume weaknesses: " + result.improvements());
        assertFalse(joined.contains("kubernetes"));
        assertTrue(result.improvements().size() <= 5);
    }

    @Test
    void doesNotInventProjectsTechnologiesOrExperience() {
        ResumeQualityAssessment result = service.assessQuality(jesResumeChunks());
        String all = (String.join(" ", result.strengths()) + " " + String.join(" ", result.improvements())).toLowerCase();
        assertFalse(all.contains("netflix"));
        assertFalse(all.contains("kubernetes"));
        assertFalse(all.contains("harvard"));
        assertFalse(all.contains("10 million"));
    }

    @Test
    void strongCompleteResumeCanReportNoMajorWeaknesses() {
        ResumeQualityAssessment result = service.assessQuality(strongResumeChunks());
        assertFalse(result.improvements().isEmpty());
        assertTrue(result.improvements().size() <= 5);
    }

    // -------------------------------------------------------------------------
    // Line count no longer used for clutter (regression guard)
    // -------------------------------------------------------------------------

    @Test
    void cloneChunksJoinedWithSpacesAreNotPenalisedByLineCounting() {
        // When chunks are joined with spaces, the result is a single line.
        // The old hasExcessiveClutter used `lines < 10` which would always fire.
        // This test asserts a multi-chunk resume with enough words scores above the
        // minimum baseline and is NOT penalised for being "one line".
        List<Document> multiChunk = chunks(
                "Experience: Built REST APIs. Led feature delivery.",
                "Skills: Java, Spring Boot, PostgreSQL, Docker.",
                "Education: B.Sc. Computer Science. National University. 2022.",
                "Projects: Built a real-time recommendation engine for e-commerce."
        );
        ResumeQualityAssessment result = service.assessQuality(multiChunk);
        assertTrue(result.structureScore() >= 0.55,
                "Multi-chunk resume joined with spaces must not be penalised by line-count check. " +
                "Structure score: " + result.structureScore());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private List<Document> strongResumeChunks() {
        return chunks(
                "John Doe | john@example.com | linkedin.com/in/johndoe | github.com/johndoe",
                "Experience: Software Engineer at Acme Corp (2021–2024). " +
                "Built and deployed REST APIs using Java and Spring Boot, reducing latency by 35%. " +
                "Led migration of monolith to microservices architecture, cutting release time by 50%. " +
                "Mentored 3 junior engineers. Delivered $2M worth of features on schedule.",
                "Skills: Java, Spring Boot, Python, PostgreSQL, Docker, Kubernetes, AWS, CI/CD",
                "Education: B.Sc. Computer Science, State University, 2021.",
                "Projects: Real-time recommendation engine serving 1 million users daily. " +
                "Designed ML pipeline using Python and scikit-learn. Reduced model training time by 3x.",
                "Certifications: AWS Certified Solutions Architect"
        );
    }

    private List<Document> weakResumeChunks() {
        return chunks(
                "Helped with some coding tasks. Know Java. Did some projects."
        );
    }

    private List<Document> jesResumeChunks() {
        return List.of(
                new Document(
                        "Projects: JES | Flask, JavaScript, Tesseract. Built an OCR workflow in Flask and JavaScript using Tesseract. Reduced review time by 40% and served 200 users.",
                        Map.of("section", "Projects", "project", "JES")
                ),
                new Document(
                        "Skills: Flask, JavaScript, Tesseract, Python",
                        Map.of("section", "Skills")
                ),
                new Document(
                        "Education: B.Sc. Computer Science, State University, 2021.",
                        Map.of("section", "Education")
                )
        );
    }

    private List<Document> vagueProjectChunks() {
        return List.of(
                new Document(
                        "Projects: JES. Worked on the application.",
                        Map.of("section", "Projects", "project", "JES")
                ),
                new Document(
                        "Experience: Responsible for coding tasks. Helped with some features.",
                        Map.of("section", "Experience")
                )
        );
    }

    private List<Document> chunks(String... texts) {
        List<Document> docs = new java.util.ArrayList<>();
        for (String text : texts) {
            docs.add(new Document(text, Map.of("section", "Test")));
        }
        return Collections.unmodifiableList(docs);
    }
}
