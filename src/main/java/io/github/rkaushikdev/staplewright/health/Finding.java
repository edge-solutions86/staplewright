package io.github.rkaushikdev.staplewright.health;

import io.github.rkaushikdev.staplewright.util.Text;

/**
 * One thing wrong, named well enough to go and look at it.
 *
 * @param check the check that found it
 * @param severity how much it matters
 * @param subject the tag, serial, symbol or number it is about
 * @param detail what is wrong with it
 */
public record Finding(String check, Severity severity, String subject, String detail) {

    /** One line for the report. */
    public String describe() {
        return Text.left(severity.label(), 7) + " " + Text.left(subject, 16) + " " + detail;
    }
}
