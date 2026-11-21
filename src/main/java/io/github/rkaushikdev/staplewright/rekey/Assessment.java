package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * What one missing key actually exposes, worked out before anybody argues about it.
 *
 * <p>The assessment is arithmetic and it is worth doing before the meeting. A key is gone. It opens
 * a list of doors. Some of those doors face outside, and every one of them is a cylinder somebody
 * has to take apart if the decision is to rekey. Severity comes off four numbers: how many
 * openings, how many buildings, whether any of them is an exterior door, and whether the key sits
 * above a change key in the hierarchy. A change key to one office is a routine event; a building
 * master taken out of a truck is not, and both are written up the same way so the difference is
 * visible.
 *
 * @param incident the report being assessed
 * @param openings every opening the key turns, in number order
 * @param buildings the buildings those openings are in, in code order
 * @param cylinders the installed cylinders the key turns, in serial order
 * @param exteriorOpenings the openings among them that face outside
 * @param keysToRecut the tags of the other keys still out on the same symbol, in tag order
 * @param holdersAffected who holds those keys, each once, in identifier order
 * @param severity the grade the four numbers come to
 */
public record Assessment(
        Incident incident,
        List<String> openings,
        List<String> buildings,
        List<String> cylinders,
        List<String> exteriorOpenings,
        List<String> keysToRecut,
        List<String> holdersAffected,
        Severity severity) {

    private static final Comparator<Assessment> WORST_FIRST = Comparator.comparing(
                    Assessment::severity, Comparator.reverseOrder())
            .thenComparing(Assessment::openingCount, Comparator.reverseOrder())
            .thenComparing(assessment -> assessment.incident().number());

    public Assessment {
        openings = List.copyOf(openings);
        buildings = List.copyOf(buildings);
        cylinders = List.copyOf(cylinders);
        exteriorOpenings = List.copyOf(exteriorOpenings);
        keysToRecut = List.copyOf(keysToRecut);
        holdersAffected = List.copyOf(holdersAffected);
    }

    /** How many openings the key turns. */
    public int openingCount() {
        return openings.size();
    }

    /** Whether any of those openings faces outside. */
    public boolean touchesOutside() {
        return !exteriorOpenings.isEmpty();
    }

    /** Whether the missing key sits above a change key. */
    public boolean isMaster() {
        return !incident.keySymbol().isChangeKey();
    }

    /** One line for the incident log: {@code I-2025-004 AA1 lost: 2 openings in 1 buildings, routine}. */
    public String summary() {
        return incident.number() + " " + incident.symbol() + " "
                + incident.cause().value() + ": " + openingCount() + " openings in " + buildings.size() + " buildings, "
                + severity.label();
    }

    /**
     * Everything an incident touches, and how bad that makes it. The missing key itself is left out
     * of the keys to recut and of the holders affected.
     */
    public static Assessment assess(Incident incident, CoverageMap coverage, KeyLedger ledger) {
        KeySymbol symbol = incident.keySymbol();
        List<String> openings = coverage.openingsFor(symbol);
        OpeningSchedule schedule = coverage.register().openings();
        List<String> exterior = openings.stream()
                .filter(number -> schedule.opening(number).exterior())
                .toList();
        List<String> cylinders = coverage.cylindersFor(symbol).stream()
                .filter(Cylinder::isInstalled)
                .map(Cylinder::serial)
                .toList();
        List<IssuedKey> others = CoverageMap.keysThatOpen(ledger, List.of(symbol)).stream()
                .filter(key -> !key.tag().equals(incident.tag()))
                .toList();
        List<String> toRecut = others.stream().map(IssuedKey::tag).toList();
        List<String> holders =
                others.stream().map(IssuedKey::keyholder).distinct().sorted().toList();
        List<String> buildings = coverage.buildingsFor(symbol);
        Severity severity = Severity.grade(
                openings.size(), buildings.size(), !exterior.isEmpty(), !symbol.isChangeKey(), incident.cause());
        return new Assessment(incident, openings, buildings, cylinders, exterior, toRecut, holders, severity);
    }

    /**
     * A pile of incidents in the order the morning meeting wants them: the worst grade first, then
     * the widest reach, then by incident number.
     */
    public static List<Assessment> worstFirst(Collection<Assessment> assessments) {
        return assessments.stream().sorted(WORST_FIRST).toList();
    }
}
