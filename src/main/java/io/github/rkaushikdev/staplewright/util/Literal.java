package io.github.rkaushikdev.staplewright.util;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A value written out the way a refusal's facts are printed: text in quotes, numbers bare, a run
 * of values in brackets, nothing written as {@code None}.
 *
 * <p>Text takes single quotes unless it holds a single quote and no double quote, and control
 * characters are escaped, so a fact always prints on one line and reads back unambiguously. A day
 * and a choice print as the text they are written as.
 */
public final class Literal {

    private Literal() {}

    /** One value, quoted when it is text. */
    public static String of(Object value) {
        return switch (value) {
            case null -> "None";
            case Boolean flag -> flag ? "True" : "False";
            case String text -> quoted(text);
            case Double number -> Numbers.repr(number);
            case Float number -> Numbers.repr(number.doubleValue());
            case BigDecimal number -> number.toPlainString();
            case Number number -> number.toString();
            case Character character -> quoted(character.toString());
            case LocalDate day -> quoted(day.toString());
            case Enum<?> member -> quoted(member.toString());
            case List<?> items -> tuple(items);
            case Collection<?> items -> items.isEmpty() ? "set()" : "{" + joined(items) + "}";
            case Map<?, ?> entries ->
                "{"
                        + entries.entrySet().stream()
                                .map(entry -> of(entry.getKey()) + ": " + of(entry.getValue()))
                                .collect(Collectors.joining(", "))
                        + "}";
            default -> value.toString();
        };
    }

    /** A value as plain text: text as it is, anything else as {@link #of} writes it. */
    public static String plain(Object value) {
        return value instanceof String text ? text : of(value);
    }

    /** Text in quotes, escaped so it reads back as exactly itself. */
    public static String quoted(String text) {
        char quote = text.indexOf('\'') >= 0 && text.indexOf('"') < 0 ? '"' : '\'';
        StringBuilder out = new StringBuilder(text.length() + 2).append(quote);
        for (int index = 0; index < text.length(); index++) {
            char at = text.charAt(index);
            switch (at) {
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (at == quote) {
                        out.append('\\').append(at);
                    } else if (at < 0x20 || (at >= 0x7f && at <= 0xa0)) {
                        out.append(String.format("\\x%02x", (int) at));
                    } else {
                        out.append(at);
                    }
                }
            }
        }
        return out.append(quote).toString();
    }

    private static String tuple(List<?> items) {
        if (items.size() == 1) {
            return "(" + of(items.get(0)) + ",)";
        }
        return "(" + joined(items) + ")";
    }

    private static String joined(Collection<?> items) {
        return items.stream().map(Literal::of).collect(Collectors.joining(", "));
    }
}
