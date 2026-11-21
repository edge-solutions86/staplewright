package io.github.rkaushikdev.staplewright.health;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Text;

/** How much of a problem a finding is: a note to know, a warning to look at, a fault already wrong. */
public enum Severity implements Valued {
    NOTE(1),
    WARNING(2),
    FAULT(3);

    private final int value;

    Severity(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    /** The severity in words, as the report prints it. */
    public String label() {
        return Text.lower(name());
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
