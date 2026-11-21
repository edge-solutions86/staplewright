package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** Somebody typed something the library cannot read. */
public class InputError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "input";

    public InputError(String message) {
        super(message);
    }

    public InputError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
