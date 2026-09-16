package com.example.resumerag.skill;

import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ExperienceDurationParser {

    private ExperienceDurationParser() {}

    private static final Pattern YEARS = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*\\+?\\s*years?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MONTHS = Pattern.compile(
            "(\\d+)\\s*\\+?\\s*months?\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern MONTH_YEAR_RANGE = Pattern.compile(
            "(?i)(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(\\d{4})\\s*[-]\\s*(?:(Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)\\s+(\\d{4})|Present)");

    private static final Pattern YEAR_RANGE = Pattern.compile(
            "(?i)(19\\d{2}|20\\d{2})\\s*[-]\\s*(19\\d{2}|20\\d{2}|Present)");

    public static Integer parseMonths(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        Matcher years = YEARS.matcher(text);
        if (years.find()) {
            double value = Double.parseDouble(years.group(1));
            return (int) Math.round(value * 12);
        }

        Matcher months = MONTHS.matcher(text);
        if (months.find()) {
            return Integer.parseInt(months.group(1));
        }

        Matcher monthRange = MONTH_YEAR_RANGE.matcher(text);
        if (monthRange.find()) {
            int startMonth = monthNumber(monthRange.group(1));
            int startYear = Integer.parseInt(monthRange.group(2));

            if ("Present".equalsIgnoreCase(monthRange.group(3))
                    && monthRange.group(4) == null) {
                YearMonth start = YearMonth.of(startYear, startMonth);
                return Math.max(0,
                        (int) ChronoUnit.MONTHS.between(start, YearMonth.now()));
            }

            int endMonth = monthNumber(monthRange.group(3));
            int endYear = Integer.parseInt(monthRange.group(4));

            return Math.max(0,
                    (int) ChronoUnit.MONTHS.between(
                            YearMonth.of(startYear, startMonth),
                            YearMonth.of(endYear, endMonth)) + 1);
        }

        Matcher yearRange = YEAR_RANGE.matcher(text);
        if (yearRange.find()) {
            int startYear = Integer.parseInt(yearRange.group(1));
            String end = yearRange.group(2);

            int endYear = "Present".equalsIgnoreCase(end)
                    ? YearMonth.now().getYear()
                    : Integer.parseInt(end);

            return Math.max(0, (endYear - startYear) * 12);
        }

        return null;
    }

    private static int monthNumber(String month) {
        String m = month.toLowerCase(Locale.ROOT).substring(0, 3);

        return switch (m) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> throw new IllegalArgumentException("Unknown month: " + month);
        };
    }
}
