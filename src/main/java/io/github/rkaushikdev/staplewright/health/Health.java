package io.github.rkaushikdev.staplewright.health;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.control.TransferPolicy;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Self checks, for the morning somebody asks whether the database is sound.
 *
 * <p>None of these are unit tests. They are the questions worth asking of a live database, and
 * every one of them came from something that actually went wrong: a cylinder installed on an opening
 * nobody has a record of, a key issued to a person whose appointment ended two years ago, a schedule
 * entry cut to a bitting that is in quarantine, a keying group with no cylinders on it.
 *
 * <p>Each check hands back a list of findings rather than refusing, because the point is to see all
 * of them at once. A check that stops at the first problem turns a morning into a week of one fix at
 * a time. The severities are the only judgement here: a warning is something to look at, a fault is
 * something that means a report somewhere is already wrong.
 */
public final class Health {

    /** One question asked of a live database. */
    @FunctionalInterface
    public interface Check {

        /** Everything this check finds wrong today. */
        List<Finding> run(Session session, LocalDate today);
    }

    /**
     * A check with the name it is asked for by.
     *
     * @param name what a caller asks for it by, which is the question written short
     * @param check the check itself
     */
    public record Named(String name, Check check) {}

    /** Every check by name, the most serious kinds of trouble first. */
    public static final List<Named> NAMED = List.of(
            new Named("unkeyed_openings", Health::unkeyedOpenings),
            new Named("keys_held_by_lapsed_people", Health::keysHeldByLapsedPeople),
            new Named("quarantined_cuts_in_use", Health::quarantinedCutsInUse),
            new Named("handovers_nobody_confirmed", Health::handoversNobodyConfirmed),
            new Named("groups_with_no_cylinders", Health::groupsWithNoCylinders),
            new Named("keys_that_wandered", Health::keysThatWandered),
            new Named("openings_with_no_cylinder", Health::openingsWithNoCylinder),
            new Named("uninstalled_cylinders", Health::uninstalledCylinders),
            new Named("uncut_symbols", Health::uncutSymbols));

    /** Every check, the most serious kinds of trouble first. */
    public static final List<Check> CHECKS = NAMED.stream().map(Named::check).toList();

    private static final Comparator<Finding> WORST_FIRST = Comparator.comparing(
                    (Finding item) -> item.severity().value(), Comparator.reverseOrder())
            .thenComparing(Finding::check)
            .thenComparing(Finding::subject);

    private Health() {}

    /**
     * Cylinders keyed to a group but sitting on a shelf. Not wrong on its own; wrong in quantity,
     * because a shelf full of keyed cores is a shelf full of keys somebody else can read the cuts off.
     */
    public static List<Finding> uninstalledCylinders(Session session, LocalDate today) {
        return session
                .list(
                        "select c from CylinderRow c where c.installedAt is null and c.group is not null order by c.serial",
                        CylinderRow.class)
                .stream()
                .map(row ->
                        new Finding("shelved keyed cores", Severity.NOTE, row.getSerial(), "keyed and not installed"))
                .toList();
    }

    /** Installed cylinders nobody wrote a keying group against. */
    public static List<Finding> unkeyedOpenings(Session session, LocalDate today) {
        return session
                .list(
                        "select c from CylinderRow c where c.installedAt is not null and c.group is null order by c.serial",
                        CylinderRow.class)
                .stream()
                .map(row -> new Finding(
                        "unmapped cylinders",
                        Severity.FAULT,
                        row.getSerial(),
                        "installed at " + row.getInstalledAt().orElse("") + " and answers to nothing on record"))
                .toList();
    }

    /** Keys still out to somebody whose appointment has ended. */
    public static List<Finding> keysHeldByLapsedPeople(Session session, LocalDate today) {
        List<Object[]> found = session.list(
                "select k, p from IssuedKeyRow k, KeyholderRow p where k.keyholder = p.identifier"
                        + " and k.state = 'out' and p.endsOn < ?1 order by k.tag",
                Object[].class,
                today);
        return found.stream()
                .map(pair -> {
                    IssuedKeyRow key = (IssuedKeyRow) pair[0];
                    KeyholderRow person = (KeyholderRow) pair[1];
                    return new Finding(
                            "keys out to lapsed holders",
                            Severity.FAULT,
                            key.getTag(),
                            person.getIdentifier() + " ended "
                                    + person.getEndsOn()
                                            .map(LocalDate::toString)
                                            .orElse(""));
                })
                .toList();
    }

    /** Schedule entries cut to a bitting somebody retired. */
    public static List<Finding> quarantinedCutsInUse(Session session, LocalDate today) {
        Map<String, RetiredBittingRow> retired = new LinkedHashMap<>();
        for (RetiredBittingRow row : session.all(RetiredBittingRow.class)) {
            retired.put(row.getBitting().render(), row);
        }
        if (retired.isEmpty()) {
            return List.of();
        }
        List<Finding> lines = new ArrayList<>();
        for (ScheduleEntryRow row : session.list(
                "select e from ScheduleEntryRow e where e.bitting is not null order by e.symbol",
                ScheduleEntryRow.class)) {
            Optional<RetiredBittingRow> held = row.getBitting().map(cut -> retired.get(cut.render()));
            if (held.isPresent()) {
                String cut = row.getBitting().orElseThrow().render();
                lines.add(new Finding(
                        "quarantined cuts in use",
                        Severity.FAULT,
                        row.getSymbol().render(),
                        "cut to " + cut + ", retired as " + held.get().getReason()));
            }
        }
        return List.copyOf(lines);
    }

