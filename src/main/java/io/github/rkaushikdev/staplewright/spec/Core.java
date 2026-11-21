package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * One interchangeable core, and the control key that pulls it.
 *
 * @param serial the number stamped on the core
 * @param format the housing family it drops into
 * @param controlCode the code of the control key that pulls it
 * @param housing what it is sitting in
 * @param opening the door it is installed in, blank for a core on the shelf
 */
public record Core(String serial, CoreFormat format, String controlCode, HousingKind housing, String opening) {

    public Core {
        if (Text.isBlank(serial)) {
            throw new InputError("a core has a serial");
        }
        if (Text.isBlank(controlCode)) {
            throw new InputError("a core is pulled by some control key", Facts.of("serial", serial));
        }
    }

    /** A core on the shelf. */
    public Core(String serial, CoreFormat format, String controlCode, HousingKind housing) {
        this(serial, format, controlCode, housing, "");
    }

    public boolean isInstalled() {
        return !opening.isEmpty();
    }

    /** Whether these two cores could swap housings, which is the risk. */
    public boolean fits(Core other) {
        return format == other.format && housing == other.housing;
    }
}
