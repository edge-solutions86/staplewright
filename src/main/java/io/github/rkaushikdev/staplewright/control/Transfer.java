package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One key moving from one pair of hands to another without it coming back to the counter.
 *
 * <p>This is the transaction the shop pretends does not happen. A technician leaves, hands their
 * building key to whoever is covering, and nobody tells the lock shop for eight months. So the
 * handover gets a record of its own, with two signatures on it and a window it has to be confirmed
 * inside. Until the person receiving it confirms, the key is still out to the person who had it, and
 * the report the audit reads says so.
 *
 * @param number the handover number
 * @param tag the key being handed over
 * @param symbol what it was cut to
 * @param passedBy who says they handed it over
 * @param passedTo who is supposed to have it now
 * @param claimedOn the day the handover was claimed
 * @param state where the handover has got to
 * @param settledOn the day it was confirmed, refused, cancelled or lapsed
 * @param note what was written when it was settled
 */
public record Transfer(
        String number,
        String tag,
        String symbol,
        String passedBy,
        String passedTo,
        LocalDate claimedOn,
        TransferState state,
        Optional<LocalDate> settledOn,
        String note) {

    public Transfer {
        if (Text.isBlank(number)) {
            throw new InputError("a transfer needs a number");
        }
        if (Text.isBlank(tag)) {
            throw new InputError("a transfer needs a key tag", Facts.of("transfer", number));
        }
        if (passedBy.equals(passedTo)) {
            throw new ConflictError(
                    "a key cannot be handed to the person who has it",
                    Facts.of("transfer", number, "keyholder", passedBy));
        }
    }

    /** A handover just claimed, not yet settled. */
    public Transfer(String number, String tag, String symbol, String passedBy, String passedTo, LocalDate claimedOn) {
        this(number, tag, symbol, passedBy, passedTo, claimedOn, TransferState.CLAIMED, Optional.empty(), "");
    }

    /** Whether the handover is still waiting to be settled. */
    public boolean isOpen() {
        return state.isOpen();
    }

    /** Whether the ledger should show the key with the new person. */
    public boolean moved() {
        return state == TransferState.CONFIRMED;
    }

    /** Who the shop believes has the key on a given day: the new person only from the confirmation on. */
    public String holderOn(LocalDate day) {
        if (state == TransferState.CONFIRMED && settledOn.isPresent()) {
            return day.isBefore(settledOn.get()) ? passedBy : passedTo;
        }
        return passedBy;
    }

    /** The last day the claim may be confirmed under this policy. */
    public LocalDate dueOn(TransferPolicy policy) {
        return claimedOn.plusDays(policy.confirmDays());
    }

    /** Whether the claim is still open past its window. */
    public boolean isLate(LocalDate today, TransferPolicy policy) {
        return isOpen() && today.isAfter(dueOn(policy));
    }

    /** One line for the book: {@code T-0001 41207 AA1 kmaki to rlaakso claimed}. */
    public String describe() {
        return number + " " + tag + " " + symbol + " " + passedBy + " to " + passedTo + " " + state.value();
    }

    /** A builder starting from this handover, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A handover put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String number;
        private String tag;
        private String symbol;
        private String passedBy;
        private String passedTo;
        private LocalDate claimedOn;
        private TransferState state;
        private Optional<LocalDate> settledOn;
        private String note;

        private Builder(Transfer from) {
            number = from.number;
            tag = from.tag;
            symbol = from.symbol;
            passedBy = from.passedBy;
            passedTo = from.passedTo;
            claimedOn = from.claimedOn;
            state = from.state;
            settledOn = from.settledOn;
            note = from.note;
        }

        public Builder number(String value) {
            number = value;
            return this;
        }

        public Builder tag(String value) {
            tag = value;
            return this;
        }

        public Builder symbol(String value) {
            symbol = value;
            return this;
        }

        public Builder passedBy(String value) {
            passedBy = value;
            return this;
        }

        public Builder passedTo(String value) {
            passedTo = value;
            return this;
        }

        public Builder claimedOn(LocalDate value) {
            claimedOn = value;
            return this;
        }

        public Builder state(TransferState value) {
            state = value;
            return this;
        }

        public Builder settledOn(LocalDate value) {
            settledOn = Optional.of(value);
            return this;
        }

        public Builder settledOn(Optional<LocalDate> value) {
            settledOn = value;
            return this;
        }

        public Builder note(String value) {
            note = value;
            return this;
        }

        public Transfer build() {
            return new Transfer(number, tag, symbol, passedBy, passedTo, claimedOn, state, settledOn, note);
        }
    }
}
