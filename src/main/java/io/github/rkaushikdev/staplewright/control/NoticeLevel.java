package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Text;

/** How hard the shop is currently asking for a key back. Ordered on purpose, gentlest first. */
public enum NoticeLevel implements Valued {
    REMINDER(1),
    FIRST(2),
    SECOND(3),
    HOLD(4),
    WRITTEN_OFF(5);

    private final int value;

    NoticeLevel(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    /** The level in words, as a notice prints it: {@code written off}. */
    public String label() {
        return Text.lower(name()).replace('_', ' ');
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
