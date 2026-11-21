package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Optional;

/**
 * One line of the keying schedule.
 *
 * @param symbol the key's place in the hierarchy
 * @param section the keyway section it is cut on
 * @param bitting what it is cut to, empty while the symbol is still open
 * @param note whatever the shop wrote beside it
 */
public record ScheduleEntry(KeySymbol symbol, String section, Optional<Bitting> bitting, String note) {

    /** An open line with no note, which is how a symbol first goes on the schedule. */
    public ScheduleEntry(KeySymbol symbol, String section) {
        this(symbol, section, Optional.empty(), "");
    }

    /** Whether the symbol has been cut. */
    public boolean isCut() {
        return bitting.isPresent();
    }

    /** The same line cut to this bitting. */
    public ScheduleEntry withBitting(Bitting cut) {
        return new ScheduleEntry(symbol, section, Optional.of(cut), note);
    }

    /** The same line with its cuts taken off. */
    public ScheduleEntry withoutBitting() {
        return new ScheduleEntry(symbol, section, Optional.empty(), note);
    }

    /** The line as the printed schedule shows it: symbol, section, then the cuts or the word open. */
    public String render() {
        String cut = bitting.map(Bitting::render).orElse("open");
        return Text.left(symbol.render(), 8) + Text.left(section, 5) + cut;
    }
}
