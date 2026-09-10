# ResumeRAG

ResumeRAG is a resume-vs-job-description matching application built with Java 21, Spring Boot, Spring AI, Ollama, ONNX embeddings, and PostgreSQL/pgvector.

## Pipeline

1. Upload a PDF resume.
2. Extract text with Apache Tika.
3. Split the resume into section-aware chunks.
4. Store embeddings in pgvector with a unique `resumeId` metadata field.
5. Extract canonical technical skills from the job description.
6. Match those skills deterministically against the uploaded resume.
7. Calculate score, matched skills, missing skills, and evidence deterministically.
8. Use the LLM only to write a human-readable explanation of the verified result. A consistency guard falls back to deterministic text if the LLM contradicts the verified data.

## Run

Requirements:
- Java 21
- Docker Desktop
- Ollama running with `phi3:mini`

Start the app:

```powershell
.\mvnw.cmd spring-boot:run
```

Open `http://localhost:8080`.

## Notes

- Only PDF uploads are accepted.
- Maximum upload size is 10MB.
- Match score is derived only from deterministic skill matching.
- LLM output is never allowed to change matched skills, missing skills, evidence, or score.
