package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/** Where a work order is, from drafted to done or cancelled. */
public enum OrderState implements Valued {
    DRAFTED("drafted"),
    SCHEDULED("scheduled"),
    STARTED("started"),
    DONE("done"),
    CANCELLED("cancelled");

    private final String value;

    OrderState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The order state written as {@code text}, when there is one. */
    public static Optional<OrderState> find(String text) {
        return Choices.find(OrderState.class, text);
    }

    /** The order state written as {@code text}, refusing anything else. */
    public static OrderState fromValue(String text) {
        return Choices.fromValue(OrderState.class, text, "order state");
    }

    @Override
    public String toString() {
        return value;
    }
}