    /** Keying groups nothing is keyed to, which is usually a rename gone wrong. */
    public static List<Finding> groupsWithNoCylinders(Session session, LocalDate today) {
        return session
                .list(
                        "select g from GroupRow g where not exists"
                                + " (select c from CylinderRow c where c.group = g) order by g.name",
                        GroupRow.class)
                .stream()
                .map(row ->
                        new Finding("empty keying groups", Severity.WARNING, row.getName(), "nothing is keyed to it"))
                .toList();
    }

    /** Symbols drawn on a live schedule that nobody has cut. */
    public static List<Finding> uncutSymbols(Session session, LocalDate today) {
        List<Object[]> found = session.list(
                "select e, s from ScheduleEntryRow e join e.system s"
                        + " where e.bitting is null and s.archived = false order by e.symbol",
                Object[].class);
        return found.stream()
                .map(pair -> new Finding(
                        "uncut symbols",
                        Severity.NOTE,
                        ((ScheduleEntryRow) pair[0]).getSymbol().render(),
                        "drawn on " + ((SystemRow) pair[1]).getName()))
                .toList();
    }

    /** Keyed openings with nothing in them, which is a door standing open. */
    public static List<Finding> openingsWithNoCylinder(Session session, LocalDate today) {
        List<String> keyed = DoorFunction.KEYED_FUNCTIONS.stream()
                .map(DoorFunction::value)
                .sorted()
                .toList();
        return session
                .list(
                        "select o from OpeningRow o where o.function in ?1 and not exists"
                                + " (select c from CylinderRow c where c.installedAt = o.number) order by o.number",
                        OpeningRow.class,
                        keyed)
                .stream()
                .map(row -> new Finding(
                        "keyed openings with nothing in them",
                        Severity.WARNING,
                        row.getNumber(),
                        "on the schedule as keyed and holds no cylinder"))
                .toList();
    }

    /**
     * Keys whose ledger holder is not the person who has them. Every line is a key the shop would
     * have asked the wrong person to produce, the most embarrassing thing an audit turns up.
     */
    public static List<Finding> keysThatWandered(Session session, LocalDate today) {
        List<TransferRow> rows = session.all(TransferRow.class);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<IssuedKeyRow> keys =
                session.list("select k from IssuedKeyRow k where k.state = 'out'", IssuedKeyRow.class);
        return TransferMappers.strays(keys, rows, today).stream()
                .map(stray -> new Finding(
                        "keys that changed hands",
                        Severity.WARNING,
                        stray.tag(),
                        "ledger says " + stray.ledgerHolder() + ", actually with " + stray.holder()))
                .toList();
    }

    /** Claims sitting open past the window, which means a key is nowhere. */
    public static List<Finding> handoversNobodyConfirmed(Session session, LocalDate today) {
        List<TransferRow> rows = session.all(TransferRow.class);
        if (rows.isEmpty()) {
            return List.of();
        }
        return TransferMappers.bookFromRows(rows, new TransferPolicy()).late(today).stream()
                .map(item -> new Finding(
                        "handovers nobody confirmed",
                        Severity.FAULT,
                        item.number(),
                        item.tag() + " claimed " + item.claimedOn() + ", nobody signed"))
                .toList();
    }

    /** Every check's name, sorted. */
    public static List<String> checkNames() {
        return NAMED.stream().map(Named::name).sorted().toList();
    }

    /** The check called {@code name}, refusing one nobody wrote. */
    public static Check checkByName(String name) {
        return NAMED.stream()
                .filter(item -> item.name().equals(name))
                .map(Named::check)
                .findFirst()
                .orElseThrow(() -> new NotFoundError("no such check", Facts.of("check", name)));
    }

    /** Every check, worst first, then by check and by what it is about. */
    public static List<Finding> runChecks(Session session, LocalDate today) {
        return runChecks(session, today, CHECKS);
    }

    /** These checks only, worst first. */
    public static List<Finding> runChecks(Session session, LocalDate today, List<Check> checks) {
        List<Finding> found = new ArrayList<>();
        for (Check check : checks) {
            found.addAll(check.run(session, today));
        }
        found.sort(WORST_FIRST);
        return List.copyOf(found);
    }

    /** The worst severity in a set, or nothing when it is clean. */
    public static Optional<Severity> worst(List<Finding> findings) {
        return findings.stream().map(Finding::severity).max(Comparator.comparing(Severity::value));
    }

    /** How many findings of each severity, every severity named. */
    public static Map<String, Integer> countsBySeverity(List<Finding> findings) {
        Map<String, Integer> counted = new LinkedHashMap<>();
        for (Severity item : Severity.values()) {
            counted.put(item.label(), 0);
        }
        for (Finding finding : findings) {
            counted.merge(finding.severity().label(), 1, Integer::sum);
        }
        return counted;
    }

    /** The whole check, as the page somebody reads on a Monday. */
    public static List<String> healthReport(Session session, LocalDate today) {
        List<Finding> findings = runChecks(session, today);
        String head = "self check at " + today;
        if (findings.isEmpty()) {
            return List.of(head, "nothing to answer for");
        }
        Map<String, Integer> counted = countsBySeverity(findings);
        List<String> lines = new ArrayList<>();
        lines.add(head);
        lines.add(counted.get("fault") + " faults, " + counted.get("warning") + " warnings, " + counted.get("note")
                + " notes");
        String seen = "";
        for (Finding finding : findings) {
            if (!finding.check().equals(seen)) {
                lines.add(finding.check());
                seen = finding.check();
            }
            lines.add("  " + finding.describe());
        }
        return List.copyOf(lines);
    }

    /** Nought unless something is actually wrong, for a scheduled run. */
    public static int exitCode(List<Finding> findings) {
        return worst(findings).filter(severity -> severity == Severity.FAULT).isPresent() ? 1 : 0;
    }
}
