package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Arrays;
import java.util.Optional;

/** How the hardware on a door is doing. Ordered on purpose, best first, so a sort means something. */
public enum Condition implements Valued {
    GOOD(1),
    WORN(2),
    FAILING(3),
    BROKEN(4);

    private final int value;

    Condition(int value) {
        this.value = value;
    }

    @Override
    public Integer value() {
        return value;
    }

    /** The condition in words, as a survey prints it: {@code failing}. */
    public String label() {
        return Text.lower(name());
    }

    /** The condition written as {@code number}, when there is one. */
    public static Optional<Condition> find(int number) {
        return Arrays.stream(values()).filter(item -> item.value == number).findFirst();
    }

    /**
     * The condition written as {@code number}.
     *
     * @throws InputError when no condition is written that way
     */
    public static Condition fromValue(int number) {
        return find(number).orElseThrow(() -> new InputError("no such condition", Facts.of("value", number)));
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
