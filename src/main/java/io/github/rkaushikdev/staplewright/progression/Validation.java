package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reading a whole schedule back and saying what is wrong with it.
 *
 * <p>Nothing in here refuses anything. A schedule that has been in the ground for nine years is
 * full of decisions somebody made on a Tuesday, and the shop needs a list of them rather than a
 * refusal. Findings carry a severity so that the difference between two keys cut the same, which is
 * a real problem, and a change key that was progressed off pattern, which is usually just old, comes
 * out of the report in the right order.
 *
 * <p>The checks that matter, in the order they get argued about:
 *
 * <ul>
 *   <li>two symbols cut the same key, which means two rooms share a lock nobody meant to share
 *   <li>a change key cut identically to its master, which is not a change key
 *   <li>a key that moved in a chamber the system said was constant
 *   <li>a split of one step, which is a wafer that will jam
 *   <li>more wafers in one chamber than the shop will build
 * </ul>
 */
public final class Validation {

    /** The most wafers the shop builds into one chamber unless told otherwise. */
    public static final int MOST_WAFERS = 3;

    private Validation() {}

    /** Two symbols cut to the same bitting, the later one in schedule order reported. */
    public static List<Finding> duplicateCuts(KeyingSchedule schedule) {
        Map<Bitting, KeySymbol> seen = new HashMap<>();
        List<Finding> found = new ArrayList<>();
        for (ScheduleEntry entry : schedule.entries()) {
            if (entry.bitting().isEmpty()) {
                continue;
            }
            Bitting bitting = entry.bitting().get();
            KeySymbol first = seen.putIfAbsent(bitting, entry.symbol());
            if (first == null) {
                continue;
            }
            found.add(new Finding(
                    "duplicate_cut",
                    Severity.BLOCKING,
                    entry.symbol().render() + " is cut the same as " + first.render() + ", " + bitting.render(),
                    entry.symbol().render()));
        }
        return List.copyOf(found);
    }

    /** A key cut identically to the one above it. */
    public static List<Finding> keysMatchingTheirMaster(KeyingSchedule schedule) {
        List<Finding> found = new ArrayList<>();
        for (ScheduleEntry entry : schedule.entries()) {
            Optional<Bitting> above = cutAbove(schedule, entry);
            if (above.isPresent() && above.equals(entry.bitting())) {
                KeySymbol parent = entry.symbol().parent().orElseThrow();
                found.add(new Finding(
                        "same_as_master",
                        Severity.BLOCKING,
                        entry.symbol().render() + " is cut the same as " + parent.render(),
                        entry.symbol().render()));
            }
        }
        return List.copyOf(found);
    }

    /** Splits between a key and its master that are not whole two step moves. */
    public static List<Finding> thinSplits(KeyingSchedule schedule) {
        return thinSplits(schedule, ProgressionPlan.DEFAULT_STEP);
    }

    /** Splits between a key and its master that are not a whole number of steps. */
    public static List<Finding> thinSplits(KeyingSchedule schedule, int step) {
        List<Finding> found = new ArrayList<>();
        for (ScheduleEntry entry : schedule.entries()) {
            Optional<Bitting> above = cutAbove(schedule, entry);
            if (above.isEmpty()) {
                continue;
            }
            Bitting cut = entry.bitting().orElseThrow();
            for (int chamber = 1; chamber <= schedule.chambers(); chamber++) {
                int gap = Math.abs(above.get().at(chamber) - cut.at(chamber));
                if (gap != 0 && gap % step != 0) {
                    found.add(new Finding(
                            "off_step",
                            Severity.SERIOUS,
                            entry.symbol().render() + " splits " + gap + " in chamber " + chamber
                                    + " and the system moves " + step + " at a time",
                            entry.symbol().render()));
                }
            }
        }
        return List.copyOf(found);
    }

    /** Change keys that could not have come out of the plan. */
    public static List<Finding> keysOffPlan(KeyingSchedule schedule, ProgressionPlan plan) {
        List<Finding> found = new ArrayList<>();
        for (KeySymbol symbol : schedule.changeKeys()) {
            Optional<Bitting> cut = schedule.bittingOf(symbol);
            if (cut.isEmpty() || plan.holds(cut.get())) {
                continue;
            }
            Bitting bitting = cut.get();
            Optional<Integer> moved = plan.constants().stream()
                    .filter(chamber -> bitting.at(chamber) != plan.master().at(chamber))
                    .findFirst();
            String message = moved.map(chamber -> symbol.render() + " moved in constant chamber " + chamber)
                    .orElse(symbol.render() + " is not one of the keys the plan holds");
            found.add(new Finding("off_plan", Severity.SERIOUS, message, symbol.render()));
        }
        return List.copyOf(found);
    }

