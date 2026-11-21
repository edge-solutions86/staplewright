package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A rekey spread across an academic year: what got a day, and what did not.
 *
 * <p>A four hundred door rekey is not a scheduling problem, it is a negotiation with a calendar
 * somebody else owns. Residence halls can only be touched in the fortnight after the students
 * leave. Teaching rooms cannot be touched in an exam period at all. So the plan takes a list of
 * doors and a calendar of windows and works out which door goes in which window. It does not
 * optimise anything. It refuses the ones that will not fit and says why, which is the only useful
 * thing a plan can do in September, because the answer the shop needs is how much of this is not
 * happening this year.
 *
 * @param placed the doors that got a day, by day and then by number
 * @param unplaced the doors that did not, hardest first
 * @param windows the calendar's windows, in date order
 */
public record Phasing(List<Placement> placed, List<Unplaced> unplaced, List<Window> windows) {

    private static final Comparator<Placement> BY_DAY_THEN_NUMBER = Comparator.comparing(Placement::on)
            .thenComparing(item -> item.door().number());

    public Phasing {
        placed = List.copyOf(placed);
        unplaced = List.copyOf(unplaced);
        windows = List.copyOf(windows);
    }

    /** Every door in the plan, placed or not. */
    public int doorCount() {
        return placed.size() + unplaced.size();
    }

    /** Whether every door got a day. */
    public boolean landed() {
        return unplaced.isEmpty();
    }

    /** The doors placed in one window, by day. */
    public List<Placement> inWindow(String name) {
        return placed.stream().filter(item -> item.window().equals(name)).toList();
    }

    /** The doors placed on one day. */
    public List<Placement> onDay(LocalDate day) {
        return placed.stream().filter(item -> item.on().equals(day)).toList();
    }

    /** Doors placed per window, every window listed in date order even when it took none. */
    public Map<String, Integer> loadByWindow() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        windows.forEach(window -> counts.put(window.name(), 0));
        placed.forEach(item -> counts.merge(item.window(), 1, Integer::sum));
        return Collections.unmodifiableMap(counts);
    }

    /** What each window could still take, in date order. */
    public Map<String, Integer> spareCapacity() {
        Map<String, Integer> used = loadByWindow();
        Map<String, Integer> spare = new LinkedHashMap<>();
        windows.forEach(window -> spare.put(window.name(), window.capacity() - used.getOrDefault(window.name(), 0)));
        return Collections.unmodifiableMap(spare);
    }

    /** Why doors did not land, the most common reason first and then as written. */
    public Map<String, Integer> reasons() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        unplaced.forEach(item -> counts.merge(item.reason(), 1, Integer::sum));
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    /** The doors for next year, in the order to put them in next year. */
    public List<Door> carryOver() {
        return unplaced.stream().map(Unplaced::door).toList();
    }

    /**
     * Put every door in the first window that will take it.
     *
     * <p>First rather than best. Doors with the fewest windows open to them go first, then by
     * number. A door that can only go in one window has to go there, and a door that could go
     * anywhere may as well go early, because the thing that ruins a year long plan is a window that
     * fills up in March with work that could have been done in August.
     */
    public static Phasing phase(WorkCalendar calendar, Collection<Door> doors) {
        List<Window> windows = calendar.inOrder();
        Map<String, Integer> used = new LinkedHashMap<>();
        windows.forEach(window -> used.put(window.name(), 0));
        List<Placement> placed = new ArrayList<>();
        List<Unplaced> unplaced = new ArrayList<>();
        for (Door door : hardestFirst(calendar, doors)) {
            boolean landed = false;
            for (Window window : windows) {
                if (!calendar.allows(window.name(), door.restriction())) {
                    continue;
                }
                int taken = used.get(window.name());
                if (taken >= window.capacity()) {
                    continue;
                }
                placed.add(new Placement(door, window.name(), window.dayFor(taken)));
                used.put(window.name(), taken + 1);
                landed = true;
                break;
            }
            if (!landed) {
                unplaced.add(new Unplaced(door, why(calendar, door)));
            }
        }
        placed.sort(BY_DAY_THEN_NUMBER);
        return new Phasing(placed, unplaced, windows);
    }

    private static List<Door> hardestFirst(WorkCalendar calendar, Collection<Door> doors) {
        return doors.stream()
                .sorted(Comparator.comparingInt((Door door) ->
                                calendar.windowsFor(door.restriction()).size())
                        .thenComparing(Door::number))
                .toList();
    }

    /** The reason a door did not land, which is one of exactly two things. */
    private static String why(WorkCalendar calendar, Door door) {
        if (calendar.windowsFor(door.restriction()).isEmpty()) {
            return "no window takes work that is " + door.restriction().value();
        }
        return "every window that would take it is full";
    }

    /**
     * The plan as the page that goes to the facilities meeting: the totals, a line a window with
     * what it took and what it has spare, then either that everything fits or how many doors do not
     * and why.
     */
    public static List<String> yearPlan(WorkCalendar calendar, Collection<Door> doors) {
        Phasing found = phase(calendar, doors);
        List<String> lines = new ArrayList<>();
        lines.add(found.doorCount() + " doors across " + calendar.size() + " windows");
        Map<String, Integer> load = found.loadByWindow();
        Map<String, Integer> spare = found.spareCapacity();
        for (Window window : calendar) {
            lines.add("  " + Text.left(window.name(), 16) + Text.right(load.getOrDefault(window.name(), 0), 4)
                    + " placed, " + Text.right(spare.getOrDefault(window.name(), 0), 4) + " spare");
        }
        if (found.landed()) {
            lines.add("everything fits this year");
            return List.copyOf(lines);
        }
        lines.add(found.unplaced().size() + " doors do not fit");
        found.reasons().forEach((reason, count) -> lines.add("  " + Text.right(count, 4) + "  " + reason));
        return List.copyOf(lines);
    }
}
