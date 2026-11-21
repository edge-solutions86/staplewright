package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.CylinderFormat;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.control.Affiliation;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.Standing;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.services.EscrowService;
import io.github.rkaushikdev.staplewright.services.StockService;
import io.github.rkaushikdev.staplewright.spec.Catalog;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A worked example campus, for the half hour after somebody installs this.
 *
 * <p>An empty database is the worst way to meet a library like this. Nothing has a symbol, so nothing
 * can be cut, so nothing can be pinned, and the first thing anybody does is type a keying system in by
 * hand to see whether any of it works. This builds one instead.
 *
 * <p>It is a small campus and it is deliberately not a tidy one. There is a building on a keyway whose
 * patent has run out, a key out to somebody whose appointment ended, a keyed door with nothing in it,
 * and a stocktake that came up twelve blanks short. Every one of those is something the self check
 * finds, and a demo where the self check finds nothing teaches nobody anything.
 */
public final class Seed {

    /** The keyway family every section of the campus belongs to. */
    public static final String FAMILY = "Sargent LA";

    /** The keying system the example writes. */
    public static final String SYSTEM = "chester park";

    /** The day the keying system was opened. */
    public static final LocalDate OPENED = LocalDate.of(2024, 8, 19);

    /** The cut of every symbol on the schedule, in schedule order. */
    public static final Map<String, String> CUTS = cuts();

    /** The keying groups and the symbols that open each, in the order they are written. */
    public static final Map<String, List<String>> GROUPS = groups();

    /** One building: its code, its name, and the year it went up. */
    record BuildingLine(String code, String name, int built) {}

    /** One door, where it is, what it does, and whether it faces out. */
    record OpeningLine(
            String number,
            String building,
            String floor,
            String room,
            DoorFunction function,
            String note,
            boolean exterior) {}

    /** One cylinder, the door it is in if any, and its keying group. */
    record CylinderLine(String serial, Optional<String> opening, Optional<Position> position, String group) {}

    /** One keyholder, and the day their record runs out if it does. */
    record PersonLine(
            String identifier,
            String name,
            Affiliation affiliation,
            String department,
            Standing standing,
            Optional<LocalDate> ends) {}

    /** One key out: its tag, what it opens, and who signed for it. */
    record HeldLine(String tag, String symbol, String holder) {}

    /** One part number in the drawer, its section, and how many the shop keeps. */
    record BlankLine(String part, String section, boolean restricted, int reorderAt, int held) {}

    static final List<BuildingLine> BUILDINGS = List.of(
            new BuildingLine("MOO", "Moore Hall", 1953),
            new BuildingLine("VOS", "Voss Hall", 1971),
            new BuildingLine("HEI", "Heikkinen Shops", 1938));

    static final List<OpeningLine> OPENINGS = List.of(
            new OpeningLine("MOO-101", "MOO", "1", "101", DoorFunction.ENTRANCE, "north door", true),
            new OpeningLine("MOO-110", "MOO", "1", "110", DoorFunction.CLASSROOM, "", false),
            new OpeningLine("MOO-214", "MOO", "2", "214", DoorFunction.OFFICE, "", false),
            new OpeningLine("MOO-216", "MOO", "2", "216", DoorFunction.OFFICE, "", false),
            new OpeningLine("MOO-218", "MOO", "2", "218B", DoorFunction.STOREROOM, "map room", false),
            new OpeningLine("MOO-260", "MOO", "2", "260", DoorFunction.PRIVACY, "staff washroom", false),
            new OpeningLine("VOS-100", "VOS", "1", "100", DoorFunction.ENTRANCE, "lake side", true),
            new OpeningLine("VOS-311", "VOS", "3", "311", DoorFunction.OFFICE, "", false),
            new OpeningLine("VOS-312", "VOS", "3", "312", DoorFunction.OFFICE, "", false),
            new OpeningLine("HEI-001", "HEI", "B", "001", DoorFunction.STOREROOM, "paint cage", false),
            new OpeningLine("HEI-G12", "HEI", "1", "G12", DoorFunction.PADLOCK, "salt shed gate", false));

    static final List<CylinderLine> CYLINDERS = List.of(
            installed("C-1001", "MOO-101", "moore entrance"),
            installed("C-1002", "MOO-214", "moore offices"),
            installed("C-1003", "MOO-216", "moore offices"),
            installed("C-1004", "VOS-100", "voss offices"),
            installed("C-1005", "VOS-311", "voss offices"),
            installed("C-1006", "HEI-001", "shops"),
            new CylinderLine("C-1007", Optional.empty(), Optional.empty(), "shops"));

