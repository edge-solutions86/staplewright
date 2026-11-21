package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** The stack the shop would have to build does not exist in metal. */
public class PinningError extends StaplewrightError {

    /** The stable code a caller branches on. */
    public static final String CODE = "pinning";

    public PinningError(String message) {
        super(message);
    }

    public PinningError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
