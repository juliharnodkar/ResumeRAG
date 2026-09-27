package com.example.resumerag;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class LexicalQueryPatternTest {

    @Test
    void javaDoesNotMatchJavaScript() {
        assertTrue(matches("Java", "Built backends in Java."));
        assertFalse(matches("Java", "Built frontends in JavaScript."));
        assertFalse(matches("Java", "javascript"));
    }

    @Test
    void javaScriptDoesNotMatchJava() {
        assertTrue(matches("JavaScript", "Experience with JavaScript."));
        assertFalse(matches("JavaScript", "Experience with Java."));
        assertFalse(matches("JavaScript", "java"));
    }

    @Test
    void awsAzureAndGcpRemainDistinct() {
        assertTrue(matches("AWS", "Deployed on AWS."));
        assertFalse(matches("AWS", "Deployed on Azure."));
        assertFalse(matches("AWS", "Deployed on GCP."));

        assertTrue(matches("Azure", "Deployed on Azure."));
        assertFalse(matches("Azure", "Deployed on AWS."));
        assertFalse(matches("Azure", "Deployed on GCP."));

        assertTrue(matches("GCP", "Deployed on GCP."));
        assertFalse(matches("GCP", "Deployed on AWS."));
        assertFalse(matches("GCP", "Deployed on Azure."));
    }

    @Test
    void cDoesNotMatchCppOrCsharp() {
        assertTrue(matches("C", "Low-level C programming."));
        assertFalse(matches("C", "Used C++ for games."));
        assertFalse(matches("C", "Used C# for Unity."));
    }

    @Test
    void cppAndCsharpMatchExactly() {
        assertTrue(matches("C++", "Used C++ for games."));
        assertTrue(matches("C#", "Used C# for Unity."));
        assertFalse(matches("C++", "Used C# for Unity."));
        assertFalse(matches("C#", "Used C++ for games."));
        assertFalse(matches("C++", "Low-level C programming."));
        assertFalse(matches("C#", "Low-level C programming."));
    }

    @Test
    void nodeJsWorksThroughStage2AVariants() {
        assertTrue(matches("Node.js", "Services in Node.js."));
        assertTrue(matches("nodejs", "Services in nodejs."));
        assertTrue(matches("node js", "Services in node js."));
        assertFalse(matches("Node.js", "Used a node in the graph."));
        assertFalse(matches("node", "Services in nodejs."));
    }

    @Test
    void springBootWorksAsMultiWordQuery() {
        assertTrue(matches("Spring Boot", "Built APIs with Spring Boot."));
        assertTrue(matches("Spring Boot", "Built APIs with Spring   Boot."));
        assertTrue(matches("Spring Boot", "spring-boot microservices"));
        assertFalse(matches("Spring Boot", "Spring without the boot framework"));
    }

    @Test
    void regexMetacharactersAreSafelyEscaped() {
        assertEquals("c\\+\\+", LexicalQueryPattern.escapePosix("c++"));
        assertEquals("rest", LexicalQueryPattern.escapePosix("rest"));

        String pattern = LexicalQueryPattern.fromQuery("C++").orElseThrow();
        assertTrue(pattern.contains("c\\+\\+"));
        assertFalse(pattern.contains("c++"));

        assertDoesNotThrow(() -> Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
        assertTrue(matches("C++", "Expert in C++."));
    }

    @Test
    void blankInputsProduceNoPattern() {
        assertEquals(Optional.empty(), LexicalQueryPattern.fromQuery(null));
        assertEquals(Optional.empty(), LexicalQueryPattern.fromQuery(""));
        assertEquals(Optional.empty(), LexicalQueryPattern.fromQuery("   "));
        assertEquals(Optional.empty(), LexicalQueryPattern.fromQuery("..."));
    }

    private static boolean matches(String query, String text) {
        Optional<String> pattern = LexicalQueryPattern.fromQuery(query);
        assertTrue(pattern.isPresent(), "expected a pattern for query: " + query);
        return Pattern.compile(pattern.get(), Pattern.CASE_INSENSITIVE)
                .matcher(text)
                .find();
    }
}
