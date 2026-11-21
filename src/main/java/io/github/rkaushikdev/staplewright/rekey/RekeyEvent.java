package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * One time a cylinder came apart, and what it came back as.
 *
 * @param serial the cylinder
 * @param happenedOn the day of the repin
 * @param toSymbols the symbols it was pinned to operate, each once
 * @param orderNumber the work order it was done under, when there was one
 * @param technician who did it
 * @param reason why
 */
public record RekeyEvent(
        String serial,
        LocalDate happenedOn,
        List<KeySymbol> toSymbols,
        String orderNumber,
        String technician,
        String reason) {

    /**
     * An event, refusing one with no cylinder, no symbols or a symbol twice.
     *
     * @throws InputError for a blank serial
     * @throws ConflictError for no symbols or a repeated one
     */
    public RekeyEvent {
        if (Text.isBlank(serial)) {
            throw new InputError("a rekey event names a cylinder");
        }
        toSymbols = List.copyOf(toSymbols);
        if (toSymbols.isEmpty()) {
            throw new ConflictError("a cylinder comes back keyed to something", Facts.of("serial", serial));
        }
        if (new HashSet<>(toSymbols).size() != toSymbols.size()) {
            throw new ConflictError("a symbol is on the cylinder twice", Facts.of("serial", serial));
        }
    }

    /** A repin with no order, technician or reason written down. */
    public RekeyEvent(String serial, LocalDate happenedOn, List<KeySymbol> toSymbols) {
        this(serial, happenedOn, toSymbols, "", "", "");
    }

    /** One less than the symbols, which is the floor on the master wafers the cylinder carries. */
    public int wafers() {
        return Math.max(toSymbols.size() - 1, 0);
    }

    /** One line of the history: {@code 2020-03-04  C-1001  AA1, AA}. */
    public String describe() {
        String symbols = toSymbols.stream().map(KeySymbol::render).collect(Collectors.joining(", "));
        return happenedOn + "  " + serial + "  " + symbols;
    }
}
