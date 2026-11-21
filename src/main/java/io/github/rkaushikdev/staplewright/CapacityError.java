package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** The system has run out of the thing being asked for. */
public class CapacityError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "capacity";

    public CapacityError(String message) {
        super(message);
    }

    public CapacityError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
