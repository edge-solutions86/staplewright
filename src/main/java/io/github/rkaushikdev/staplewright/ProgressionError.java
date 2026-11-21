package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** The array cannot be progressed the way the system was set up. */
public class ProgressionError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "progression";

    public ProgressionError(String message) {
        super(message);
    }

    public ProgressionError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
