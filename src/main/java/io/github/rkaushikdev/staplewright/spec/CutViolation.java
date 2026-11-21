package io.github.rkaushikdev.staplewright.spec;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * One reason a bitting was turned down.
 *
 * @param rule the rule's short name
 * @param message what is wrong, in a sentence
 * @param chamber the chamber it starts at, when the rule is about one
 * @param facts the numbers behind it, in the order they read best
 */
public record CutViolation(String rule, String message, Optional<Integer> chamber, Map<String, Object> facts) {

    public CutViolation {
        facts = Collections.unmodifiableMap(new LinkedHashMap<>(facts));
    }

    /** A violation of the whole key, with nothing more to say. */
    public CutViolation(String rule, String message) {
        this(rule, message, Optional.empty(), Map.of());
    }

    /** The rule, the message, the chamber when there is one, then the facts. */
    public Map<String, Object> asDict() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("rule", rule);
        out.put("message", message);
        chamber.ifPresent(at -> out.put("chamber", at));
        out.putAll(facts);
        return out;
    }
}
