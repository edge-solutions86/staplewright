package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One person asking for one key.
 *
 * @param number the request number the department quotes
 * @param keyholder the identifier of the person who wants the key
 * @param symbol the symbol they are asking for
 * @param building the building the key is for, which the signature has to reach
 * @param reason why they want it, which every request says
 * @param raisedOn the day it was raised
 * @param state where it has got to
 * @param isMaster whether the key masters, which needs a signature allowed to approve masters
 * @param decidedOn the day it was decided or withdrawn
 * @param decidedBy the identifier of whoever decided it, empty until somebody has
 * @param note what was written when it was decided or withdrawn
 */
public record KeyRequest(
        String number,
        String keyholder,
        String symbol,
        String building,
        String reason,
        LocalDate raisedOn,
        RequestState state,
        boolean isMaster,
        Optional<LocalDate> decidedOn,
        String decidedBy,
        String note) {

    public KeyRequest {
        if (Text.isBlank(number)) {
            throw new InputError("a request needs a number");
        }
        if (Text.isBlank(reason)) {
            throw new InputError("a request needs a reason", Facts.of("number", number));
        }
    }

    /** A new request for a change key, raised and not yet decided. */
    public KeyRequest(
            String number, String keyholder, String symbol, String building, String reason, LocalDate raisedOn) {
        this(
                number,
                keyholder,
                symbol,
                building,
                reason,
                raisedOn,
                RequestState.RAISED,
                false,
                Optional.empty(),
                "",
                "");
    }

    /** Whether the request is still waiting on somebody. */
    public boolean isOpen() {
        return state.isOpen();
    }

    /** Whether the request was approved or denied. A withdrawn request was not decided. */
    public boolean isDecided() {
        return state.isDecided();
    }

    /** One line for the counter: {@code R-001 kmaki wants AA1 in MOO}. */
    public String describe() {
        return number + " " + keyholder + " wants " + symbol + " in " + building;
    }

    /** A builder starting from this request, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A request put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String number;
        private String keyholder;
        private String symbol;
        private String building;
        private String reason;
        private LocalDate raisedOn;
        private RequestState state;
        private boolean isMaster;
        private Optional<LocalDate> decidedOn;
        private String decidedBy;
        private String note;

        private Builder(KeyRequest from) {
            number = from.number;
            keyholder = from.keyholder;
            symbol = from.symbol;
            building = from.building;
            reason = from.reason;
            raisedOn = from.raisedOn;
            state = from.state;
            isMaster = from.isMaster;
            decidedOn = from.decidedOn;
            decidedBy = from.decidedBy;
            note = from.note;
        }

        public Builder number(String value) {
            number = value;
            return this;
        }

        public Builder keyholder(String value) {
            keyholder = value;
            return this;
        }

        public Builder symbol(String value) {
            symbol = value;
            return this;
        }

        public Builder building(String value) {
            building = value;
            return this;
        }

        public Builder reason(String value) {
            reason = value;
            return this;
        }

        public Builder raisedOn(LocalDate value) {
            raisedOn = value;
            return this;
        }

        public Builder state(RequestState value) {
            state = value;
            return this;
        }

        public Builder isMaster(boolean value) {
            isMaster = value;
            return this;
        }

        public Builder decidedOn(LocalDate value) {
            decidedOn = Optional.of(value);
            return this;
        }

        public Builder decidedOn(Optional<LocalDate> value) {
            decidedOn = value;
            return this;
        }

        public Builder decidedBy(String value) {
            decidedBy = value;
            return this;
        }

        public Builder note(String value) {
            note = value;
            return this;
        }

        public KeyRequest build() {
            return new KeyRequest(
                    number, keyholder, symbol, building, reason, raisedOn, state, isMaster, decidedOn, decidedBy, note);
        }
    }
}
