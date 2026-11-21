package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One person the shop will hand metal to.
 *
 * <p>Nobody holds a key. A person holds a key, and the difference matters the morning that person
 * stops working here. So a keyholder carries a standing and a separation date, and a keyholder who
 * has separated cannot be issued anything, which is the single most useful rule in key control.
 *
 * <p>Contractors and students are their own problem. They are legitimate keyholders with a date
 * the work ends, and after that date they are treated as separated whether or not anybody
 * remembered to close the record. The shop learned that one the hard way in 2016 and the rule has
 * been in the manual ever since.
 *
 * @param identifier the campus identifier the counter looks people up by
 * @param name the name the counter reads out
 * @param affiliation what kind of person this is
 * @param department the department that answers for their keys
 * @param standing where they sit with the shop today
 * @param endsOn the day a temporary record runs out, set on every contractor and student
 * @param separatedOn the day somebody left, set on every separated record
 */
public record Keyholder(
        String identifier,
        String name,
        Affiliation affiliation,
        String department,
        Standing standing,
        Optional<LocalDate> endsOn,
        Optional<LocalDate> separatedOn) {

    public Keyholder {
        if (Text.isBlank(identifier)) {
            throw new InputError("a keyholder needs an identifier");
        }
        if (Text.isBlank(name)) {
            throw new InputError("a keyholder needs a name", Facts.of("identifier", identifier));
        }
        if (affiliation.isTemporary() && endsOn.isEmpty()) {
            throw new ConflictError(
                    "a temporary keyholder has a date the work ends",
                    Facts.of("identifier", identifier, "affiliation", affiliation.value()));
        }
        if (standing == Standing.SEPARATED && separatedOn.isEmpty()) {
            throw new ConflictError("a separated keyholder has a date", Facts.of("identifier", identifier));
        }
    }

    /** An active keyholder on a permanent record. */
    public Keyholder(String identifier, String name, Affiliation affiliation, String department) {
        this(identifier, name, affiliation, department, Standing.ACTIVE, Optional.empty(), Optional.empty());
    }

    /** An active keyholder whose record runs out on {@code endsOn}. */
    public Keyholder(String identifier, String name, Affiliation affiliation, String department, LocalDate endsOn) {
        this(identifier, name, affiliation, department, Standing.ACTIVE, Optional.of(endsOn), Optional.empty());
    }

    /** Whether a temporary record has run past its end date. The end date itself is still good. */
    public boolean lapsedOn(LocalDate today) {
        return endsOn.isPresent() && today.isAfter(endsOn.get());
    }

    /** Whether the counter may hand this person a key today. */
    public boolean mayHoldKeys(LocalDate today) {
        if (standing != Standing.ACTIVE) {
            return false;
        }
        return !lapsedOn(today);
    }

    /** The name with the identifier after it, the way the counter reads somebody out. */
    public String label() {
        return name + " (" + identifier + ")";
    }

    /** The same person, gone as of {@code when}. */
    Keyholder separated(LocalDate when) {
        return new Keyholder(identifier, name, affiliation, department, Standing.SEPARATED, endsOn, Optional.of(when));
    }

    /** The same person, with their keys stopped and their record still open. */
    Keyholder suspended() {
        return new Keyholder(identifier, name, affiliation, department, Standing.SUSPENDED, endsOn, separatedOn);
    }

    /** The same person, active again and with no separation on the record. */
    Keyholder reinstated() {
        return new Keyholder(identifier, name, affiliation, department, Standing.ACTIVE, endsOn, Optional.empty());
    }

    /** The same person, with the record now running out on {@code until}. */
    Keyholder extendedTo(LocalDate until) {
        return new Keyholder(identifier, name, affiliation, department, standing, Optional.of(until), separatedOn);
    }
}
