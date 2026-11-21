package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.BoxRegister;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Sealed key boxes to rows and back, with the seal history alongside.
 *
 * <p>The awkward part is that the domain has one object and the database has two tables. A box in
 * the register knows its current seal and nothing about the ones before it, because the register is
 * what a walk round holds and a walk round does not care what the seal was in 2019.
 *
 * <p>So the history is built here rather than in the register. Reading is the easy direction: the
 * current box row is enough. Writing needs both, and the caller gets the seal row it has to close
 * or open, because only the caller knows which day it is doing that on.
 */
public final class EscrowMappers {

    /** Oldest seal first, and among seals put on the same day the one written first. */
    private static final Comparator<SealRow> OLDEST_FIRST = Comparator.comparing(SealRow::getSealedOn)
            .thenComparingInt(row -> Objects.requireNonNullElse(row.getId(), 0));

    private EscrowMappers() {}

    /** A new box row, its state and opener stored as their values; a box nobody opened stores no opener. */
    public static KeyBoxRow boxToRow(KeyBox box) {
        KeyBoxRow row =
                new KeyBoxRow(box.number(), box.building(), box.opening(), box.symbol(), box.seal(), box.sealedOn());
        row.setState(box.state().value());
        row.setCheckedOn(box.checkedOn().orElse(null));
        row.setOpenedOn(box.openedOn().orElse(null));
        row.setOpenedBy(opener(box));
        row.setNote(box.note());
        return row;
    }

    /**
     * The box a row holds.
     *
     * @throws InputError for a state or an opener nobody recognises
     */
    public static KeyBox boxFromRow(KeyBoxRow row) {
        return new KeyBox(
                row.getNumber(),
                row.getBuildingCode(),
                row.getOpeningNumber(),
                row.getSymbol(),
                row.getSeal(),
                row.getSealedOn(),
                state(row.getState()),
                row.getCheckedOn(),
                row.getOpenedOn(),
                opener(row.getOpenedBy()),
                row.getNote());
    }

    private static BoxState state(String value) {
        return BoxState.find(value)
                .orElseThrow(() -> new InputError("that is not a state a box can be in", Facts.of("state", value)));
    }

    private static Optional<OpenedBy> opener(String value) {
        if (value.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(OpenedBy.find(value)
                .orElseThrow(() -> new InputError("nobody opens a box that way", Facts.of("opened_by", value))));
    }

    private static String opener(KeyBox box) {
        return box.openedBy().map(OpenedBy::value).orElse("");
    }

    /** Move an existing row to match a box the register has just changed. */
    public static KeyBoxRow applyToRow(KeyBoxRow row, KeyBox box) {
        row.setSymbol(box.symbol());
        row.setSeal(box.seal());
        row.setSealedOn(box.sealedOn());
        row.setState(box.state().value());
        row.setCheckedOn(box.checkedOn().orElse(null));
        row.setOpenedOn(box.openedOn().orElse(null));
        row.setOpenedBy(opener(box));
        row.setNote(box.note());
        return row;
    }

    /** The seal that is on the box now, as the open end of its history. */
    public static SealRow sealToRow(KeyBox box) {
        SealRow row = new SealRow(box.number(), box.seal(), box.symbol(), box.sealedOn());
        row.setNote(box.note());
        return row;
    }

    /** {@link #closeSeal(SealRow, LocalDate, String, String)} by nobody named, keeping the note. */
    public static SealRow closeSeal(SealRow row, LocalDate when) {
        return closeSeal(row, when, "", "");
    }

    /** {@link #closeSeal(SealRow, LocalDate, String, String)} keeping the note. */
    public static SealRow closeSeal(SealRow row, LocalDate when, String by) {
        return closeSeal(row, when, by, "");
    }

    /**
     * Write the day a seal came off and who took it off. A note, when there is one, replaces the
     * seal's note.
     *
     * @throws InputError for a seal already closed, or a day before the seal went on
     */
    public static SealRow closeSeal(SealRow row, LocalDate when, String by, String note) {
        if (row.getBrokenOn().isPresent()) {
            throw new InputError("that seal was already closed", Facts.of("seal", row.getSeal()));
        }
        if (when.isBefore(row.getSealedOn())) {
            throw new InputError("a seal cannot come off before it went on", Facts.of("seal", row.getSeal()));
        }
        row.setBrokenOn(when);
        row.setBrokenBy(by);
        if (!note.isEmpty()) {
            row.setNote(note);
        }
        return row;
    }

    /** The one seal with no end date, or nothing on an empty box. */
    public static Optional<SealRow> currentSeal(List<SealRow> rows) {
        return rows.stream().filter(SealRow::isCurrent).max(OLDEST_FIRST);
    }

    /** Every seal a box has carried, oldest first. */
    public static List<SealRow> sealHistory(List<SealRow> rows) {
        return rows.stream().sorted(OLDEST_FIRST).toList();
    }

    /** Which seal was on the box on a given day, for the question that matters. */
    public static Optional<SealRow> sealOn(List<SealRow> rows, LocalDate when) {
        for (SealRow row : sealHistory(rows)) {
            if (row.getSealedOn().isAfter(when)) {
                continue;
            }
            if (row.getBrokenOn().map(broken -> !broken.isBefore(when)).orElse(true)) {
                return Optional.of(row);
            }
        }
        return Optional.empty();
    }

    /** Which key was in the box on a day. This is the whole reason for the table. */
    public static Optional<KeySymbol> symbolOn(List<SealRow> rows, LocalDate when) {
        return sealOn(rows, when).map(SealRow::getSymbol);
    }

    /** The whole register under the standing policy, each box in whatever state it reached. */
    public static BoxRegister registerFromRows(List<KeyBoxRow> rows) {
        return fill(new BoxRegister(), rows);
    }

    /** The whole register under {@code policy}, each box in whatever state it reached. */
    public static BoxRegister registerFromRows(List<KeyBoxRow> rows, BoxPolicy policy) {
        return fill(new BoxRegister(policy), rows);
    }

    private static BoxRegister fill(BoxRegister register, List<KeyBoxRow> rows) {
        for (KeyBoxRow row : rows) {
            register.load(boxFromRow(row));
        }
        return register;
    }
}
