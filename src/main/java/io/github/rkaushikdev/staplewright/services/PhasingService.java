package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.rekey.Door;
import io.github.rkaushikdev.staplewright.rekey.Phasing;
import io.github.rkaushikdev.staplewright.rekey.Placement;
import io.github.rkaushikdev.staplewright.rekey.Restriction;
import io.github.rkaushikdev.staplewright.rekey.Window;
import io.github.rkaushikdev.staplewright.rekey.WindowKind;
import io.github.rkaushikdev.staplewright.rekey.WorkCalendar;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * The campus, the calendar, and which door happens when.
 *
 * <p>The phasing rules know nothing about buildings, and that is deliberate: they take doors and
 * windows and do arithmetic. What they cannot do is decide that a residence hall room is occupied
 * and a plant room is not, because that is a fact about this campus and it lives in the opening
 * schedule.
 *
 * <p>So this is the translation layer, and it is the only place where a door function turns into a
 * calendar restriction. The mapping is one table, {@link #RESTRICTION_BY_FUNCTION}, rather than a
 * chain of conditions, because somebody will disagree with it within a year and the argument should
 * be about one table.
 */
public final class PhasingService {

    /**
     * What each door function means for the calendar, on this campus.
     *
     * <p>Offices count as occupied because somebody is behind the door and wants warning. A store
     * room does not, because nobody is in it, and a store room that somebody is living in is a
     * different conversation. A function the table does not name stops nothing.
     */
    public static final Map<DoorFunction, Restriction> RESTRICTION_BY_FUNCTION = restrictions();

    /** Doors a crew gets through in a day, unless somebody says otherwise. */
    public static final int DEFAULT_RATE = 12;

    /** Minutes one door takes, unless somebody says otherwise. */
    public static final int MINUTES_PER_DOOR = 25;

    private static final List<String> UNFINISHED = List.of("drafted", "scheduled", "started");

    private final Session session;
    private final WorkCalendar calendar;

    /** A service planning against an empty calendar, to be filled with {@link #addWindow}. */
    public PhasingService(Session session) {
        this(session, new WorkCalendar());
    }

    public PhasingService(Session session, WorkCalendar calendar) {
        this.session = session;
        this.calendar = calendar;
    }

    /**
     * How many doors there are against how many the year can take.
     *
     * @param wanted the doors to be done
     * @param room the doors the calendar's windows can take between them
     */
    public record Demand(int wanted, int room) {}

    private static Map<DoorFunction, Restriction> restrictions() {
        Map<DoorFunction, Restriction> found = new EnumMap<>(DoorFunction.class);
        found.put(DoorFunction.CLASSROOM, Restriction.TEACHING);
        found.put(DoorFunction.OFFICE, Restriction.OCCUPIED);
        found.put(DoorFunction.ENTRANCE, Restriction.NONE);
        found.put(DoorFunction.STOREROOM, Restriction.NONE);
        found.put(DoorFunction.PADLOCK, Restriction.NONE);
        found.put(DoorFunction.CABINET, Restriction.NONE);
        found.put(DoorFunction.PRIVACY, Restriction.OCCUPIED);
        return Collections.unmodifiableMap(found);
    }

    /** What stops this door being done today. */
    public static Restriction restrictionFor(Opening opening) {
        return RESTRICTION_BY_FUNCTION.getOrDefault(opening.function(), Restriction.NONE);
    }

    /** The opening as a door to place, taking the standard minutes. */
    public static Door doorFor(Opening opening) {
        return doorFor(opening, MINUTES_PER_DOOR);
    }

    /** The opening as a door to place, taking the minutes given. */
    public static Door doorFor(Opening opening, int minutes) {
        return new Door(opening.number(), opening.building(), restrictionFor(opening), minutes);
    }

    public Session session() {
        return session;
    }

    /** The calendar the plans are made against. */
    public WorkCalendar calendar() {
        return calendar;
    }

    /** Add a window at the standard rate that takes only unrestricted work. */
    public Window addWindow(String name, WindowKind kind, LocalDate opensOn, LocalDate closesOn) {
        return addWindow(name, kind, opensOn, closesOn, DEFAULT_RATE, List.of());
    }

    /** Add a window at a given rate that takes only unrestricted work. */
    public Window addWindow(String name, WindowKind kind, LocalDate opensOn, LocalDate closesOn, int doorsPerDay) {
        return addWindow(name, kind, opensOn, closesOn, doorsPerDay, List.of());
    }

    /** Add a window to the calendar, with the restrictions it can work around. */
    public Window addWindow(
            String name,
            WindowKind kind,
            LocalDate opensOn,
            LocalDate closesOn,
            int doorsPerDay,
            Collection<Restriction> allows) {
        return calendar.add(new Window(name, kind, opensOn, closesOn, doorsPerDay), allows);
    }

    /**
     * Every keyed door on a building, in number order.
     *
     * @throws NotFoundError when the building has no keyed door at all
     */
    public List<Door> doorsIn(String building) {
        List<Door> found = Loaders.loadOpenings(session).inBuilding(building).stream()
                .filter(Opening::isKeyed)
                .map(PhasingService::doorFor)
                .toList();
        if (found.isEmpty()) {
            throw new NotFoundError("no keyed doors on that building", Facts.of("building", building));
        }
        return inNumberOrder(found);
    }

    /**
     * The doors a work order covers, which is the usual way in. A line with no opening is left out.
     *
     * @throws NotFoundError for an order nobody raised
     */
    public List<Door> doorsOn(String orderNumber) {
        WorkOrderRow row = session.get(WorkOrderRow.class, orderNumber)
                .orElseThrow(() -> new NotFoundError("no such work order", Facts.of("order", orderNumber)));
        OpeningSchedule schedule = Loaders.loadOpenings(session);
        List<Door> found = new ArrayList<>();
        for (WorkOrderLineRow line : row.getLines()) {
            if (line.getOpeningNumber().isEmpty()) {
                continue;
            }
            found.add(doorFor(schedule.opening(line.getOpeningNumber())));
        }
        return inNumberOrder(found);
    }

    /** Every keyed door on the campus, in number order. */
    public List<Door> everyKeyedDoor() {
        return inNumberOrder(Loaders.loadOpenings(session).keyedOpenings().stream()
                .map(PhasingService::doorFor)
                .toList());
    }

    public Phasing plan(Collection<Door> doors) {
        return Phasing.phase(calendar, doors);
    }

    public Phasing planBuilding(String building) {
        return plan(doorsIn(building));
    }

    public Phasing planOrder(String orderNumber) {
        return plan(doorsOn(orderNumber));
    }

    public Phasing planCampus() {
        return plan(everyKeyedDoor());
    }

    /** The year plan for these doors, as the page that goes to the facilities meeting. */
    public List<String> sheet(Collection<Door> doors) {
        return Phasing.yearPlan(calendar, doors);
    }

    /** The doors that do not fit this year, for next year. */
    public List<Door> nextYear(Collection<Door> doors) {
        return plan(doors).carryOver();
    }

    /** What a crew does on one day, in the order they walk it. */
    public List<String> daySheet(Collection<Door> doors, LocalDate day) {
        List<Placement> found = plan(doors).onDay(day);
        if (found.isEmpty()) {
            return List.of("nothing booked for " + day);
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " doors on " + day);
        found.stream()
                .sorted(Comparator.comparing(item -> item.door().number()))
                .forEach(item -> lines.add(
                        "  " + Text.left(item.door().number(), 12) + item.door().building()));
        return List.copyOf(lines);
    }

    /** The windows, in date order. */
    public List<Window> windows() {
        return calendar.inOrder();
    }

    /** How many doors there are against how many the year can take. */
    public Demand capacityAgainst(Collection<Door> doors) {
        return new Demand(doors.size(), calendar.totalCapacity());
    }

    /** The four windows a campus year has, at the standard rate. */
    public static WorkCalendar academicYear(int starting) {
        return academicYear(starting, DEFAULT_RATE);
    }

    /**
     * The four windows a campus year actually has, which is not many.
     *
     * <p>These are the windows the shop gets, not the windows it would like. The long summer one is
     * the only one where residence work happens at all, and the fortnight after finals is the only
     * one where teaching rooms open up. The reading week runs at half the rate, and never below one
     * door a day.
     *
     * @param starting the year the summer window falls in
     * @param rate doors a crew gets through in a day
     */
    public static WorkCalendar academicYear(int starting, int rate) {
        int half = Math.floorDiv(rate, 2);
        WorkCalendar calendar = new WorkCalendar();
        calendar.add(
                new Window(
                        "summer",
                        WindowKind.VACATION,
                        LocalDate.of(starting, 6, 1),
                        LocalDate.of(starting, 8, 20),
                        rate),
                List.of(Restriction.OCCUPIED, Restriction.TEACHING, Restriction.RESEARCH));
        calendar.add(
                new Window(
                        "reading week",
                        WindowKind.TERM,
                        LocalDate.of(starting, 10, 20),
                        LocalDate.of(starting, 10, 26),
                        half == 0 ? 1 : half),
                List.of(Restriction.TEACHING));
        calendar.add(
                new Window(
                        "winter shutdown",
                        WindowKind.SHUTDOWN,
                        LocalDate.of(starting, 12, 20),
                        LocalDate.of(starting + 1, 1, 3),
                        rate),
                List.of(Restriction.OCCUPIED, Restriction.TEACHING));
        calendar.add(
                new Window(
                        "spring break",
                        WindowKind.VACATION,
                        LocalDate.of(starting + 1, 3, 14),
                        LocalDate.of(starting + 1, 3, 22),
                        rate),
                List.of(Restriction.TEACHING));
        return calendar;
    }

    /** Every building that has a keyed door, in code order, for the planning menu. */
    public static List<String> buildingsWithWork(Session session) {
        TreeSet<String> found = new TreeSet<>();
        Loaders.loadOpenings(session).keyedOpenings().forEach(opening -> found.add(opening.building()));
        return List.copyOf(found);
    }

    /** Work orders that are not finished, oldest first. */
    public static List<String> ordersToPlan(Session session) {
        return session.list(
                "select o.number from WorkOrderRow o where o.state in ?1 order by o.number", String.class, UNFINISHED);
    }

    private static List<Door> inNumberOrder(List<Door> doors) {
        return doors.stream().sorted(Comparator.comparing(Door::number)).toList();
    }
}
