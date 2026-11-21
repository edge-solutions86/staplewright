package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** A manufacturer specification is inconsistent or was used wrongly. */
public class SpecificationError extends HaspError {

    /** The stable code a caller branches on. */
    public static final String CODE = "specification";

    public SpecificationError(String message) {
        super(message);
    }

    public SpecificationError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
