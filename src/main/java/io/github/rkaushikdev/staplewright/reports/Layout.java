package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Laying out a printed page, once, for every report that prints one.
 *
 * <p>Joining with newlines, padding a value into a column and writing a heading live here and
 * nowhere else, so the pinning chart and the keyholder statement line their columns up the same way
 * and a page with both on it reads as one document.
 *
 * <p>Everything here is fixed width because everything here gets printed. There is no wrapping and
 * no truncation: a value longer than its column pushes the rest of the line along, which is ugly and
 * visible, and both of those are better than a room number quietly losing its last character.
 */
public final class Layout {

    /** The character a rule is drawn with. */
    public static final String RULE = "-";

    /** How wide a printed page is, in characters. */
    public static final int PAGE_WIDTH = 72;

    private Layout() {}

    /**
     * Values padded into left aligned columns, the last one unpadded, with no white space left at the
     * end of the line. A missing value prints as nothing; a value past the last width is not padded.
     */
    public static String columns(List<?> values, int... widths) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.size(); index++) {
            Object value = values.get(index);
            String text = value == null ? "" : String.valueOf(value);
            line.append(index < widths.length ? Text.left(text, widths[index]) : text);
        }
        return stripEnd(line.toString());
    }

    /** Values right aligned in equal columns, which is how numbers read. */
    public static String right(List<?> values, int width) {
        return values.stream()
                .map(value -> Text.right(String.valueOf(value), width))
                .collect(Collectors.joining());
    }

    /** A heading line, laid out the same way as the rows under it. */
    public static String heading(List<String> names, int... widths) {
        return columns(names, widths);
    }

    /** A rule across the whole page. */
    public static String rule() {
        return rule(PAGE_WIDTH);
    }

    /** A rule {@code width} characters long. */
    public static String rule(int width) {
        return RULE.repeat(width);
    }

    /** A page with a title on it, or the same page without one when the title is blank. */
    public static List<String> titled(String title, List<String> lines) {
        List<String> page = new ArrayList<>();
        if (!title.isEmpty()) {
            page.add(title);
        }
        page.addAll(lines);
        return List.copyOf(page);
    }

    /** The lines joined into one document, a newline between each and none after the last. */
    public static String asText(List<String> lines) {
        return String.join("\n", lines);
    }

    /** {@code text} without white space at its end, which is how every printed line finishes. */
    static String stripEnd(String text) {
        int end = text.length();
        while (end > 0 && Text.isSpace(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end);
    }
}
