package com.example.resumerag;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Restores UTF-8 text that was incorrectly decoded as Windows-1252 / ISO-8859-1.
 *
 * <p>This is an encoding reinterpretation (bytes → UTF-8), not a cosmetic
 * replacement of mojibake glyphs. Valid Unicode is left unchanged because
 * those characters do not encode back to well-formed UTF-8 via Latin-1.
 */
public final class Utf8Text {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");
    private static final int MAX_PASSES = 8;

    private Utf8Text() {
    }

    public static String restoreIfMisdecoded(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String current = value;
        for (int i = 0; i < MAX_PASSES; i++) {
            if (!looksLikeMojibake(current)) {
                break;
            }
            String restored = betterRestoration(current);
            if (restored == null || restored.equals(current)
                    || mojibakeScore(restored) >= mojibakeScore(current)) {
                break;
            }
            current = restored;
        }
        return current;
    }

    private static String betterRestoration(String current) {
        String from1252 = restoreWith(current, WINDOWS_1252);
        String fromLatin1 = restoreWith(current, StandardCharsets.ISO_8859_1);
        if (from1252 == null) {
            return fromLatin1;
        }
        if (fromLatin1 == null) {
            return from1252;
        }
        return mojibakeScore(fromLatin1) < mojibakeScore(from1252) ? fromLatin1 : from1252;
    }

    private static String restoreWith(String current, Charset source) {
        byte[] bytes = encodeStrict(current, source);
        if (bytes == null || !isWellFormedUtf8(bytes)) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static byte[] encodeStrict(String value, Charset charset) {
        CharsetEncoder encoder = charset.newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            ByteBuffer buffer = encoder.encode(CharBuffer.wrap(value));
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        } catch (CharacterCodingException ex) {
            return null;
        }
    }

    static boolean looksLikeMojibake(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        return value.indexOf('\u00C3') >= 0   // Ã
                || value.indexOf('\u00C2') >= 0   // Â
                || value.contains("â€")
                || value.contains("Ãƒ")
                || value.indexOf('\uFFFD') >= 0;
    }

    static int mojibakeScore(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        int score = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\u00C3' || c == '\u00C2' || c == '\u00E2' || c == '\uFFFD') {
                score++;
            }
        }
        if (value.contains("Ãƒ")) {
            score += 4;
        }
        if (value.contains("â€")) {
            score += 4;
        }
        return score;
    }

    private static boolean isWellFormedUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException ex) {
            return false;
        }
    }
}
