package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.util.Numbers;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A list of records written as json a person can read: two spaces an indent level, every key and
 * value on its own line, keys in column order, and anything outside printable ASCII escaped so the
 * file survives any mail system it is sent through.
 */
final class Json {

    private static final String INDENT = "  ";

    private Json() {}

    static String records(List<Map<String, Optional<Object>>> records) {
        if (records.isEmpty()) {
            return "[]";
        }
        StringBuilder out = new StringBuilder("[\n");
        for (int index = 0; index < records.size(); index++) {
            out.append(INDENT);
            object(out, records.get(index));
            out.append(index + 1 < records.size() ? ",\n" : "\n");
        }
        return out.append(']').toString();
    }

    private static void object(StringBuilder out, Map<String, Optional<Object>> record) {
        if (record.isEmpty()) {
            out.append("{}");
            return;
        }
        out.append("{\n");
        int left = record.size();
        for (Map.Entry<String, Optional<Object>> entry : record.entrySet()) {
            out.append(INDENT).append(INDENT);
            text(out, entry.getKey());
            out.append(": ");
            value(out, entry.getValue());
            left--;
            out.append(left > 0 ? ",\n" : "\n");
        }
        out.append(INDENT).append('}');
    }

    private static void value(StringBuilder out, Optional<Object> value) {
        if (value.isEmpty()) {
            out.append("null");
            return;
        }
        Object found = value.get();
        switch (found) {
            case CharSequence chars -> text(out, chars.toString());
            case Boolean flag -> out.append(flag ? "true" : "false");
            case Integer number -> out.append(number);
            case Long number -> out.append(number);
            case Double number -> out.append(Numbers.repr(number));
            default -> throw new IllegalArgumentException("json carries text, numbers and nothing else: " + found);
        }
    }

    private static void text(StringBuilder out, String text) {
        out.append('"');
        for (int index = 0; index < text.length(); index++) {
            char at = text.charAt(index);
            switch (at) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                default -> {
                    if (at < ' ' || at > '~') {
                        out.append(String.format(Locale.ROOT, "\\u%04x", (int) at));
                    } else {
                        out.append(at);
                    }
                }
            }
        }
        out.append('"');
    }
}
