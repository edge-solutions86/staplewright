package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.KeyingMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers;
import io.github.rkaushikdev.staplewright.persistence.models.AuthorizerRow;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.rekey.Quarantine;
import io.github.rkaushikdev.staplewright.rekey.RetirementReason;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rebuilding whole domain objects out of a session, in one place.
 *
 * <p>Two services had each grown a private method that read the same four tables and built the
 * same coverage map, and they had already started to differ: one of them passed the group names in
 * and the other did not, so a cylinder looked unmapped from one side and keyed from the other.
 *
 * <p>So the loaders live here. They are the only code that knows both a session and a domain
 * object, which is the seam a repository and a mapper leave between them.
 */
public final class Loaders {

    private Loaders() {}

    /** Every building and opening, as one schedule. */
    public static OpeningSchedule loadOpenings(Session session) {
        return CampusMappers.scheduleFromRows(session.all(BuildingRow.class), session.all(OpeningRow.class));
    }

    /** Each keying group's name against the key of its row. */
    public static Map<Integer, String> loadGroupNames(Session session) {
        Map<Integer, String> names = new LinkedHashMap<>();
        for (GroupRow row : session.all(GroupRow.class)) {
            names.put(row.getId(), row.getName());
        }
        return Collections.unmodifiableMap(names);
    }

    /** Every keying group, keyed by name. */
    public static Map<String, KeyingGroup> loadGroups(Session session) {
        return KeyingMappers.groupsFromRows(session.all(GroupRow.class));
    }

    /** Every cylinder back where it was, pinned to its group's name. */
    public static HardwareRegister loadRegister(Session session) {
        return CampusMappers.registerFromRows(
                loadOpenings(session), session.all(CylinderRow.class), loadGroupNames(session));
    }

    /** Cylinders, their groups, and the doors those groups sit in. */
    public static CoverageMap loadCoverage(Session session) {
        return new CoverageMap(loadRegister(session), loadGroups(session));
    }

    /** Every keyholder and every signature. */
    public static Directory loadDirectory(Session session) {
        return ControlMappers.directoryFromRows(session.all(KeyholderRow.class), session.all(AuthorizerRow.class));
    }

    /** Every key ever issued, against the directory. */
    public static KeyLedger loadLedger(Session session) {
        return ControlMappers.ledgerFromRows(loadDirectory(session), session.all(IssuedKeyRow.class));
    }

    /** Every deposit line. */
    public static DepositBook loadDeposits(Session session) {
        return ControlMappers.depositBookFromRows(session.all(DepositLineRow.class));
    }

    /**
     * The keyway family stored under {@code familyName}.
     *
     * @throws io.github.rkaushikdev.staplewright.KeywayError when nothing is stored under the name,
     *     which reads as a family with no root
     */
    public static KeywayFamily loadFamily(Session session, String familyName) {
        List<KeywaySectionRow> rows = session.list(
                "select r from KeywaySectionRow r where r.familyName = ?1", KeywaySectionRow.class, familyName);
        return ReferenceMappers.familyFromRows(familyName, rows);
    }

    /** A system's schedule under the standing cutting rules. */
    public static KeyingSchedule loadSchedule(Session session, SystemRow system) {
        return loadSchedule(session, system, new CuttingRules());
    }

    /** A system's schedule on its own chart and family, under {@code rules}. */
    public static KeyingSchedule loadSchedule(Session session, SystemRow system, CuttingRules rules) {
        List<ScheduleEntryRow> rows = session.list(
                "select r from ScheduleEntryRow r where r.system.id = ?1", ScheduleEntryRow.class, system.getId());
        RuleSet ruleset = new RuleSet(ReferenceMappers.chartFromRow(system.getChart()), rules);
        return KeyingMappers.scheduleFromRows(
                system.getName(), ruleset, loadFamily(session, system.getFamilyName()), system.getTopSection(), rows);
    }

    /** Every retired cut, released after the standing number of years. */
    public static Quarantine loadQuarantine(Session session) {
        return loadQuarantine(session, Quarantine.RELEASE_AFTER_YEARS);
    }

    /** Every retired cut, released after {@code releaseAfterYears}. */
    public static Quarantine loadQuarantine(Session session, int releaseAfterYears) {
        Quarantine hold = new Quarantine(releaseAfterYears);
        for (RetiredBittingRow row : session.all(RetiredBittingRow.class)) {
            hold.retire(
                    row.getBitting(),
                    RetirementReason.fromValue(row.getReason()),
                    row.getRetiredOn(),
                    row.getSymbol(),
                    row.getNote());
        }
        return hold;
    }
}
