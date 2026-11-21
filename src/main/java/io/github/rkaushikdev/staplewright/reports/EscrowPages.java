package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.BoxRegister;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The key box paperwork, which two different people want two different halves of.
 *
 * <p>The fire service wants the coverage list: which buildings have a box, where it is, and nothing
 * else. They do not want to know what is in it and the shop should not be telling them.
 *
 * <p>The shop wants the exposure list: which boxes are open, how long they have been open, and which
 * symbols are loose because of it. That half never leaves the shop, because a list of buildings
 * whose emergency key is missing is a shopping list for anybody who reads it.
 *
 * <p>So they are two pages and they are meant to stay two pages. The temptation to write one report
 * with a switch on it is exactly how the wrong half ends up in an email to a fire officer.
 */
public final class EscrowPages {

    static final int BOX_WIDTH = 10;
    static final int OPENING_WIDTH = 12;

    private EscrowPages() {}

    private static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    /** Where the boxes are, building by building in code order. Safe to hand over, and says nothing about keys. */
    public static List<String> coverageSheet(BoxRegister register, Collection<String> buildings) {
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("building", "box", "door"), 10, BOX_WIDTH));
        Map<String, KeyBox> held = new LinkedHashMap<>();
        for (KeyBox box : register) {
            if (box.isLive()) {
                held.put(box.building(), box);
            }
        }
        for (String code : buildings.stream().sorted().toList()) {
            KeyBox box = held.get(code);
            if (box == null) {
                lines.add(Layout.columns(List.of(code, "none", ""), 10, BOX_WIDTH));
            } else {
                lines.add(Layout.columns(List.of(code, box.number(), box.opening()), 10, BOX_WIDTH));
            }
        }
        return List.copyOf(lines);
    }

    /** What is open and for how long, and which keys are loose because of it. Never leaves the shop. */
    public static List<String> exposureSheet(BoxRegister register, LocalDate today) {
        List<KeyBox> found = register.openBoxes();
        if (found.isEmpty()) {
            return List.of("every box is sealed");
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " boxes are not sealed");
        for (KeyBox box : found) {
            long days = daysBetween(box.openedOn().orElse(box.lastSeen()), today);
            String held = box.holdsAKey() ? box.symbol().render() : "nothing in it";
            lines.add("  " + Text.left(box.number(), BOX_WIDTH) + box.building() + " " + held + ", " + days + " days");
        }
        List<KeySymbol> atRisk = register.symbolsAtRisk();
        if (!atRisk.isEmpty()) {
            lines.add("keys loose: " + atRisk.stream().map(KeySymbol::render).collect(Collectors.joining(", ")));
        }
        return List.copyOf(lines);
    }

    /** The list somebody carries round the campus: the boxes to reseal, then the boxes due a look. */
    public static List<String> walkSheet(BoxRegister register, LocalDate today) {
        List<KeyBox> due = register.dueForCheck(today);
        List<KeyBox> openBoxes = register.openBoxes();
        if (due.isEmpty() && openBoxes.isEmpty()) {
            return List.of("nothing to walk this week");
        }
        List<String> lines = new ArrayList<>();
        if (!openBoxes.isEmpty()) {
            lines.add(openBoxes.size() + " to reseal");
            for (KeyBox box : openBoxes) {
                lines.add("  " + Text.left(box.number(), BOX_WIDTH) + box.building() + " " + box.opening());
            }
        }
        if (!due.isEmpty()) {
            lines.add(due.size() + " to check");
            for (KeyBox box : due) {
                long days = daysBetween(box.lastSeen(), today);
                lines.add("  " + Text.left(box.number(), BOX_WIDTH) + box.building() + " last seen " + days
                        + " days ago");
            }
        }
        return List.copyOf(lines);
    }

    /** One box's whole history, oldest seal first, which is the answer to a found key. */
    public static List<String> sealSheet(Collection<SealRow> rows, LocalDate today) {
        if (rows.isEmpty()) {
            return List.of("no seal was ever recorded on that box");
        }
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("seal", "key", "on", "off", "days"), 10, 8, 12, 12));
        rows.stream()
                .sorted(Comparator.comparing(SealRow::getSealedOn)
                        .thenComparingInt(row -> row.getId() == null ? 0 : row.getId()))
                .forEach(row -> lines.add(Layout.columns(
                        List.of(
                                row.getSeal(),
                                row.getSymbol().render(),
                                row.getSealedOn().toString(),
                                row.getBrokenOn().map(LocalDate::toString).orElse("on now"),
                                String.valueOf(row.daysHeld(today))),
                        10,
                        8,
                        12,
                        12)));
        return List.copyOf(lines);
    }

    /** Boxes open longer than the shop said it would take to get back out. */
    public static List<String> overdueSheet(BoxRegister register, LocalDate today, BoxPolicy policy) {
        List<KeyBox> found = register.overdueReseal(today);
        if (found.isEmpty()) {
            return List.of("no box is overdue a reseal");
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " boxes are past the " + policy.resealDays() + " day reseal window");
        for (KeyBox box : found) {
            long days = daysBetween(box.openedOn().orElse(today), today);
            lines.add("  " + Text.left(box.number(), BOX_WIDTH) + box.building() + " open " + days + " days");
        }
        return List.copyOf(lines);
    }

    /** How many boxes are in each state, every state listed, in the order the states read. */
    public static Map<String, Integer> stateCounts(BoxRegister register) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (BoxState state : BoxState.values()) {
            counts.put(state.value(), register.inState(state).size());
        }
        return Collections.unmodifiableMap(counts);
    }

    /** The shop's half, top to bottom. This one stays in the building. */
    public static List<String> shopReport(BoxRegister register, Collection<String> buildings, LocalDate today) {
        Map<String, Integer> counts = stateCounts(register);
        String tally = counts.entrySet().stream()
                .filter(entry -> entry.getValue() != 0)
                .map(entry -> entry.getValue() + " " + entry.getKey())
                .collect(Collectors.joining(", "));
        List<String> lines = new ArrayList<>();
        lines.add("key boxes at " + today + ", " + register.size() + " on record");
        lines.add(tally.isEmpty() ? "none on any wall" : tally);
        lines.add("");
        lines.addAll(exposureSheet(register, today));
        lines.add("");
        lines.addAll(overdueSheet(register, today, register.policy()));
        lines.add("");
        lines.addAll(walkSheet(register, today));
        lines.add("");
        lines.addAll(uncovered(register, buildings));
        return List.copyOf(lines);
    }

    private static List<String> uncovered(BoxRegister register, Collection<String> buildings) {
        Set<String> held = new HashSet<>();
        for (KeyBox box : register) {
            if (box.isLive()) {
                held.add(box.building());
            }
        }
        List<String> missing =
                buildings.stream().filter(code -> !held.contains(code)).sorted().toList();
        if (missing.isEmpty()) {
            return List.of("every building has a box");
        }
        List<String> lines = new ArrayList<>();
        lines.add(missing.size() + " buildings have no box");
        for (String code : missing) {
            lines.add("  " + code);
        }
        return List.copyOf(lines);
    }

    /** Which boxes hold this symbol, for the key somebody handed in. */
    public static List<String> keyFound(BoxRegister register, String symbol) {
        List<KeyBox> matching = new ArrayList<>();
        for (KeyBox box : register) {
            if (box.symbol().render().equals(symbol)) {
                matching.add(box);
            }
        }
        if (matching.isEmpty()) {
            return List.of("no box holds " + symbol);
        }
        List<String> lines = new ArrayList<>();
        lines.add(matching.size() + " boxes hold " + symbol);
        for (KeyBox box : matching) {
            lines.add("  " + box.describe());
        }
        return List.copyOf(lines);
    }
}
