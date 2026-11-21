package io.github.rkaushikdev.staplewright;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One way of saying no when a record is in the wrong state.
 *
 * <p>The state always reaches the facts under the name {@code state}, and the states that would
 * have been allowed under {@code allowed}, so a caller can tell a wrong state from a missing record
 * without knowing which object it was talking to. The message stays with the caller: a work order
 * and a lockout call are not in the same state for different reasons, and a shared message would
 * be so vague nobody could act on it.
 */
public final class Lifecycle {

    private Lifecycle() {}

    /** Refuse unless the record is in one of the states given. */
    public static void requireState(Object current, Collection<?> allowed, String message) {
        requireState(current, allowed, message, Map.of());
    }

    /**
     * Refuse unless the record is in one of the states given, with the caller's own facts after the
     * state and the allowed states.
     */
    public static void requireState(Object current, Collection<?> allowed, String message, Map<String, ?> facts) {
        if (allowed.stream().anyMatch(state -> same(state, current))) {
            return;
        }
        Map<String, Object> carried = new LinkedHashMap<>();
        carried.put("state", stateOf(current));
        carried.put("allowed", allowed.stream().map(Lifecycle::stateOf).toList());
        carried.putAll(facts);
        throw new ConflictError(message, carried);
    }

    /** Refuse unless a plain condition holds. The other half of the same job. */
    public static void require(boolean condition, String message) {
        require(condition, message, Map.of());
    }

    public static void require(boolean condition, String message, Map<String, ?> facts) {
        if (!condition) {
            throw new ConflictError(message, facts);
        }
    }

    /** The same state, whether it is held as a choice or as the value it is written as. */
    private static boolean same(Object state, Object current) {
        if (state == null || current == null) {
            return state == current;
        }
        if (state.equals(current)) {
            return true;
        }
        return (state instanceof Valued || current instanceof Valued)
                && stateOf(state).equals(stateOf(current));
    }

    /** However a state is spelled, this is what goes in a message: a choice's value, or its text. */
    public static String stateOf(Object value) {
        if (value instanceof Valued choice) {
            return String.valueOf(choice.value());
        }
        return String.valueOf(value);
    }
}
