package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** A blank or a keyway section will not do what was asked of it. */
public class KeywayError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "keyway";

    public KeywayError(String message) {
        super(message);
    }

    public KeywayError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
