package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Arrays;
import java.util.Optional;

/**
 * Where in a chamber's column a segment belongs.
 *
 * <p>From the bottom of the plug up: one bottom pin against the key, a master wafer for every extra
 * depth the chamber breaks at, a driver on top of all of it, then a spring.
 */
public enum SegmentKind implements Valued {
    BOTTOM("bottom"),
    WAFER("wafer"),
    DRIVER("driver"),
    SPRING("spring");

    private final String value;

    SegmentKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The kind written as {@code text}, when there is one. */
    public static Optional<SegmentKind> find(String text) {
        return Arrays.stream(values()).filter(kind -> kind.value.equals(text)).findFirst();
    }

    /** The kind written as {@code text}, refusing anything else. */
    public static SegmentKind fromValue(String text) {
        return find(text).orElseThrow(() -> new InputError("no such segment kind", Facts.of("value", text)));
    }

    @Override
    public String toString() {
        return value;
    }
}
