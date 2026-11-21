package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.IncidentRepository;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.Numbering;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.WorkOrderRepository;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Cause;
import io.github.rkaushikdev.staplewright.rekey.Incident;
import io.github.rkaushikdev.staplewright.rekey.RetirementReason;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.rekey.ScopePolicy;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A missing key, from the phone call to the work order.
 *
 * <p>Four things happen the morning a key is reported gone and they happen together. The key is
 * closed in the ledger, the incident is written down, the cut is retired so nobody ever issues it
 * again, and the assessment is graded so the meeting at ten has a number in front of it.
 *
 * <p>Retiring the cut is the one that has to be inside the transaction. A rolled back incident that
 * left the cut quarantined would take a perfectly good bitting out of the array for no recorded
 * reason, and nobody would ever work out why.
 */
public final class IncidentService {

    /** The series incident numbers are taken from. */
    public static final String INCIDENT_SERIES = "incidents";

    /** The series work order numbers are taken from. */
    public static final String ORDER_SERIES = "work orders";

    private final Session session;
    private final Numbering numbering;
    private final IncidentRepository incidents;
    private final WorkOrderRepository orders;
    private final ScopePolicy policy;
    private final Workspace space;

    public IncidentService(Session session) {
        this(session, ScopePolicy.DEFAULT);
    }

    public IncidentService(Session session, ScopePolicy policy) {
        this.session = session;
        this.numbering = new Numbering(session);
        this.incidents = new IncidentRepository(session);
        this.orders = new WorkOrderRepository(session);
        this.policy = policy;
        this.space = new Workspace(session);
    }

    public Session session() {
        return session;
    }

    public IncidentRepository incidents() {
        return incidents;
    }

    public WorkOrderRepository orders() {
        return orders;
    }

    public ScopePolicy policy() {
        return policy;
    }

    /**
     * The map from cylinders to symbols, built once per service.
     *
     * <p>Assessing an incident asks for this three times, and rebuilding the whole campus each time
     * made a lost grand master take four seconds to report on a campus of four hundred doors.
     */
    public CoverageMap coverage() {
        return space.coverage();
    }

    /**
     * The ledger as it stands now, which is not something to hold on to.
     *
     * <p>Reporting an incident closes a key, so a held ledger read after one would still show it
     * out. Only the coverage is held, because nothing this service does moves a cylinder.
     */
    public KeyLedger ledger() {
        return Loaders.loadLedger(session);
    }

    /** Report a key lost or gone with nothing written about it, retiring its cut. */
    public IncidentRow report(String tag, Cause cause, LocalDate when) {
        return report(tag, cause, when, "", true);
    }

    /** Report a key missing with a note, retiring its cut. */
    public IncidentRow report(String tag, Cause cause, LocalDate when, String note) {
        return report(tag, cause, when, note, true);
    }

    /**
     * Write the incident down, close the key, and retire the cut.
     *
     * @param retireCut whether the bitting the key was cut to goes into quarantine
     * @throws NotFoundError for a tag the ledger has never issued
     * @throws ConflictError for a key that is not out, or a theft with nothing written about it
     */
    public IncidentRow report(String tag, Cause cause, LocalDate when, String note, boolean retireCut) {
        IssuedKeyRow key = session.get(IssuedKeyRow.class, tag)
                .orElseThrow(() -> new NotFoundError("no such key in the ledger", Facts.of("tag", tag)));
        if (!key.isOut()) {
            throw new ConflictError("that key is not out", Facts.of("tag", tag, "state", key.getState()));
        }
        if (cause == Cause.STOLEN && Text.isBlank(note)) {
            throw new ConflictError("a theft is written up", Facts.of("tag", tag));
        }

        String number = numbering.next(INCIDENT_SERIES);
        Assessment found = Assessment.assess(
                new Incident(number, tag, key.getSymbol(), cause, when, key.getKeyholder(), note),
                coverage(),
                ledger());
        IncidentRow row = new IncidentRow(number, tag, key.getSymbol(), cause.value(), when);
        row.setKeyholder(key.getKeyholder());
        row.setSeverity(found.severity().label());
        row.setNote(note);
        incidents.add(row);
        key.setState(cause == Cause.STOLEN ? "stolen" : "lost");
        key.setClosedOn(when);
        if (retireCut) {
            retire(key.getSymbol(), when, number);
        }
        session.flush();
        return row;
    }

