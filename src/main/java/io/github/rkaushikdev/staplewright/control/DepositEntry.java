package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;

/**
 * One line of the deposit book. Charges add, everything else takes the money away again.
 *
 * @param tag the key the money is held against
 * @param keyholder whoever paid it
 * @param kind what the line does
 * @param cents how much it moves, always more than nothing
 * @param when the day it happened
 * @param note why, which a forfeit always says
 */
public record DepositEntry(String tag, String keyholder, EntryKind kind, int cents, LocalDate when, String note) {

    public DepositEntry {
        if (cents <= 0) {
            throw new InputError("a deposit line moves money", Facts.of("tag", tag));
        }
        if (kind == EntryKind.FORFEIT && Text.isBlank(note)) {
            throw new InputError("a forfeit says why", Facts.of("tag", tag));
        }
    }

    /** A line with nothing written against it. */
    public DepositEntry(String tag, String keyholder, EntryKind kind, int cents, LocalDate when) {
        this(tag, keyholder, kind, cents, when, "");
    }

    /** What the line does to the money held: a charge adds, anything else takes away. */
    public int signedCents() {
        return kind == EntryKind.CHARGE ? cents : -cents;
    }
}
