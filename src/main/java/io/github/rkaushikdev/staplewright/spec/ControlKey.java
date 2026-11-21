package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One control key, and the cores it can pull.
 *
 * <p>A control key does not open a door. It pulls the core out of the housing, so the person
 * holding one can take the lock away and put their own in. That is why control keys are not on the
 * master key system at all: a control key is not above a grand master in the hierarchy, it is beside
 * it, and a shop that files it as another master eventually gives one to somebody because they were
 * already trusted with a building master.
 *
 * @param code the control key's own code
 * @param section the keyway section it is cut on
 * @param format the core format it pulls
 * @param bitting what it is cut to
 * @param issuedOn the day it was cut
 * @param holder who has it, blank when nobody signed for it
 * @param withdrawnOn the day it was taken out of service
 */
public record ControlKey(
        String code,
        String section,
        CoreFormat format,
        Bitting bitting,
        LocalDate issuedOn,
        String holder,
        Optional<LocalDate> withdrawnOn) {

    /** What a control key nobody signed for is held by. */
    static final String NOBODY = "nobody named";

    public ControlKey {
        if (Text.isBlank(code)) {
            throw new InputError("a control key needs a code");
        }
        if (Text.isBlank(section)) {
            throw new InputError("a control key is cut on a section", Facts.of("code", code));
        }
    }

    /** A live control key nobody has signed for. */
    public ControlKey(String code, String section, CoreFormat format, Bitting bitting, LocalDate issuedOn) {
        this(code, section, format, bitting, issuedOn, "", Optional.empty());
    }

    /** A live control key in somebody's hands. */
    public ControlKey(
            String code, String section, CoreFormat format, Bitting bitting, LocalDate issuedOn, String holder) {
        this(code, section, format, bitting, issuedOn, holder, Optional.empty());
    }

    public boolean isLive() {
        return withdrawnOn.isEmpty();
    }

    /** Whether this key takes that core out of its housing. */
    public boolean canPull(Core core) {
        return isLive() && core.format() == format && core.controlCode().equals(code);
    }

    /** One line: {@code CK-1 small format, A2 held by nobody named, live}. */
    public String describe() {
        String held = holder.isEmpty() ? NOBODY : holder;
        String state = withdrawnOn.map(day -> "withdrawn " + day).orElse("live");
        return code + " " + format.value() + " held by " + held + ", " + state;
    }

    /** The same key in somebody else's hands. */
    ControlKey withHolder(String value) {
        return new ControlKey(code, section, format, bitting, issuedOn, value, withdrawnOn);
    }

    /** The same key, out of service from {@code day}. */
    ControlKey withWithdrawnOn(LocalDate day) {
        return new ControlKey(code, section, format, bitting, issuedOn, holder, Optional.of(day));
    }
}
