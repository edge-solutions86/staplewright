package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** Something was looked up by name or number and is not there. */
public class NotFoundError extends StaplewrightError {

    /** The stable code a caller branches on. */
    public static final String CODE = "not_found";

    public NotFoundError(String message) {
        super(message);
    }

    public NotFoundError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