    /** Chambers asked to break at more depths than the shop builds, three wafers at most. */
    public static List<Finding> crowdedChambers(KeyingSchedule schedule, List<List<Bitting>> groups) {
        return crowdedChambers(schedule, groups, MOST_WAFERS);
    }

    /**
     * Chambers asked to break at more depths than the shop will build.
     *
     * @param groups the cuts behind each keying group; groups are numbered from one in the messages
     */
    public static List<Finding> crowdedChambers(KeyingSchedule schedule, List<List<Bitting>> groups, int mostWafers) {
        List<Finding> found = new ArrayList<>();
        for (int index = 1; index <= groups.size(); index++) {
            List<Bitting> keys = groups.get(index - 1);
            for (int chamber = 1; chamber <= schedule.chambers(); chamber++) {
                Set<Integer> depths = new HashSet<>();
                for (Bitting key : keys) {
                    depths.add(key.at(chamber));
                }
                int breaks = depths.size();
                if (breaks - 1 > mostWafers) {
                    found.add(new Finding(
                            "crowded_chamber",
                            Severity.SERIOUS,
                            "group " + index + " wants " + (breaks - 1) + " wafers in chamber " + chamber
                                    + " and the shop builds " + mostWafers));
                }
            }
        }
        return List.copyOf(found);
    }

    /** Symbols drawn on the schedule that nobody has cut. */
    public static List<Finding> uncutSymbols(KeyingSchedule schedule) {
        return schedule.openSymbols().stream()
                .map(symbol -> new Finding(
                        "uncut",
                        Severity.NOTE,
                        symbol.render() + " is on the schedule and has never been cut",
                        symbol.render()))
                .toList();
    }

    /** Every finding for a two step system, worst first. */
    public static List<Finding> review(KeyingSchedule schedule) {
        return review(schedule, Optional.empty(), ProgressionPlan.DEFAULT_STEP);
    }

    /** Every finding for a system progressed by {@code step}, worst first. */
    public static List<Finding> review(KeyingSchedule schedule, int step) {
        return review(schedule, Optional.empty(), step);
    }

    /** Every finding for a two step system, with the change keys checked against a plan. */
    public static List<Finding> review(KeyingSchedule schedule, ProgressionPlan plan) {
        return review(schedule, Optional.of(plan), ProgressionPlan.DEFAULT_STEP);
    }

    /**
     * Every finding, worst first, then by symbol and code so the report is stable.
     *
     * <p>A finding with no symbol sorts before the symbols at its severity. Crowded chambers are not
     * part of a review, because they need the keying groups.
     */
    public static List<Finding> review(KeyingSchedule schedule, Optional<ProgressionPlan> plan, int step) {
        List<Finding> found = new ArrayList<>();
        found.addAll(duplicateCuts(schedule));
        found.addAll(keysMatchingTheirMaster(schedule));
        found.addAll(thinSplits(schedule, step));
        plan.ifPresent(drawn -> found.addAll(keysOffPlan(schedule, drawn)));
        found.addAll(uncutSymbols(schedule));
        found.sort(Comparator.comparing(Finding::severity)
                .thenComparing(finding -> finding.symbol().orElse(""))
                .thenComparing(Finding::code));
        return List.copyOf(found);
    }

    /** The findings that stop the job. */
    public static List<Finding> blocking(List<Finding> findings) {
        return findings.stream()
                .filter(finding -> finding.severity() == Severity.BLOCKING)
                .toList();
    }

    /** Whether nothing in the findings stops the job. */
    public static boolean isBuildable(List<Finding> findings) {
        return blocking(findings).isEmpty();
    }

    /** What the key directly above this cut line is cut to, when both are cut and the parent is held. */
    private static Optional<Bitting> cutAbove(KeyingSchedule schedule, ScheduleEntry entry) {
        Optional<KeySymbol> parent = entry.symbol().parent();
        if (entry.bitting().isEmpty() || parent.isEmpty() || !schedule.contains(parent.get())) {
            return Optional.empty();
        }
        return schedule.bittingOf(parent.get());
    }
}
