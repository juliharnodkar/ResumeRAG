package com.example.resumerag;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeServiceEncodingTest {

    @Test
    void displayFilenameRestoresMisdecodedUnicode() {
        String original = "José – GRAPHIC DESIGNER.pdf";
        String misdecoded = new String(original.getBytes(StandardCharsets.UTF_8), java.nio.charset.Charset.forName("windows-1252"));
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                misdecoded,
                "application/pdf",
                new byte[]{1, 2, 3}
        );
        assertEquals(original, new ResumeService().displayFilename(file));
    }

    @Test
    void frontendScriptDisplaysServerOrBrowserFilename() throws Exception {
        String html = Files.readString(Path.of("src/main/resources/static/index.html"), StandardCharsets.UTF_8);
        assertTrue(html.contains("JSON.parse(raw)"));
        assertTrue(html.contains("<meta charset=\"UTF-8\">"));
        assertTrue(html.contains("data.filename || file.name"));
        assertFalse(html.contains("ÃƒÆ’"));
    }

    @Test
    void extractTextKeepsLatinUnicodeFromPdf() throws Exception {
        byte[] pdf = latinUnicodePdf("Jose cafe: Jose's resume - Espana. e u n quotes.");
        MockMultipartFile file = new MockMultipartFile(
                "resume",
                "José.pdf",
                "application/pdf",
                pdf
        );
        String text = new ResumeService().extractText(file);
        assertFalse(text.isBlank());
        assertFalse(Utf8Text.looksLikeMojibake(text), text);
        assertTrue(text.toLowerCase().contains("jose") || text.toLowerCase().contains("cafe"), text);
    }

    private static byte[] latinUnicodePdf(String line) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(font, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText(line);
                stream.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }
}
