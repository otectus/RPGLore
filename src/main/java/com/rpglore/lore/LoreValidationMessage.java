package com.rpglore.lore;

import javax.annotation.Nullable;

/**
 * One structured diagnostic produced while parsing a lore book definition.
 *
 * <p>Line and column are 1-based; {@code -1} means "unknown" and suppresses the
 * {@code :line:col} suffix in {@link #format()}.
 */
public record LoreValidationMessage(
        Severity severity,
        String source,
        @Nullable String bookId,
        @Nullable String jsonPath,
        String message,
        @Nullable String expected,
        @Nullable String actual,
        int line,
        int column,
        @Nullable String suggestion
) {

    public enum Severity { INFO, WARNING, ERROR }

    public static final int UNKNOWN_POSITION = -1;

    // --- Factory helpers ---

    public static LoreValidationMessage error(String source, @Nullable String bookId, @Nullable String jsonPath,
                                              String message) {
        return of(Severity.ERROR, source, bookId, jsonPath, message, null, null,
                UNKNOWN_POSITION, UNKNOWN_POSITION, null);
    }

    public static LoreValidationMessage error(String source, @Nullable String bookId, @Nullable String jsonPath,
                                              String message, @Nullable String expected, @Nullable String actual) {
        return of(Severity.ERROR, source, bookId, jsonPath, message, expected, actual,
                UNKNOWN_POSITION, UNKNOWN_POSITION, null);
    }

    public static LoreValidationMessage warning(String source, @Nullable String bookId, @Nullable String jsonPath,
                                                String message) {
        return of(Severity.WARNING, source, bookId, jsonPath, message, null, null,
                UNKNOWN_POSITION, UNKNOWN_POSITION, null);
    }

    public static LoreValidationMessage warning(String source, @Nullable String bookId, @Nullable String jsonPath,
                                                String message, @Nullable String expected, @Nullable String actual) {
        return of(Severity.WARNING, source, bookId, jsonPath, message, expected, actual,
                UNKNOWN_POSITION, UNKNOWN_POSITION, null);
    }

    public static LoreValidationMessage info(String source, @Nullable String bookId, @Nullable String jsonPath,
                                             String message) {
        return of(Severity.INFO, source, bookId, jsonPath, message, null, null,
                UNKNOWN_POSITION, UNKNOWN_POSITION, null);
    }

    public static LoreValidationMessage of(Severity severity, String source, @Nullable String bookId,
                                           @Nullable String jsonPath, String message, @Nullable String expected,
                                           @Nullable String actual, int line, int column,
                                           @Nullable String suggestion) {
        return new LoreValidationMessage(severity, source, bookId, jsonPath, message, expected, actual,
                line, column, suggestion);
    }

    /** Returns a copy of this message with the given source position attached. */
    public LoreValidationMessage withPosition(int newLine, int newColumn) {
        return new LoreValidationMessage(severity, source, bookId, jsonPath, message, expected, actual,
                newLine, newColumn, suggestion);
    }

    /** Returns a copy of this message with the given suggestion attached. */
    public LoreValidationMessage withSuggestion(@Nullable String newSuggestion) {
        return new LoreValidationMessage(severity, source, bookId, jsonPath, message, expected, actual,
                line, column, newSuggestion);
    }

    /** Returns a copy of this message with the given book id attached. */
    public LoreValidationMessage withBookId(@Nullable String newBookId) {
        return new LoreValidationMessage(severity, source, newBookId, jsonPath, message, expected, actual,
                line, column, suggestion);
    }

    public boolean hasPosition() {
        return line > 0 && column > 0;
    }

    /**
     * Renders the multi-line human-readable form. Null parts are omitted entirely
     * rather than producing blank lines.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append(severity.name()).append(' ').append(source);
        if (hasPosition()) {
            sb.append(':').append(line).append(':').append(column);
        }
        if (jsonPath != null && !jsonPath.isEmpty()) {
            sb.append('\n').append(stripRoot(jsonPath));
        }
        sb.append('\n').append(message);
        if (expected != null && actual != null) {
            sb.append("\nExpected ").append(expected).append(", but found ").append(actual).append('.');
        } else if (expected != null) {
            sb.append("\nExpected ").append(expected).append('.');
        } else if (actual != null) {
            sb.append("\nFound ").append(actual).append('.');
        }
        if (suggestion != null && !suggestion.isEmpty()) {
            sb.append('\n').append(suggestion);
        }
        return sb.toString();
    }

    private static String stripRoot(String path) {
        return path.startsWith("$.") ? path.substring(2) : path;
    }
}
