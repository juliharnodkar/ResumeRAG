package com.example.resumerag;

import com.example.resumerag.skill.SkillRegistry;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SkillExtractorService {

    public List<String> extractSkills(String jobDescription) {
        if (jobDescription == null || jobDescription.isBlank()) return List.of();
        List<String> found = new ArrayList<>();
        for (String skill : SkillRegistry.canonicalSkills()) {
            if (SkillRegistry.contains(jobDescription, skill)) found.add(skill);
        }
        return found;
    }
}
