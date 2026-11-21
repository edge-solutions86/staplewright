package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.Transfer;
import io.github.rkaushikdev.staplewright.control.TransferBook;
import io.github.rkaushikdev.staplewright.control.TransferPolicy;
import io.github.rkaushikdev.staplewright.control.TransferState;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Handovers to rows and back, and the one join that matters.
 *
 * <p>{@link #holderNow} is the reason this class is more than two conversions. Who has a key is the
 * key row plus every confirmed handover on top of it, and every caller that worked that out for
 * itself got it slightly different: one of them took the last handover by claim date rather than by
 * settle date, which put a key back with somebody who had already passed it on.
 */
public final class TransferMappers {

    private TransferMappers() {}

    /**
     * A key whose ledger holder is not the person who has it.
     *
     * @param tag the key
     * @param ledgerHolder who the ledger says has it
     * @param holder who actually has it, handovers included
     */
    public record Stray(String tag, String ledgerHolder, String holder) {}

    private static final Comparator<Stray> STRAY_ORDER =
            Comparator.comparing(Stray::tag).thenComparing(Stray::ledgerHolder).thenComparing(Stray::holder);

    /** A new handover row, its state stored as the state's value. */
    public static TransferRow transferToRow(Transfer transfer) {
        TransferRow row = new TransferRow(
                transfer.number(),
                transfer.tag(),
                transfer.symbol(),
                transfer.passedBy(),
                transfer.passedTo(),
                transfer.claimedOn());
        row.setState(transfer.state().value());
        row.setSettledOn(transfer.settledOn().orElse(null));
        row.setNote(transfer.note());
        return row;
    }

    /**
     * The handover a row holds.
     *
     * @throws InputError for a state nobody recognises
     */
    public static Transfer transferFromRow(TransferRow row) {
        return new Transfer(
                row.getNumber(),
                row.getTag(),
                row.getSymbol(),
                row.getPassedBy(),
                row.getPassedTo(),
                row.getClaimedOn(),
                state(row.getState()),
                row.getSettledOn(),
                row.getNote());
    }

    private static TransferState state(String value) {
        return TransferState.find(value)
                .orElseThrow(() -> new InputError("that is not a state a handover reaches", Facts.of("state", value)));
    }

    /** Move a stored row to match a handover the book has just settled. */
    public static TransferRow applyToRow(TransferRow row, Transfer transfer) {
        row.setState(transfer.state().value());
        row.setSettledOn(transfer.settledOn().orElse(null));
        row.setNote(transfer.note());
        return row;
    }

    /** The handover book under the standing policy, each handover in the state it reached. */
    public static TransferBook bookFromRows(List<TransferRow> rows) {
        return fill(new TransferBook(), rows);
    }

    /** The handover book under {@code policy}, each handover in the state it reached. */
    public static TransferBook bookFromRows(List<TransferRow> rows, TransferPolicy policy) {
        return fill(new TransferBook(policy), rows);
    }

    private static TransferBook fill(TransferBook book, List<TransferRow> rows) {
        for (TransferRow row : rows) {
            book.load(transferFromRow(row));
        }
        return book;
    }

    /** Every confirmed handover of one key, in the order they settled. */
    public static List<TransferRow> confirmedMoves(List<TransferRow> rows, String tag) {
        return rows.stream()
                .filter(row -> row.getTag().equals(tag)
                        && row.moved()
                        && row.getSettledOn().isPresent())
                .sorted(Comparator.comparing(
                                (TransferRow row) -> row.getSettledOn().orElseThrow())
                        .thenComparing(TransferRow::getNumber))
                .toList();
    }

    /**
     * Who has this key today, which is the key row plus the handovers on it.
     *
     * <p>Sorted by the day the handover settled and not the day it was claimed. Two handovers
     * claimed in one week can settle in the other order, and the one that settled last is the one
     * that decides where the key is.
     */
    public static String holderNow(IssuedKeyRow key, List<TransferRow> rows, LocalDate today) {
        String holder = key.getKeyholder();
        for (TransferRow row : confirmedMoves(rows, key.getTag())) {
            if (row.getSettledOn().map(settled -> !settled.isAfter(today)).orElse(false)) {
                holder = row.getPassedTo();
            }
        }
        return holder;
    }

    /** Every tag this person actually has today, handovers included, sorted. */
    public static List<String> holdingsOf(
            List<IssuedKeyRow> keys, List<TransferRow> rows, String identifier, LocalDate today) {
        return keys.stream()
                .filter(key -> holderNow(key, rows, today).equals(identifier))
                .map(IssuedKeyRow::getTag)
                .sorted()
                .toList();
    }

    /**
     * Keys whose ledger holder is not the person who has them, sorted.
     *
     * <p>This is the list that gets read out at an audit, and every line on it is a key the shop
     * would have asked the wrong person to produce.
     */
    public static List<Stray> strays(List<IssuedKeyRow> keys, List<TransferRow> rows, LocalDate today) {
        List<Stray> found = new ArrayList<>();
        for (IssuedKeyRow key : keys) {
            String now = holderNow(key, rows, today);
            if (!now.equals(key.getKeyholder())) {
                found.add(new Stray(key.getTag(), key.getKeyholder(), now));
            }
        }
        found.sort(STRAY_ORDER);
        return List.copyOf(found);
    }
}
