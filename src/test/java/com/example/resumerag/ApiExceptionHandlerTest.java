package com.example.resumerag;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ApiExceptionHandlerTest {

    @Test
    void unexpectedErrorsDoNotExposeInternalDetails() {
        ResponseEntity<Map<String, String>> response =
                new ApiExceptionHandler().unexpected(
                        new IllegalStateException("database password leaked"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertEquals("Unexpected server error.", response.getBody().get("error"));
        assertFalse(response.getBody().get("error").contains("password"));
    }
}
