package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every repin the shop has recorded, by cylinder, which is the only case for replacing one.
 *
 * <p>A cylinder does not wear out on a schedule. It wears out from being taken apart: the plug gets
 * marked, the cap screws round out, the chambers pick up brass dust, and the eleventh repin is the
 * one where a pin sticks and somebody spends an afternoon on a door that used to work. Each repin
 * is twenty minutes and looks like nothing. The history turns that into a number, and the number is
 * what gets a cylinder replaced during a rekey the shop is doing anyway rather than in an emergency
 * six months later.
 */
public final class RekeyHistory implements Iterable<RekeyEvent> {

    /** How many repins a cylinder takes before the shop replaces it, unless told otherwise. */
    public static final int REPLACE_AFTER = 10;

    /** How many cylinders the busiest list names unless told otherwise. */
    public static final int BUSIEST_LIMIT = 5;

    private static final Comparator<RekeyEvent> BY_DAY_THEN_ORDER =
            Comparator.comparing(RekeyEvent::happenedOn).thenComparing(RekeyEvent::orderNumber);

    private static final Comparator<RekeyEvent> BY_DAY_THEN_SERIAL =
            Comparator.comparing(RekeyEvent::happenedOn).thenComparing(RekeyEvent::serial);

    private static final Comparator<RepinCount> MOST_FIRST =
            Comparator.comparing(RepinCount::repins, Comparator.reverseOrder()).thenComparing(RepinCount::serial);

    private final int replaceAfter;
    private final List<RekeyEvent> events = new ArrayList<>();

    /** A history replacing cylinders after the standing ten repins. */
    public RekeyHistory() {
        this(REPLACE_AFTER);
    }

    /**
     * A history replacing cylinders after this many repins.
     *
     * @throws InputError for fewer than one
     */
    public RekeyHistory(int replaceAfter) {
        if (replaceAfter < 1) {
            throw new InputError("a replacement threshold is a count of repins");
        }
        this.replaceAfter = replaceAfter;
    }

    /** How many repins make a cylinder worn out. */
    public int replaceAfter() {
        return replaceAfter;
    }

    /** Write one repin down. Events can arrive out of order and do. */
    public RekeyEvent record(RekeyEvent event) {
        events.add(event);
        return event;
    }

    /** Write several repins down, in the order given. */
    public void recordAll(Iterable<RekeyEvent> some) {
        some.forEach(this::record);
    }

    /** One cylinder's repins, oldest first and then by order number. */
    public List<RekeyEvent> eventsFor(String serial) {
        return events.stream()
                .filter(event -> event.serial().equals(serial))
                .sorted(BY_DAY_THEN_ORDER)
                .toList();
    }

    /** How many times one cylinder has come apart. */
    public int timesRekeyed(String serial) {
        return eventsFor(serial).size();
    }

    /** The day of one cylinder's latest repin, when it has one. */
    public Optional<LocalDate> lastRekeyed(String serial) {
        return latest(serial).map(RekeyEvent::happenedOn);
    }

    /** What the cylinder is keyed to now, which is its last event, or nothing on record. */
    public List<KeySymbol> currentSymbols(String serial) {
        return latest(serial).map(RekeyEvent::toSymbols).orElse(List.of());
    }

    /** Days between one repin and the next, oldest first. */
    public List<Integer> intervals(String serial) {
        List<RekeyEvent> found = eventsFor(serial);
        List<Integer> gaps = new ArrayList<>();
        for (int index = 1; index < found.size(); index++) {
            gaps.add((int) ChronoUnit.DAYS.between(
                    found.get(index - 1).happenedOn(), found.get(index).happenedOn()));
        }
        return List.copyOf(gaps);
    }

    /** The mean gap between repins in whole days, cut toward zero, when there are two repins. */
    public Optional<Integer> averageInterval(String serial) {
        List<Integer> gaps = intervals(serial);
        if (gaps.isEmpty()) {
            return Optional.empty();
        }
        long total = gaps.stream().mapToLong(Integer::longValue).sum();
        return Optional.of((int) (total / (double) gaps.size()));
    }

    /** Cylinders at or past the replacement threshold, most repinned first and then by serial. */
    public List<RepinCount> wornOut() {
        return counts().stream()
                .filter(count -> count.repins() >= replaceAfter)
                .sorted(MOST_FIRST)
                .toList();
    }

    /** Cylinders with no history at all, which is either new or unrecorded, in serial order. */
    public List<String> neverRekeyed(Collection<String> serials) {
        Set<String> seen = new HashSet<>();
        events.forEach(event -> seen.add(event.serial()));
        return serials.stream()
                .filter(serial -> !seen.contains(serial))
                .sorted()
                .toList();
    }

    /** The five most repinned cylinders. */
    public List<RepinCount> busiest() {
        return busiest(BUSIEST_LIMIT);
    }

    /** The most repinned cylinders, the most first and then by serial. */
    public List<RepinCount> busiest(int limit) {
        return counts().stream().sorted(MOST_FIRST).limit(limit).toList();
    }

    /** One technician's repins, by day and then by serial. */
    public List<RekeyEvent> byTechnician(String technician) {
        return events.stream()
                .filter(event -> event.technician().equals(technician))
                .sorted(BY_DAY_THEN_SERIAL)
                .toList();
    }

    /** Every repin on or after a day, by day and then by serial. */
    public List<RekeyEvent> since(LocalDate when) {
        return events.stream()
                .filter(event -> !event.happenedOn().isBefore(when))
                .sorted(BY_DAY_THEN_SERIAL)
                .toList();
    }

    /** The history of one cylinder for the back of a work order: a heading, then a line a repin. */
    public List<String> lines(String serial) {
        List<RekeyEvent> found = eventsFor(serial);
        if (found.isEmpty()) {
            return List.of(serial + ": nothing on record");
        }
        List<String> lines = new ArrayList<>();
        lines.add(serial + ": " + found.size() + " repins");
        found.forEach(event -> lines.add(event.describe()));
        return List.copyOf(lines);
    }

    /** Every repin in the order it was written down. */
    @Override
    public Iterator<RekeyEvent> iterator() {
        return List.copyOf(events).iterator();
    }

    /** How many repins are on record. */
    public int size() {
        return events.size();
    }

    private Optional<RekeyEvent> latest(String serial) {
        List<RekeyEvent> found = eventsFor(serial);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(found.size() - 1));
    }

    private List<RepinCount> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        events.forEach(event -> counts.merge(event.serial(), 1, Integer::sum));
        return counts.entrySet().stream()
                .map(entry -> new RepinCount(entry.getKey(), entry.getValue()))
                .toList();
    }
}