    static final List<PersonLine> PEOPLE = List.of(
            staff("kmaki", "K Maki", "facilities"),
            staff("dsalo", "D Salo", "facilities"),
            staff("rvirta", "R Virta", "registrar"),
            staff("phamm", "P Hamm", "facilities"),
            new PersonLine(
                    "jkoivu",
                    "J Koivu",
                    Affiliation.CONTRACTOR,
                    "facilities",
                    Standing.ACTIVE,
                    Optional.of(LocalDate.of(2026, 6, 30))),
            new PersonLine(
                    "ovirta",
                    "O Virta",
                    Affiliation.STUDENT,
                    "registrar",
                    Standing.ACTIVE,
                    Optional.of(LocalDate.of(2025, 2, 1))));

    static final List<HeldLine> HELD = List.of(
            new HeldLine("41207", "AA1", "kmaki"),
            new HeldLine("41208", "AA1", "dsalo"),
            new HeldLine("41209", "AA", "rvirta"),
            new HeldLine("41210", "A", "phamm"),
            new HeldLine("41211", "GGM", "phamm"),
            new HeldLine("41212", "BA1", "ovirta"));

    static final List<BlankLine> BLANKS =
            List.of(new BlankLine("1145A", "LC", true, 50, 200), new BlankLine("RA4", "L", false, 20, 40));

    private Seed() {}

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("B", "242446");
        cuts.put("AA", "622446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        cuts.put("AB", "662446");
        cuts.put("AB1", "662046");
        cuts.put("BA", "246446");
        cuts.put("BA1", "246046");
        return Collections.unmodifiableMap(cuts);
    }

    private static Map<String, List<String>> groups() {
        Map<String, List<String>> groups = new LinkedHashMap<>();
        groups.put("moore offices", List.of("GGM", "A", "AA", "AA1"));
        groups.put("moore entrance", List.of("GGM", "A"));
        groups.put("voss offices", List.of("GGM", "B", "BA", "BA1"));
        groups.put("shops", List.of("GGM"));
        return Collections.unmodifiableMap(groups);
    }

    private static CylinderLine installed(String serial, String opening, String group) {
        return new CylinderLine(serial, Optional.of(opening), Optional.of(Position.LOCKSET), group);
    }

    private static PersonLine staff(String identifier, String name, String department) {
        return new PersonLine(identifier, name, Affiliation.STAFF, department, Standing.ACTIVE, Optional.empty());
    }

    /** The family, with one section whose patent has already run out. */
    public static KeywayFamily demoFamily() {
        KeywayFamily family = new KeywayFamily(FAMILY, new KeywaySection("L", "Sargent"));
        family.add(new KeywaySection("LA", "Sargent"), "L");
        family.add(new KeywaySection("LB", "Sargent"), "L");
        family.add(new KeywaySection("LC", "Sargent", true, Optional.of("2031-04-01")), "LA");
        family.add(new KeywaySection("LD", "Sargent", true, Optional.of("2023-11-30")), "LA");
        family.add(new KeywaySection("LE", "Sargent"), "LB");
        return family;
    }

    /** Write the whole worked example down as of today and hand back the keying system. */
    public static SystemRow seed(Session session) {
        return seed(session, LocalDate.now());
    }

    /**
     * Write the whole worked example down and hand back the keying system.
     *
     * <p>Refuses on a database that already has a system in it, because seeding over live data is the
     * sort of thing that gets done once at three in the afternoon and found out about at nine the next
     * morning.
     *
     * @throws ConflictError when the database already holds a keying system
     */
    public static SystemRow seed(Session session, LocalDate today) {
        if (session.get(SystemRow.class, 1).isPresent()) {
            throw new ConflictError("there is already a keying system in this database");
        }
        Map<String, KeywaySectionRow> sections = sections(session);
        SystemRow system = system(session);
        Map<String, GroupRow> groups = groups(session, system);
        campus(session, groups);
        people(session, today);
        stock(session, sections, today);
        boxes(session, today);
        return system;
    }

    /** What the demo campus contains, for the line printed after seeding. */
    public static List<String> summary() {
        return List.of(
                BUILDINGS.size() + " buildings, " + OPENINGS.size() + " openings, " + CYLINDERS.size() + " cylinders",
                CUTS.size() + " symbols on " + SYSTEM + ", " + GROUPS.size() + " keying groups",
                PEOPLE.size() + " keyholders holding " + HELD.size() + " keys",
                BLANKS.size() + " blank part numbers and two sealed boxes",
                "the self check finds several things wrong on purpose");
    }

