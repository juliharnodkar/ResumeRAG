package com.example.resumerag.model;

public record Evidence(
        String text,
        String section,
        String project,
        double relevance,
        boolean directMention
) {}
