package com.example.resumerag;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ConceptQueryExpanderTest {

    @Test
    void originalQueryIsAlwaysRetained() {
        assertTrue(ConceptQueryExpander.expand("Java").contains("Java"));
        assertTrue(ConceptQueryExpander.expand("AWS").contains("AWS"));
        assertTrue(ConceptQueryExpander.expand("Spring Boot").contains("Spring Boot"));
    }

    @Test
    void originalQueryIsFirst() {
        assertEquals("Java", ConceptQueryExpander.expand("Java").get(0));
        assertEquals("AWS", ConceptQueryExpander.expand("AWS").get(0));
        assertEquals("Spring Boot", ConceptQueryExpander.expand("Spring Boot").get(0));
    }

    @Test
    void expansionIsDeterministic() {
        List<String> first = ConceptQueryExpander.expand("Spring Boot");
        List<String> second = ConceptQueryExpander.expand("Spring Boot");
        assertEquals(first, second);
    }

    @Test
    void expansionIsBounded() {
        assertTrue(ConceptQueryExpander.expand("Spring Boot").size() <= 3);
        assertTrue(ConceptQueryExpander.expand("AWS").size() <= 3);
        assertTrue(ConceptQueryExpander.expand("Java").size() <= 3);
    }

    @Test
    void noDuplicateQueries() {
        List<String> queries = ConceptQueryExpander.expand("Spring Boot");
        assertEquals(queries.stream().distinct().count(), queries.size());
    }

    @Test
    void unknownConceptQuantumFluxRemainsSearchable() {
        List<String> queries = ConceptQueryExpander.expand("QuantumFlux");
        assertEquals(1, queries.size());
        assertEquals("QuantumFlux", queries.get(0));
    }

    @Test
    void awsDoesNotExpandToAzureOrGcp() {
        List<String> queries = ConceptQueryExpander.expand("AWS");
        for (String q : queries) {
            assertFalse(q.toLowerCase().contains("azure"));
            assertFalse(q.toLowerCase().contains("gcp"));
            assertFalse(q.toLowerCase().contains("google"));
        }
    }

    @Test
    void azureDoesNotExpandToAwsOrGcp() {
        List<String> queries = ConceptQueryExpander.expand("Azure");
        for (String q : queries) {
            assertFalse(q.toLowerCase().contains("aws"));
            assertFalse(q.toLowerCase().contains("amazon"));
            assertFalse(q.toLowerCase().contains("gcp"));
            assertFalse(q.toLowerCase().contains("google"));
        }
    }

    @Test
    void gcpDoesNotExpandToAwsOrAzure() {
        List<String> queries = ConceptQueryExpander.expand("GCP");
        for (String q : queries) {
            assertFalse(q.toLowerCase().contains("aws"));
            assertFalse(q.toLowerCase().contains("amazon"));
            assertFalse(q.toLowerCase().contains("azure"));
        }
    }

    @Test
    void javaDoesNotExpandToJavaScript() {
        List<String> queries = ConceptQueryExpander.expand("Java");
        for (String q : queries) {
            assertFalse(q.toLowerCase().contains("javascript"));
            assertFalse(q.toLowerCase().contains("js"));
        }
    }

    @Test
    void javaScriptDoesNotExpandToJava() {
        List<String> queries = ConceptQueryExpander.expand("JavaScript");
        // JavaScript doesn't have an expansion rule in our table currently, but it should not become Java.
        for (String q : queries) {
            assertTrue(q.equalsIgnoreCase("JavaScript") || !q.toLowerCase().contains("java"));
        }
    }

    @Test
    void shortConceptsAreNotAggressivelyExpanded() {
        assertEquals(List.of("SQL"), ConceptQueryExpander.expand("SQL"));
        assertEquals(List.of("REST"), ConceptQueryExpander.expand("REST"));
        assertEquals(List.of("Docker"), ConceptQueryExpander.expand("Docker"));
    }

    @Test
    void supportedMultiWordConceptCanReceiveControlledVariants() {
        List<String> queries = ConceptQueryExpander.expand("REST API");
        assertTrue(queries.size() > 1);
        assertTrue(queries.contains("restful api"));
    }
}
