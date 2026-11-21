package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/**
 * The four ways what comes back at an audit fails to match the ledger, which are all different
 * problems.
 */
public enum FindingKind implements Valued {
    /** Out on the ledger and nobody can produce it. This is the one that costs money. */
    UNACCOUNTED("unaccounted"),
    /** Turned up and is not in the ledger at all, which usually means a hardware store cut it. */
    STRAY("stray"),
    /** Closed on the ledger and somebody is still carrying it: a key that came back on paper only. */
    CLOSED("closed"),
    /** In the wrong hands: a transfer that never got written down. */
    WRONG_HOLDER("wrong_holder");

    private final String value;

    FindingKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The kind written as {@code text}, when there is one. */
    public static Optional<FindingKind> find(String text) {
        return Choices.find(FindingKind.class, text);
    }

    /** The kind written as {@code text}, refusing anything else. */
    public static FindingKind fromValue(String text) {
        return Choices.fromValue(FindingKind.class, text, "audit finding");
    }

    @Override
    public String toString() {
        return value;
    }
}
