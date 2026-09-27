package com.example.resumerag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ResumeService {

    public String displayFilename(MultipartFile resume) {
        if (resume == null) {
            return null;
        }
        return Utf8Text.restoreIfMisdecoded(resume.getOriginalFilename());
    }

    public String extractText(MultipartFile resume) throws IOException {
        if (resume == null || resume.isEmpty()) throw new IllegalArgumentException("Resume file is empty.");
        String filename = displayFilename(resume);
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF resume files are supported.");
        }
        TikaDocumentReader reader = new TikaDocumentReader(new InputStreamResource(resume.getInputStream()));
        List<Document> documents;
        try {
            documents = reader.read();
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to read the PDF resume.");
        }
        if (documents == null || documents.isEmpty()) throw new IllegalArgumentException("Could not extract any text from the resume.");
        String text = documents.stream().map(Document::getText).filter(t -> t != null && !t.isBlank())
                .reduce((a,b) -> a + "\n\n" + b).orElse("").trim();
        text = Utf8Text.restoreIfMisdecoded(text);
        if (text.isBlank()) throw new IllegalArgumentException("Could not extract any text from the resume.");
        return text;
    }
}
