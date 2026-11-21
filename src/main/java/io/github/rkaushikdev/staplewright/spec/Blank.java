package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.KeywayError;

/**
 * A key blank as it sits in the drawer, with its part number.
 *
 * @param partNumber the manufacturer's part number
 * @param section the keyway section it is milled for
 * @param manufacturer who makes it
 * @param chambers how many cuts it takes
 * @param stock how many are in the drawer
 */
public record Blank(String partNumber, String section, String manufacturer, int chambers, int stock) {

    public Blank {
        if (chambers < 1) {
            throw new KeywayError("a blank has at least one chamber", Facts.of("part", partNumber));
        }
        if (stock < 0) {
            throw new KeywayError("the drawer cannot hold less than nothing", Facts.of("part", partNumber));
        }
    }

    /** A blank with none in the drawer. */
    public Blank(String partNumber, String section, String manufacturer, int chambers) {
        this(partNumber, section, manufacturer, chambers, 0);
    }
}
