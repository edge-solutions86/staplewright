package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;

/**
 * Somebody signed for more copies of one symbol than policy allows.
 *
 * @param symbol the symbol, as the shop writes it
 * @param ceiling how many may be out from now on
 * @param signedBy who signed for it
 * @param signedOn the day they signed
 * @param reason why, which is not optional
 */
public record Authorisation(String symbol, int ceiling, String signedBy, LocalDate signedOn, String reason) {

    public Authorisation {
        if (ceiling < 1) {
            throw new InputError("an authorisation raises a ceiling", Facts.of("symbol", symbol));
        }
        if (Text.isBlank(reason)) {
            throw new InputError("an authorisation says why", Facts.of("symbol", symbol));
        }
    }
}
