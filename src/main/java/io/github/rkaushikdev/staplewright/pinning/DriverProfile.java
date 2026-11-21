package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Arrays;
import java.util.Optional;

/**
 * What the side of a driver looks like.
 *
 * <p>A standard driver is a plain cylinder. A spool or a serrated driver has a waist cut into it so
 * that a pick sets it false, and the dorms get those on anything facing a corridor.
 */
public enum DriverProfile implements Valued {
    STANDARD("standard"),
    SPOOL("spool"),
    SERRATED("serrated");

    private final String value;

    DriverProfile(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The profile written as {@code text}, when there is one. */
    public static Optional<DriverProfile> find(String text) {
        return Arrays.stream(values())
                .filter(profile -> profile.value.equals(text))
                .findFirst();
    }

    /** The profile written as {@code text}, refusing anything else. */
    public static DriverProfile fromValue(String text) {
        return find(text).orElseThrow(() -> new InputError("no such driver profile", Facts.of("value", text)));
    }

    @Override
    public String toString() {
        return value;
    }
}
