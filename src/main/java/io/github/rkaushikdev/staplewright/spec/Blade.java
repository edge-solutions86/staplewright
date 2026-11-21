package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;

/**
 * One blank, measured, because they are not all the length they say.
 *
 * @param partNumber the manufacturer's part number
 * @param lengthMils how long this blank actually is, shoulder to tip
 * @param nominalMils how long the catalogue says it is
 */
public record Blade(String partNumber, int lengthMils, int nominalMils) {

    public Blade {
        if (lengthMils <= 0 || nominalMils <= 0) {
            throw new InputError("a blade has a length", Facts.of("part", partNumber));
        }
    }

    /** How far off nominal this blank is. Positive means long. */
    public int errorMils() {
        return lengthMils - nominalMils;
    }

    public boolean isNominal() {
        return errorMils() == 0;
    }
}
