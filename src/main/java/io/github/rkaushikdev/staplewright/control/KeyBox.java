package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * One sealed box, on one building, with one key in it.
 *
 * <p>Every building on a campus has a box by the main entrance with a key in it that the fire
 * service can reach without waiting for anybody. The box is sealed with a numbered tag, and the
 * seal is the whole of the security model: nobody watches the box, so the only evidence that it has
 * been opened is a seal number that no longer matches the one on file.
 *
 * @param number the box number
 * @param building the building it is bolted to
 * @param opening the opening it is beside
 * @param symbol the key inside
 * @param seal the number on the seal, which every sealed box has
 * @param sealedOn the day the current seal went on
 * @param state where the box stands
 * @param checkedOn the last day somebody looked at it
 * @param openedOn the day the seal was found broken or reported broken
 * @param openedBy who broke it, when anybody knows
 * @param note what was written the last time the box changed
 */
public record KeyBox(
        String number,
        String building,
        String opening,
        KeySymbol symbol,
        String seal,
        LocalDate sealedOn,
        BoxState state,
        Optional<LocalDate> checkedOn,
        Optional<LocalDate> openedOn,
        Optional<OpenedBy> openedBy,
        String note) {

    public KeyBox {
        if (Text.isBlank(number)) {
            throw new InputError("a box needs a number");
        }
        if (Text.isBlank(seal) && state == BoxState.SEALED) {
            throw new InputError("a sealed box has a seal number", Facts.of("box", number));
        }
    }

    /** A box just sealed, never checked and never opened. */
    public KeyBox(String number, String building, String opening, KeySymbol symbol, String seal, LocalDate sealedOn) {
        this(
                number,
                building,
                opening,
                symbol,
                seal,
                sealedOn,
                BoxState.SEALED,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "");
    }

    /** Whether the box is still on a wall. */
    public boolean isLive() {
        return state.isLive();
    }

    /** Whether there is still a key in the box. */
    public boolean holdsAKey() {
        return state.holdsAKey();
    }

    /** The most recent day anybody looked at it. */
    public LocalDate lastSeen() {
        return checkedOn.orElse(sealedOn);
    }

    /** Whether a box still on the wall has gone a full check window without anybody looking. */
    public boolean dueForCheck(LocalDate today, BoxPolicy policy) {
        if (state == BoxState.WITHDRAWN) {
            return false;
        }
        return ChronoUnit.DAYS.between(lastSeen(), today) >= policy.checkDays();
    }

    /** Open longer than the shop said it would take to get back out. */
    public boolean overdueReseal(LocalDate today, BoxPolicy policy) {
        if (state != BoxState.OPENED || openedOn.isEmpty()) {
            return false;
        }
        return ChronoUnit.DAYS.between(openedOn.get(), today) > policy.resealDays();
    }

    /** One line for the register: {@code KB-01 MOO MOO-100 AA1 sealed}. */
    public String describe() {
        return number + " " + building + " " + opening + " " + symbol.render() + " " + state.value();
    }

    /** A builder starting from this box, for a copy with some fields changed. */
    public Builder toBuilder() {
        return new Builder(this);
    }

    /** A box put together a field at a time, checked once when it is built. */
    public static final class Builder {
        private String number;
        private String building;
        private String opening;
        private KeySymbol symbol;
        private String seal;
        private LocalDate sealedOn;
        private BoxState state;
        private Optional<LocalDate> checkedOn;
        private Optional<LocalDate> openedOn;
        private Optional<OpenedBy> openedBy;
        private String note;

        private Builder(KeyBox from) {
            number = from.number;
            building = from.building;
            opening = from.opening;
            symbol = from.symbol;
            seal = from.seal;
            sealedOn = from.sealedOn;
            state = from.state;
            checkedOn = from.checkedOn;
            openedOn = from.openedOn;
            openedBy = from.openedBy;
            note = from.note;
        }

        public Builder number(String value) {
            number = value;
            return this;
        }

        public Builder building(String value) {
            building = value;
            return this;
        }

        public Builder opening(String value) {
            opening = value;
            return this;
        }

        public Builder symbol(KeySymbol value) {
            symbol = value;
            return this;
        }

        public Builder seal(String value) {
            seal = value;
            return this;
        }

        public Builder sealedOn(LocalDate value) {
            sealedOn = value;
            return this;
        }

        public Builder state(BoxState value) {
            state = value;
            return this;
        }

        public Builder checkedOn(LocalDate value) {
            checkedOn = Optional.of(value);
            return this;
        }

        public Builder checkedOn(Optional<LocalDate> value) {
            checkedOn = value;
            return this;
        }

        public Builder openedOn(LocalDate value) {
            openedOn = Optional.of(value);
            return this;
        }

        public Builder openedOn(Optional<LocalDate> value) {
            openedOn = value;
            return this;
        }

        public Builder openedBy(OpenedBy value) {
            openedBy = Optional.of(value);
            return this;
        }

        public Builder openedBy(Optional<OpenedBy> value) {
            openedBy = value;
            return this;
        }

        public Builder note(String value) {
            note = value;
            return this;
        }

        public KeyBox build() {
            return new KeyBox(
                    number, building, opening, symbol, seal, sealedOn, state, checkedOn, openedOn, openedBy, note);
        }
    }
}
