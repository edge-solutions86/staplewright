package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * One stamped key and whoever signed for it.
 *
 * <p>Every key that leaves the counter is stamped with a tag number and written down against a
 * person. The tag is the identity, not the symbol, because five people hold a key cut AA1 and only
 * one of them has tag 41207. Deposits are held in whole cents because they are money and because
 * the refund has to come back to the exact figure that was taken.
 *
 * @param tag the number stamped on the key
 * @param symbol the symbol it was cut to
 * @param keyholder the identifier of whoever signed for it
 * @param issuedOn the day it went out, or the day it last changed hands
 * @param dueOn the day it is due back, for a key lent for a while
 * @param depositCents the deposit taken on it, in whole cents
 * @param state where it is now
 * @param closedOn the day it stopped being out
 * @param note what the counter wrote when it stopped being out
 */
public record IssuedKey(
        String tag,
        String symbol,
        String keyholder,
        LocalDate issuedOn,
        Optional<LocalDate> dueOn,
        int depositCents,
        KeyState state,
        Optional<LocalDate> closedOn,
        String note) {

    public IssuedKey {
        if (Text.isBlank(tag)) {
            throw new InputError("a key needs a tag number");
        }
        if (depositCents < 0) {
            throw new InputError("a deposit is not negative", Facts.of("tag", tag));
        }
        if (dueOn.isPresent() && dueOn.get().isBefore(issuedOn)) {
            throw new ConflictError("a key is not due back before it goes out", Facts.of("tag", tag));
        }
    }

    /** A key just over the counter, with no due date and no deposit. */
    public IssuedKey(String tag, String symbol, String keyholder, LocalDate issuedOn) {
        this(tag, symbol, keyholder, issuedOn, Optional.empty(), 0);
    }

    /** A key just over the counter, out until it comes back. */
    public IssuedKey(
            String tag,
            String symbol,
            String keyholder,
            LocalDate issuedOn,
            Optional<LocalDate> dueOn,
            int depositCents) {
        this(tag, symbol, keyholder, issuedOn, dueOn, depositCents, KeyState.OUT, Optional.empty(), "");
    }

    public boolean isOut() {
        return state == KeyState.OUT;
    }

    /** Whether the key was reported lost or stolen. */
    public boolean isGone() {
        return state.isGone();
    }

    /** Whether the key is still out past the day it was due back. A key with no due date never is. */
    public boolean overdueOn(LocalDate today) {
        return isOut() && dueOn.isPresent() && today.isAfter(dueOn.get());
    }

    /** How many days past its due date the key is, or nought when it is not overdue. */
    public int daysLate(LocalDate today) {
        if (!overdueOn(today)) {
            return 0;
        }
        return Math.toIntExact(ChronoUnit.DAYS.between(dueOn.get(), today));
    }

    /** One line for the counter: {@code tag 41207 cut AA1, kmaki, out}. */
    public String describe() {
        return "tag " + tag + " cut " + symbol + ", " + keyholder + ", " + state.value();
    }

    /** The same key, no longer out as of {@code when}. */
    IssuedKey closedAs(KeyState closed, LocalDate when, String closingNote) {
        return new IssuedKey(
                tag, symbol, keyholder, issuedOn, dueOn, depositCents, closed, Optional.of(when), closingNote);
    }

    /** The same key, now with somebody else since {@code when}. */
    IssuedKey handedTo(String newHolder, LocalDate when) {
        return new IssuedKey(tag, symbol, newHolder, when, dueOn, depositCents, state, closedOn, note);
    }

    /** The same key, due back on {@code until} instead. */
    IssuedKey dueBy(LocalDate until) {
        return new IssuedKey(tag, symbol, keyholder, issuedOn, Optional.of(until), depositCents, state, closedOn, note);
    }
}
