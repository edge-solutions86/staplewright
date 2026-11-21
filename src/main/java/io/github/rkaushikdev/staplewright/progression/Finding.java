package io.github.rkaushikdev.staplewright.progression;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One thing wrong with a schedule.
 *
 * @param code a short stable name for the kind of finding
 * @param severity how much the shop cares
 * @param message what is wrong, in a sentence
 * @param symbol the key it is about, when it is about one
 */
public record Finding(String code, Severity severity, String message, Optional<String> symbol) {

    /** A finding about the schedule as a whole rather than one key. */
    public Finding(String code, Severity severity, String message) {
        this(code, severity, message, Optional.empty());
    }

    /** A finding about one key. */
    public Finding(String code, Severity severity, String message, String symbol) {
        this(code, severity, message, Optional.of(symbol));
    }

    /** The finding flat, for a report or a response body: the symbol only when there is one. */
    public Map<String, Object> asDict() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("code", code);
        out.put("severity", severity.value());
        out.put("message", message);
        symbol.ifPresent(value -> out.put("symbol", value));
        return out;
    }
}
