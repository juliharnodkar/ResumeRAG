package com.example.resumerag;

import com.example.resumerag.model.MatchResult;
import com.example.resumerag.model.TailoredMatchResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class ResumeController {

    private final ResumeService resumeService;
    private final ResumeIngestionService resumeIngestionService;
    private final MatchAnalysisService matchAnalysisService;

    public ResumeController(ResumeService resumeService,
                            ResumeIngestionService resumeIngestionService,
                            MatchAnalysisService matchAnalysisService) {
        this.resumeService = resumeService;
        this.resumeIngestionService = resumeIngestionService;
        this.matchAnalysisService = matchAnalysisService;
    }

    @PostMapping("/resume/upload")
    public ResponseEntity<Map<String, String>> uploadResume(@RequestParam("resume") MultipartFile resume) throws Exception {
        String filename = resumeService.displayFilename(resume);
        String resumeText = resumeService.extractText(resume);
        String resumeId = resumeIngestionService.ingestResume(resumeText);
        System.out.println("UPLOAD: generated resumeId = " + resumeId);
        return ResponseEntity.ok(Map.of(
                "resumeId", resumeId,
                "message", "Resume uploaded successfully.",
                "filename", filename == null ? "" : filename
        ));
    }

    @PostMapping("/analyze")
    public ResponseEntity<MatchResult> analyze(@RequestParam("resumeId") String resumeId,
                                                @RequestParam("jobDescription") String jobDescription) {
        try { UUID.fromString(resumeId); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid resume identifier."); }
        System.out.println("ANALYZE CONTROLLER: resumeId = " + resumeId);
        return ResponseEntity.ok(matchAnalysisService.analyze(resumeId, jobDescription));
    }

    @PostMapping("/analyze-tailored")
    public ResponseEntity<TailoredMatchResult> analyzeTailored(@RequestParam("resumeId") String resumeId,
                                                               @RequestParam("jobDescription") String jobDescription) {
        try { UUID.fromString(resumeId); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Invalid resume identifier."); }
        return ResponseEntity.ok(matchAnalysisService.analyzeTailored(resumeId, jobDescription));
    }
}
