package com.example.resumerag;

import org.junit.jupiter.api.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Utf8TextTest {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    @Test
    void restoresWindows1252MisdecodedUtf8() {
        String original = "café – ‘quotes’ résumé José España";
        String misdecoded = new String(original.getBytes(StandardCharsets.UTF_8), WINDOWS_1252);
        assertTrue(Utf8Text.looksLikeMojibake(misdecoded), misdecoded);
        assertEquals(original, Utf8Text.restoreIfMisdecoded(misdecoded));
    }

    @Test
    void restoresIso88591MisdecodedUtf8() {
        String original = "é ü ñ";
        String misdecoded = new String(original.getBytes(StandardCharsets.UTF_8), StandardCharsets.ISO_8859_1);
        assertEquals(original, Utf8Text.restoreIfMisdecoded(misdecoded));
    }

    @Test
    void leavesValidUnicodeUnchanged() {
        String original = "é ü ñ ₹ “quotes” ’ apostrophes — em dash";
        assertEquals(original, Utf8Text.restoreIfMisdecoded(original));
        assertFalse(Utf8Text.looksLikeMojibake(original));
    }

    @Test
    void restoresRepeatedMojibake() {
        String original = "café GRAPHIC DESIGNER.pdf";
        String current = original;
        for (int i = 0; i < 3; i++) {
            current = new String(current.getBytes(StandardCharsets.UTF_8), WINDOWS_1252);
        }
        assertEquals(original, Utf8Text.restoreIfMisdecoded(current));
    }

    @Test
    void doesNotInventMojibakeFromValidUtf8() {
        String original = "José – GRAPHIC DESIGNER.pdf";
        String restored = Utf8Text.restoreIfMisdecoded(original);
        assertEquals(original, restored);
        assertFalse(Utf8Text.looksLikeMojibake(restored));
    }
}
