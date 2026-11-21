package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Optional;

/**
 * One building, as facilities numbers it.
 *
 * @param code the code facilities gives it, trimmed and in capitals: {@code MOO}
 * @param name what everybody calls it
 * @param yearBuilt the year it went up, when anybody knows
 */
public record Building(String code, String name, Optional<Integer> yearBuilt) {

    /** The earliest year a building on campus can have gone up. */
    public static final int EARLIEST_YEAR = 1850;

    /** The latest year a building can be said to go up. */
    public static final int LATEST_YEAR = 2100;

    /**
     * A building, refusing a code that is blank, padded or not in capitals, and a year that is not
     * plausible.
     *
     * @throws InputError for a bad code or an implausible year
     */
    public Building {
        if (Text.isBlank(code)) {
            throw new InputError("a building needs a code");
        }
        if (!code.equals(Text.upper(Text.strip(code)))) {
            throw new InputError("building codes are upper case", Facts.of("code", code));
        }
        if (yearBuilt.isPresent() && (yearBuilt.get() < EARLIEST_YEAR || yearBuilt.get() > LATEST_YEAR)) {
            throw new InputError("that is not a year a building went up", Facts.of("year", yearBuilt.get()));
        }
    }

    /** A building nobody has dated. */
    public Building(String code, String name) {
        this(code, name, Optional.empty());
    }

    /** A building that went up in {@code yearBuilt}. */
    public Building(String code, String name, int yearBuilt) {
        this(code, name, Optional.of(yearBuilt));
    }
}
