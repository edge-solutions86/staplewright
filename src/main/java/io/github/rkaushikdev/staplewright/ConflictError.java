package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** Two facts that both have to be true are not. */
public class ConflictError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "conflict";

    public ConflictError(String message) {
        super(message);
    }

    public ConflictError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
