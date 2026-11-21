package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** A cut sequence is not one the specification allows. */
public class BittingError extends StaplewrightError {

    /** The stable code a caller branches on. */
    public static final String CODE = "bitting";

    public BittingError(String message) {
        super(message);
    }

    public BittingError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