    /**
     * What one incident exposes, graded against the campus as it is today rather than as it was
     * the day it was reported.
     *
     * @throws NotFoundError for a number nobody reported
     */
    public Assessment assessment(String number) {
        IncidentRow row = incidents
                .find(number)
                .orElseThrow(() -> new NotFoundError("no such incident", Facts.of("number", number)));
        Incident incident = new Incident(
                row.getNumber(),
                row.getTag(),
                row.getSymbol(),
                Cause.fromValue(row.getCause()),
                row.getReportedOn(),
                row.getKeyholder(),
                row.getNote());
        return Assessment.assess(incident, coverage(), ledger());
    }

    /** What the shop would do about one incident, under this service's scope policy. */
    public ScopeDecision plan(String number) {
        return ScopeDecision.decide(assessment(number), coverage(), policy);
    }

    /** Raise the work order off an incident, with no day booked for it yet. */
    public WorkOrderRow raiseOrder(String number) {
        return raiseOrder(number, Optional.empty());
    }

    /** Raise the work order off an incident, booked for a day. */
    public WorkOrderRow raiseOrder(String number, LocalDate when) {
        return raiseOrder(number, Optional.of(when));
    }

    /**
     * Turn a scope decision into one work order with a line a cylinder.
     *
     * @throws ConflictError when the decision calls for no cylinders at all
     */
    private WorkOrderRow raiseOrder(String number, Optional<LocalDate> when) {
        ScopeDecision decision = plan(number);
        if (decision.cylinders().isEmpty()) {
            throw new ConflictError("that incident calls for no work", Facts.of("number", number));
        }
        CoverageMap coverage = coverage();
        String orderNumber = numbering.next(ORDER_SERIES);
        WorkOrderRow order = new WorkOrderRow(orderNumber);
        order.setIncidentNumber(number);
        orders.add(order);
        session.flush();
        for (String serial : decision.cylinders()) {
            String opening = coverage.register().cylinder(serial).installedAt().orElse("");
            session.add(WorkOrderLineRow.of(order, serial, opening));
        }
        when.ifPresent(order::setScheduledFor);
        session.flush();
        return order;
    }

    /** Open the incident counter at {@code I-0001} and the work order counter at {@code W-1041}. */
    public void openSeries() {
        numbering.openSeries(INCIDENT_SERIES, "I-", 4, 1);
        numbering.openSeries(ORDER_SERIES, "W-", 4, 1041);
    }

    /** The symbols one incident's plan has recut, shallowest first. */
    public List<KeySymbol> symbolsRecut(String number) {
        return plan(number).symbols();
    }

    private void retire(String symbol, LocalDate when, String number) {
        Set<String> held = session.all(RetiredBittingRow.class).stream()
                .map(row -> row.getBitting().render())
                .collect(Collectors.toSet());
        Optional<Bitting> cut = cutFor(symbol);
        if (cut.isEmpty() || held.contains(cut.get().render())) {
            return;
        }
        RetiredBittingRow row = new RetiredBittingRow(cut.get(), RetirementReason.COMPROMISED.value(), when);
        row.setSymbol(symbol);
        row.setNote("retired by " + number);
        session.add(row);
    }

    private Optional<Bitting> cutFor(String symbol) {
        return session
                .list(
                        "select e from ScheduleEntryRow e where e.symbol = ?1 order by e.id",
                        ScheduleEntryRow.class,
                        KeySymbol.parse(symbol))
                .stream()
                .map(ScheduleEntryRow::getBitting)
                .flatMap(Optional::stream)
                .findFirst();
    }
}
