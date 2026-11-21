package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;

/**
 * One missing key, written down the day it is reported.
 *
 * <p>A theft is always written up: an incident with the cause stolen and no note is refused,
 * because the note is what the police report and the insurer will both ask for.
 *
 * @param number the incident number the shop files it under
 * @param tag the tag stamped on the missing key
 * @param symbol the key symbol it was cut to, as the shop writes it
 * @param cause how it left
 * @param reportedOn the day somebody said so
 * @param keyholder who had it, when that is known
 * @param note what was written about it
 */
public record Incident(
        String number, String tag, String symbol, Cause cause, LocalDate reportedOn, String keyholder, String note) {

    /**
     * An incident, refusing one with no number and a theft with nothing written about it.
     *
     * @throws InputError for a blank number or a theft with a blank note
     */
    public Incident {
        if (Text.isBlank(number)) {
            throw new InputError("an incident needs a number");
        }
        if (cause == Cause.STOLEN && Text.isBlank(note)) {
            throw new InputError("a theft is written up", Facts.of("number", number));
        }
    }

    /** An incident with no keyholder named and nothing written about it. */
    public Incident(String number, String tag, String symbol, Cause cause, LocalDate reportedOn) {
        this(number, tag, symbol, cause, reportedOn, "", "");
    }

    /** An incident with the keyholder named and nothing written about it. */
    public Incident(String number, String tag, String symbol, Cause cause, LocalDate reportedOn, String keyholder) {
        this(number, tag, symbol, cause, reportedOn, keyholder, "");
    }

    /** The symbol read as a key symbol. */
    public KeySymbol keySymbol() {
        return KeySymbol.parse(symbol);
    }
}
