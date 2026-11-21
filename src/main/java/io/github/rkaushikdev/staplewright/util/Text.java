package io.github.rkaushikdev.staplewright.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Reading what somebody typed, with one meaning of white space and one meaning of a digit.
 *
 * <p>White space is every character Unicode treats as a separator or a line break, the no-break
 * space and the ideographic space included, so text pasted from a spreadsheet trims the same as
 * text typed at the terminal.
 */
public final class Text {

    private Text() {}

    /** Whether {@code at} is white space. */
    public static boolean isSpace(char at) {
        return switch (at) {
            case '\t',
                    '\n',
                    '\u000b',
                    '\f',
                    '\r',
                    '\u001c',
                    '\u001d',
                    '\u001e',
                    '\u001f',
                    ' ',
                    '\u0085',
                    ' ',
                    ' ',
                    ' ',
                    ' ',
                    ' ',
                    ' ',
                    '　' -> true;
            default -> at >= ' ' && at <= ' ';
        };
    }

    /** {@code text} without white space at either end. */
    public static String strip(String text) {
        int start = 0;
        int end = text.length();
        while (start < end && isSpace(text.charAt(start))) {
            start++;
        }
        while (end > start && isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(start, end);
    }

    /** Whether there is nothing in {@code text} but white space. */
    public static boolean isBlank(String text) {
        return strip(text).isEmpty();
    }

    /** Whether {@code text} is one or more digits and nothing else. */
    public static boolean isDigits(String text) {
        return !text.isEmpty() && text.chars().allMatch(Character::isDigit);
    }

    /** Whether {@code text} is one or more letters and nothing else. */
    public static boolean isAlpha(String text) {
        return !text.isEmpty() && text.chars().allMatch(Character::isLetter);
    }

    /** Whether {@code text} is one or more letters or digits and nothing else. */
    public static boolean isAlnum(String text) {
        return !text.isEmpty() && text.chars().allMatch(Character::isLetterOrDigit);
    }

    /** {@code text} in capitals, the same on every machine. */
    public static String upper(String text) {
        return text.toUpperCase(Locale.ROOT);
    }

    /** {@code text} in small letters, the same on every machine. */
    public static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }

    /**
     * {@code text} split on runs of white space, with nothing at either end: {@code " a  b "} is
     * {@code [a, b]} and a blank line is no words at all.
     */
    public static List<String> words(String text) {
        List<String> found = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        for (int index = 0; index < text.length(); index++) {
            char at = text.charAt(index);
            if (isSpace(at)) {
                if (!word.isEmpty()) {
                    found.add(word.toString());
                    word.setLength(0);
                }
            } else {
                word.append(at);
            }
        }
        if (!word.isEmpty()) {
            found.add(word.toString());
        }
        return List.copyOf(found);
    }

    /**
     * A whole number read from text the way a person writes one: white space around it allowed, an
     * optional sign, digits with single underscores between groups. Empty when it is not one.
     */
    public static Optional<Long> wholeNumber(String text) {
        String cleaned = strip(text);
        int start = 0;
        if (!cleaned.isEmpty() && (cleaned.charAt(0) == '+' || cleaned.charAt(0) == '-')) {
            start = 1;
        }
        String body = cleaned.substring(start);
        if (body.isEmpty() || body.startsWith("_") || body.endsWith("_") || body.contains("__")) {
            return Optional.empty();
        }
        String digits = body.replace("_", "");
        if (!isDigits(digits)) {
            return Optional.empty();
        }
        try {
            long value = Long.parseLong(digits);
            return Optional.of(cleaned.charAt(0) == '-' ? -value : value);
        } catch (NumberFormatException tooLong) {
            return Optional.empty();
        }
    }

    /** {@code text} padded on the right to {@code width}, as a column of a printed table. */
    public static String left(Object text, int width) {
        return String.format(Locale.ROOT, "%-" + width + "s", text);
    }

    /** {@code text} padded on the left to {@code width}, as a column of figures. */
    public static String right(Object text, int width) {
        return String.format(Locale.ROOT, "%" + width + "s", text);
    }
}
