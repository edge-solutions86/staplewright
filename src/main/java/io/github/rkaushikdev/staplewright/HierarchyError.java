package io.github.rkaushikdev.staplewright;

import java.util.Map;

/** A key symbol or a level does not fit the schedule it was handed to. */
public class HierarchyError extends StaplewrightError {

    /** The stable code a caller branches on. */
    public static final String CODE = "hierarchy";

    public HierarchyError(String message) {
        super(message);
    }

    public HierarchyError(String message, Map<String, ?> facts) {
        super(message, facts);
    }

    @Override
    public String code() {
        return CODE;
    }
}
