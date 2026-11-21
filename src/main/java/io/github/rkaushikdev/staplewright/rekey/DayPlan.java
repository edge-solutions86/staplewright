package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * One technician, one day, in the order the doors get walked.
 *
 * <p>A rekey day is a bench half and a door half. The bench half is predictable: so many minutes a
 * cylinder, done sitting down, in any order. The door half is walking, and walking is where a day
 * goes wrong, because a crew that does a door on the second floor, then one in the basement, then
 * another on the second floor has spent twenty minutes on stairs for nothing. So the route is
 * sorted by building and floor and nothing cleverer: a shortest-tour solution would save perhaps
 * four minutes on a real building and produce a route nobody can follow with a bag of pins in one
 * hand.
 *
 * @param technician who walks it
 * @param stops the doors, in walking order
 * @param benchMinutes repinning at the bench
 * @param travelMinutes getting to the buildings and back
 */
public record DayPlan(String technician, List<Stop> stops, int benchMinutes, int travelMinutes) {

    private static final Comparator<Stop> WALKING_ORDER =
            Comparator.comparing(Stop::building).thenComparing(Stop::floor).thenComparing(Stop::opening);

    public DayPlan {
        stops = List.copyOf(stops);
    }

    /** Every minute at the doors. */
    public int doorMinutes() {
        return stops.stream().mapToInt(Stop::minutes).sum();
    }

    /** Bench, door and travel together. */
    public int totalMinutes() {
        return benchMinutes + doorMinutes() + travelMinutes;
    }

    /** The buildings visited, each once, in code order. */
    public List<String> buildings() {
        return stops.stream().map(Stop::building).distinct().sorted().toList();
    }

    /** The plan as printed: a heading with the totals, then a line a door. */
    public List<String> lines() {
        String head = technician + ": " + stops.size() + " doors, " + totalMinutes() + " minutes";
        return Stream.concat(Stream.of(head), stops.stream().map(Stop::describe))
                .toList();
    }

    /**
     * Whether the whole plan fits in the minutes a day has.
     *
     * @throws InputError for a negative day
     */
    public boolean fitsIn(int minutesAvailable) {
        if (minutesAvailable < 0) {
            throw new InputError("a day is not negative", Facts.of("minutes", minutesAvailable));
        }
        return totalMinutes() <= minutesAvailable;
    }

    /**
     * Cut the plan at the end of the last building that fits. A plan that fits comes back whole as
     * the first half, with nothing after it.
     *
     * <p>Splitting inside a building is what produces two trips to the same address, so the cut
     * goes on a building boundary even when that wastes half an hour of the first day. A first
     * building that does not fit on its own is taken anyway.
     *
     * @throws InputError for a day with no minutes in it
     */
    public DaySplit splitDay(int minutesAvailable) {
        if (minutesAvailable <= 0) {
            throw new InputError("a day has some minutes in it", Facts.of("minutes", minutesAvailable));
        }
        if (fitsIn(minutesAvailable)) {
            return new DaySplit(this, Optional.empty());
        }
        List<String> buildings = buildings();
        List<Stop> taken = new ArrayList<>();
        for (String building : buildings) {
            List<Stop> candidate = new ArrayList<>(taken);
            candidate.addAll(inBuilding(building));
            if (minutesFor(candidate) > minutesAvailable && !taken.isEmpty()) {
                break;
            }
            taken = candidate;
        }
        if (taken.isEmpty()) {
            taken = inBuilding(buildings.get(0));
        }
        List<Stop> kept = taken;
        List<Stop> left = stops.stream().filter(stop -> !kept.contains(stop)).toList();
        DayPlan first = rebuild(kept);
        Optional<DayPlan> second = left.isEmpty() ? Optional.empty() : Optional.of(rebuild(left));
        return new DaySplit(first, second);
    }

    private List<Stop> inBuilding(String building) {
        return stops.stream().filter(stop -> stop.building().equals(building)).toList();
    }

    private int perCylinder() {
        return benchMinutes / Math.max(stops.size(), 1);
    }

    private int perBuilding() {
        return travelMinutes / Math.max(buildings().size(), 1);
    }

    private static int buildingCount(Collection<Stop> stops) {
        return (int) stops.stream().map(Stop::building).distinct().count();
    }

    private int minutesFor(List<Stop> some) {
        int bench = perCylinder() * some.size();
        int travel = perBuilding() * buildingCount(some);
        return bench + travel + some.stream().mapToInt(Stop::minutes).sum();
    }

    private DayPlan rebuild(List<Stop> some) {
        return new DayPlan(technician, some, perCylinder() * some.size(), perBuilding() * buildingCount(some));
    }

    /** Sort the doors into walking order: building, then floor, then opening number. */
    public static List<Stop> route(Collection<OrderLine> lines, OpeningSchedule openings, RateCard rates) {
        List<Stop> stops = new ArrayList<>();
        for (OrderLine line : lines) {
            Opening opening = openings.opening(line.opening());
            stops.add(new Stop(
                    line.serial(),
                    line.opening(),
                    opening.building(),
                    opening.floor(),
                    rates.doorMinutesPerCylinder()));
        }
        stops.sort(WALKING_ORDER);
        return List.copyOf(stops);
    }

    /** Everything left on one order, for the technician the order names. */
    public static DayPlan planDay(WorkOrder order, OpeningSchedule openings, RateCard rates) {
        return planDay(order, openings, rates, "");
    }

    /**
     * Everything left on one order, in the order it should be walked, for a named technician or,
     * when the name is empty, the one the order names.
     *
     * @throws ConflictError when nobody is named or nothing is left on the order
     */
    public static DayPlan planDay(WorkOrder order, OpeningSchedule openings, RateCard rates, String technician) {
        String who = technician.isEmpty() ? order.technician() : technician;
        if (Text.isBlank(who)) {
            throw new ConflictError("a day is planned for somebody", Facts.of("number", order.number()));
        }
        List<OrderLine> pending = order.pending();
        if (pending.isEmpty()) {
            throw new ConflictError("that order has nothing left on it", Facts.of("number", order.number()));
        }
        List<Stop> stops = route(pending, openings, rates);
        int bench = rates.benchMinutesPerCylinder() * stops.size();
        int travel = rates.travelMinutesPerBuilding() * buildingCount(stops);
        return new DayPlan(who, stops, bench, travel);
    }
}
