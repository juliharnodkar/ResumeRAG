package com.example.resumerag;

import com.example.resumerag.model.MatchResult;
import com.example.resumerag.model.SkillMatch;
import com.example.resumerag.skill.AnalysisGuard;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MatchAnalysisService {

    private final SkillExtractorService skillExtractorService;
    private final SkillMatchingService skillMatchingService;
    private final ChatClient chatClient;

    public MatchAnalysisService(SkillExtractorService skillExtractorService,
                                SkillMatchingService skillMatchingService,
                                ChatClient.Builder builder) {
        this.skillExtractorService = skillExtractorService;
        this.skillMatchingService = skillMatchingService;
        this.chatClient = builder.defaultSystem("""
                You are an expert technical recruiter.
                Use only the supplied verified facts.
                Never invent technologies, projects, or experience.
                Never change the matched or missing skills.
                Write concise professional prose.
                """).build();
    }

    public MatchResult analyze(String jobDescription, String resumeId) {
        if (jobDescription == null || jobDescription.isBlank()) {
            throw new IllegalArgumentException("Job description cannot be empty.");
        }
        List<String> requiredSkills = skillExtractorService.extractSkills(jobDescription);
        if (requiredSkills.isEmpty()) {
            throw new IllegalArgumentException("No recognizable technical requirements found in the job description.");
        }
        List<SkillMatch> matches = skillMatchingService.matchSkills(requiredSkills, resumeId);
        int score = skillMatchingService.calculateScore(matches);
        List<String> matched = matches.stream().filter(SkillMatch::matched).map(SkillMatch::skill).toList();
        List<String> missing = matches.stream().filter(m -> !m.matched()).map(SkillMatch::skill).toList();

        String prompt = """
                Verified match data only.

                MATCH SCORE: %d%%
                MATCHED SKILLS: %s
                MISSING SKILLS: %s

                Write exactly three sections:
                1. MATCH SUMMARY - discuss only MATCHED SKILLS.
                2. GAPS - mention only MISSING SKILLS.
                3. RECOMMENDATIONS - practical improvements only for MISSING SKILLS.
                Do not mention any other technology.
                """.formatted(score, String.join(", ", matched), String.join(", ", missing));

        String generated;
        try {
            generated = chatClient.prompt().user(prompt).call().content();
        } catch (Exception ex) {
            generated = null;
        }
        String analysis = AnalysisGuard.verifyOrFallback(generated, matched, missing);
        List<String> recommendations = missing.stream()
                .map(skill -> "If you genuinely have experience with " + skill + ", make it more visible in the resume.")
                .toList();
        return new MatchResult(score, matched, missing, analysis, recommendations, matches);
    }
}
