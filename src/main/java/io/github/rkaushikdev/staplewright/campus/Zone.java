package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * A group of buildings the shop treats as one thing.
 *
 * <p>The shop does not think in buildings. It thinks in the science precinct, the lower campus, the
 * two houses out on the point, because that is how the crews are assigned and how the after hours
 * call rota is drawn.
 *
 * @param code the zone's code, trimmed and in capitals: {@code ACAD}
 * @param name what the crews call it
 * @param afterHoursRota who is on call for it out of hours
 * @param note anything else worth knowing
 */
public record Zone(String code, String name, String afterHoursRota, String note) {

    /**
     * A zone, refusing a code that is blank, padded or not in capitals, and a blank name.
     *
     * @throws InputError for a bad code or a missing name
     */
    public Zone {
        if (Text.isBlank(code)) {
            throw new InputError("a zone needs a code");
        }
        if (!code.equals(Text.upper(Text.strip(code)))) {
            throw new InputError("zone codes are upper case", Facts.of("code", code));
        }
        if (Text.isBlank(name)) {
            throw new InputError("a zone needs a name", Facts.of("code", code));
        }
    }

    /** A zone with nobody on call for it yet. */
    public Zone(String code, String name) {
        this(code, name, "", "");
    }

    /** A zone and who is on call for it. */
    public Zone(String code, String name, String afterHoursRota) {
        this(code, name, afterHoursRota, "");
    }
}