    private static Map<String, KeywaySectionRow> sections(Session session) {
        KeywayFamily family = demoFamily();
        Map<String, KeywaySectionRow> found = new HashMap<>();
        for (String code : family.codes().stream()
                .sorted(Comparator.comparingInt(family::depthOf))
                .toList()) {
            KeywaySection section = family.section(code);
            KeywaySectionRow row = family.parentOf(code)
                    .map(parent -> ReferenceMappers.sectionToRow(family, section, found.get(parent.code())))
                    .orElseGet(() -> ReferenceMappers.sectionToRow(family, section));
            session.add(row);
            session.flush();
            found.put(code, row);
        }
        return found;
    }

    private static SystemRow system(Session session) {
        ChartRow chart = ReferenceMappers.chartToRow(Catalog.SARGENT_LA);
        session.add(chart);
        session.flush();
        SystemRow row = new SystemRow(SYSTEM, chart, FAMILY, "L");
        row.setOpenedOn(OPENED);
        session.add(row);
        session.flush();
        CUTS.forEach((symbol, cut) ->
                session.add(ScheduleEntryRow.of(row, KeySymbol.parse(symbol), "LC", Bitting.parse(cut))));
        session.flush();
        return row;
    }

    private static Map<String, GroupRow> groups(Session session, SystemRow system) {
        Map<String, GroupRow> found = new HashMap<>();
        GROUPS.forEach((name, symbols) -> {
            GroupRow row = GroupRow.of(system, name);
            session.add(row);
            session.flush();
            found.put(name, row);
            for (String symbol : symbols) {
                session.add(GroupMemberRow.of(row, KeySymbol.parse(symbol)));
            }
        });
        session.flush();
        return found;
    }

    private static void campus(Session session, Map<String, GroupRow> groups) {
        for (BuildingLine line : BUILDINGS) {
            session.add(CampusMappers.buildingToRow(new Building(line.code(), line.name(), line.built())));
        }
        session.flush();
        for (OpeningLine line : OPENINGS) {
            session.add(CampusMappers.openingToRow(new Opening(
                    line.number(),
                    line.building(),
                    line.floor(),
                    line.room(),
                    line.function(),
                    line.note(),
                    line.exterior())));
        }
        session.flush();
        for (CylinderLine line : CYLINDERS) {
            Cylinder cylinder = new Cylinder(
                    line.serial(),
                    CylinderFormat.MORTISE,
                    "LC",
                    6,
                    "",
                    false,
                    line.opening(),
                    line.position(),
                    line.group());
            GroupRow group = groups.get(line.group());
            session.add(
                    group == null
                            ? CampusMappers.cylinderToRow(cylinder)
                            : CampusMappers.cylinderToRow(cylinder, group));
        }
        session.flush();
    }

    private static void people(Session session, LocalDate today) {
        for (PersonLine line : PEOPLE) {
            session.add(ControlMappers.keyholderToRow(new Keyholder(
                    line.identifier(),
                    line.name(),
                    line.affiliation(),
                    line.department(),
                    line.standing(),
                    line.ends(),
                    Optional.empty())));
        }
        session.flush();
        for (HeldLine line : HELD) {
            IssuedKeyRow row = new IssuedKeyRow(line.tag(), line.symbol(), line.holder(), today.minusDays(200));
            row.setDueOn(today.plusDays(165));
            row.setState("out");
            session.add(row);
        }
        session.flush();
    }

    private static void stock(Session session, Map<String, KeywaySectionRow> sections, LocalDate today) {
        for (BlankLine line : BLANKS) {
            BlankRow row = new BlankRow(line.part(), sections.get(line.section()), "Sargent", 6);
            row.setRestricted(line.restricted());
            row.setReorderAt(line.reorderAt());
            session.add(row);
        }
        session.flush();
        StockService stock = new StockService(session);
        for (BlankLine line : BLANKS) {
            stock.receive(line.part(), line.held(), today.minusDays(120), "PO-4412");
        }
        stock.cut("1145A", 6, today.minusDays(60), "41207");
        stock.count("1145A", 182, today.minusDays(10));
    }

    private static void boxes(Session session, LocalDate today) {
        EscrowService boxes = new EscrowService(session, new BoxPolicy());
        boxes.install("KB-01", "MOO", "MOO-101", KeySymbol.parse("AA1"), "44012", today.minusDays(300));
        boxes.install("KB-02", "VOS", "VOS-100", KeySymbol.parse("BA1"), "44013", today.minusDays(40));
    }
}
