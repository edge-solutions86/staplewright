package io.github.rkaushikdev.staplewright.reports;

import java.util.ArrayList;
import java.util.List;

/**
 * Comma separated text, written and read the way the campus space system and the shop's
 * spreadsheets do it: a comma between fields, unix line endings, and a field quoted only when it has
 * to be, which is when it carries a comma, a quote or a line break. A quote inside a quoted field is
 * written twice.
 */
final class Csv {

    private Csv() {}

    /** One line of fields, with its line ending. */
    static String line(List<String> fields) {
        if (fields.size() == 1 && fields.get(0).isEmpty()) {
            return "\"\"\n";
        }
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append(field(fields.get(index)));
        }
        return out.append('\n').toString();
    }

    private static String field(String text) {
        boolean quote =
                text.indexOf(',') >= 0 || text.indexOf('"') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0;
        return quote ? "\"" + text.replace("\"", "\"\"") + "\"" : text;
    }

    /**
     * Every record in {@code text}, fields as written with the quoting taken off. A blank line is a
     * record with no fields. A line break inside quotes belongs to the field; a quote in the middle of
     * an unquoted field is just a character; a quoted field that runs to the end of the text ends
     * there.
     */
    static List<List<String>> read(String text) {
        List<List<String>> records = new ArrayList<>();
        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        boolean started = false;
        int index = 0;
        int length = text.length();
        while (index < length) {
            char at = text.charAt(index);
            if (quoted) {
                if (at == '"') {
                    if (index + 1 < length && text.charAt(index + 1) == '"') {
                        field.append('"');
                        index++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(at);
                }
            } else if (at == '"' && field.isEmpty() && !started) {
                quoted = true;
                started = true;
            } else if (at == ',') {
                record.add(field.toString());
                field.setLength(0);
                started = false;
            } else if (at == '\n' || at == '\r') {
                if (at == '\r' && index + 1 < length && text.charAt(index + 1) == '\n') {
                    index++;
                }
                if (started || !field.isEmpty() || !record.isEmpty()) {
                    record.add(field.toString());
                }
                records.add(List.copyOf(record));
                record = new ArrayList<>();
                field.setLength(0);
                started = false;
            } else {
                field.append(at);
                started = true;
            }
            index++;
        }
        if (quoted || started || !field.isEmpty() || !record.isEmpty()) {
            record.add(field.toString());
            records.add(List.copyOf(record));
        }
        return List.copyOf(records);
    }
}
