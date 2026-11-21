package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.CallState;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.Lockout;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.persistence.Numbering;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * The lockout telephone log against the database, one call and one small write at a time.
 *
 * <p>A lockout is logged on a telephone while the caller is still talking, so the service takes one
 * step of a call at a time and refuses a step clearly rather than batching anything. Each call takes
 * the next number in its own series, and each step after the first has to find the call in the state
 * that step follows from: nobody lets a person in before somebody has attended.
 */
public final class LockoutService {

    /** The numbering series lockout calls are numbered in. */
    public static final String LOCKOUT_SERIES = "lockouts";

    private final Session session;
    private final Numbering numbering;

    public LockoutService(Session session) {
        this.session = session;
        this.numbering = new Numbering(session);
    }

    public Session session() {
        return session;
    }

    /**
     * Open the call counter, numbering calls {@code L-00001} onwards. Once, when the shop is set up.
     *
     * @throws ConflictError when the series is already open
     */
    public void openSeries() {
        numbering.openSeries(LOCKOUT_SERIES, "L-", 5, 1);
    }

    /**
     * Log a call about a door, naming the person locked out.
     *
     * @throws NotFoundError for a door nobody has
     * @throws InputError when the call names nobody
     */
    public LockoutRow log(String opening, String keyholder, LocalDateTime when) {
        if (session.get(OpeningRow.class, opening).isEmpty()) {
            throw new NotFoundError("no such opening", Facts.of("opening", opening));
        }
        if (Text.isBlank(keyholder)) {
            throw new InputError("a lockout call names somebody", Facts.of("opening", opening));
        }
        String number = numbering.next(LOCKOUT_SERIES);
        LockoutRow row = FacilitiesMappers.lockoutToRow(new Lockout(number, opening, keyholder, when));
        session.add(row);
        session.flush();
        return row;
    }

    /**
     * Record who went to a logged call, and when.
     *
     * @throws InputError when nobody is named
     * @throws ConflictError when the attendance comes before the call, or the call is past logged
     */
    public LockoutRow attend(String number, String technician, LocalDateTime when) {
        LockoutRow row = mustBe(number, Set.of(CallState.LOGGED));
        if (Text.isBlank(technician)) {
            throw new InputError("somebody attends a call", Facts.of("number", number));
        }
        if (when.isBefore(row.getCalledAt())) {
            throw new ConflictError("nobody attended before the call", Facts.of("number", number));
        }
        row.setState(CallState.ATTENDED.value());
        row.setTechnician(technician);
        row.setAttendedAt(when);
        session.flush();
        return row;
    }

    /**
     * Let the person in on an attended call, recording how they were identified.
     *
     * @throws ConflictError when they were not identified, or nobody has attended
     */
    public LockoutRow openDoor(String number, Identification how, LocalDateTime when) {
        LockoutRow row = mustBe(number, Set.of(CallState.ATTENDED));
        if (how == Identification.NONE) {
            throw new ConflictError("an unidentified person does not get let in", Facts.of("number", number));
        }
        row.setState(CallState.OPENED.value());
        row.setIdentifiedBy(how.value());
        row.setClosedAt(when);
        session.flush();
        return row;
    }

    /**
     * Close an attended call without opening the door, saying why.
     *
     * @throws InputError when the refusal says nothing
     */
    public LockoutRow refuse(String number, LocalDateTime when, String note) {
        LockoutRow row = mustBe(number, Set.of(CallState.ATTENDED));
        if (Text.isBlank(note)) {
            throw new InputError("a refusal says why", Facts.of("number", number));
        }
        row.setState(CallState.REFUSED.value());
        row.setClosedAt(when);
        row.setNote(note);
        session.flush();
        return row;
    }

    /** Every call, read back in the order it came in. */
    public LockoutLog logBook() {
        return FacilitiesMappers.logFromRows(session.all(LockoutRow.class));
    }

    private LockoutRow mustBe(String number, Set<CallState> states) {
        LockoutRow row = session.get(LockoutRow.class, number)
                .orElseThrow(() -> new NotFoundError("no such lockout call", Facts.of("number", number)));
        Lifecycle.requireState(
                CallState.fromValue(row.getState()),
                states,
                "the call is not in a state for that",
                Facts.of("number", number));
        return row;
    }
}
