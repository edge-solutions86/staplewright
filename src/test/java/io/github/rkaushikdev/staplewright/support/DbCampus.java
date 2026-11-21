package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A whole campus written into a database, for the service tests.
 *
 * <p>Buildings, doors, cylinders, a keying system with its groups, people and the keys they hold.
 * Everything the incident and reporting services need to answer a real question, built once here
 * rather than four times over, and written through the mappers so it is stored exactly as the
 * services would store it.
 */
public final class DbCampus {

    /** One key out over the counter: its tag, what it was cut to, and who signed for it. */
    public record Held(String tag, String symbol, String holder) {}

    /** The cut of every line in the system's schedule, in schedule order. */
    public static final Map<String, String> CUTS = cuts();

    /** The keys out, in the order they went over the counter. */
    public static final List<Held> HELD = List.of(
            new Held("41207", "AA1", "kmaki"),
            new Held("41208", "AA1", "dsalo"),
            new Held("41209", "AA", "rvirta"),
            new Held("41210", "A", "phamm"),
            new Held("41211", "GGM", "phamm"));

    /** The day every key went out, which is the day the people fixture works on. */
    public static final LocalDate TODAY = People.TODAY;

    /** The name of the keying system {@link #build} writes. */
    public static final String SYSTEM = "chester park";

    private DbCampus() {}

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("B", "242446");
        cuts.put("AA", "622446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        cuts.put("AB1", "662046");
        cuts.put("BA1", "246446");
        return Collections.unmodifiableMap(cuts);
    }

    /** Write the whole campus down and hand back the keying system. */
    public static SystemRow build(Session session) {
        ChartRow chart = ReferenceMappers.chartToRow(Charts.sixPinChart());
        session.add(chart);
        session.flush();

        KeywayFamily family = Systems.campusFamily();
        Map<String, KeywaySectionRow> sections = new HashMap<>();
        for (String code : family.codes().stream()
                .sorted(Comparator.comparingInt(family::depthOf))
                .toList()) {
            KeywaySectionRow row = family.parentOf(code)
                    .map(parent ->
                            ReferenceMappers.sectionToRow(family, family.section(code), sections.get(parent.code())))
                    .orElseGet(() -> ReferenceMappers.sectionToRow(family, family.section(code)));
            session.add(row);
            sections.put(code, row);
        }
        session.flush();

        SystemRow system = new SystemRow(SYSTEM, chart, "Sargent LA", "L");
        session.add(system);
        session.flush();

        CUTS.forEach((symbol, cut) ->
                session.add(ScheduleEntryRow.of(system, KeySymbol.parse(symbol), "LC", Bitting.parse(cut))));
        session.flush();

        Map<String, GroupRow> groups = new HashMap<>();
        Keyed.GROUPS.forEach((name, symbols) -> {
            GroupRow row = GroupRow.of(system, name);
            session.add(row);
            groups.put(name, row);
            for (String symbol : symbols) {
                session.add(GroupMemberRow.of(row, KeySymbol.parse(symbol)));
            }
        });
        session.flush();

        OpeningSchedule schedule = Campus.aCampus();
        for (Building building : schedule.buildings()) {
            session.add(CampusMappers.buildingToRow(building));
        }
        session.flush();
        for (Opening opening : schedule) {
            session.add(CampusMappers.openingToRow(opening));
        }
        session.flush();

        HardwareRegister register = Keyed.aRegister();
        Map<String, String> groupOf = new HashMap<>();
        for (Keyed.Installed installed : Keyed.INSTALLED) {
            groupOf.put(installed.serial(), installed.group());
        }
        for (Cylinder cylinder : register) {
            GroupRow group = groups.get(groupOf.getOrDefault(cylinder.serial(), ""));
            session.add(
                    group == null
                            ? CampusMappers.cylinderToRow(cylinder)
                            : CampusMappers.cylinderToRow(cylinder, group));
        }
        session.flush();

        Directory directory = People.aDirectory();
        for (Keyholder person : directory) {
            session.add(ControlMappers.keyholderToRow(person));
        }
        session.flush();
        for (Authorizer signer : directory.authorizers()) {
            session.add(ControlMappers.authorizerToRow(signer));
        }
        session.flush();

        for (Held held : HELD) {
            session.add(new IssuedKeyRow(held.tag(), held.symbol(), held.holder(), TODAY));
        }
        session.flush();
        return system;
    }
}
