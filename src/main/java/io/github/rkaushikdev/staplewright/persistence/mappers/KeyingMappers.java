package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.persistence.models.GroupMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schedules and keying groups, between the tables and the domain objects.
 *
 * <p>Loading a schedule uses the loading door rather than the front door. Every line went through
 * add and assign once, years ago, under whatever rules were current then, and forcing a 2011 system
 * to pass today's policy on the way out of the database would mean the shop could not open half of
 * what it maintains. So rows come back through {@link KeyingSchedule#load}, and the review is where
 * anything wrong gets said out loud.
 */
public final class KeyingMappers {

    private static final Comparator<ScheduleEntryRow> SHALLOW_FIRST = Comparator.comparingInt(
                    (ScheduleEntryRow row) -> row.getSymbol().level())
            .thenComparing(row -> row.getSymbol().render());

    private KeyingMappers() {}

    /** A group's row and one member row per symbol, the members already listed on the group. */
    public record GroupRows(GroupRow group, List<GroupMemberRow> members) {

        public GroupRows {
            members = List.copyOf(members);
        }
    }

    /** A new line of {@code system}'s schedule, cut or not as the entry is. */
    public static ScheduleEntryRow entryToRow(ScheduleEntry entry, SystemRow system) {
        ScheduleEntryRow row = ScheduleEntryRow.of(system, entry.symbol(), entry.section());
        entry.bitting().ifPresent(row::setBitting);
        row.setNote(entry.note());
        return row;
    }

    /** The schedule line a row holds. */
    public static ScheduleEntry entryFromRow(ScheduleEntryRow row) {
        return new ScheduleEntry(row.getSymbol(), row.getSection(), row.getBitting(), row.getNote());
    }

    /** Every line, shallow symbols first so a reader can follow the tree. */
    public static List<ScheduleEntryRow> scheduleToRows(KeyingSchedule schedule, SystemRow system) {
        return schedule.entries().stream()
                .map(entry -> entryToRow(entry, system))
                .toList();
    }

    /** Rebuild a schedule from its rows, checking nothing on the way in. */
    public static KeyingSchedule scheduleFromRows(
            String name, RuleSet ruleset, KeywayFamily family, String topSection, List<ScheduleEntryRow> rows) {
        KeyingSchedule schedule = new KeyingSchedule(name, ruleset, family, topSection);
        for (ScheduleEntryRow row : rows.stream().sorted(SHALLOW_FIRST).toList()) {
            schedule.load(entryFromRow(row));
        }
        return schedule;
    }

    /** A new group row in {@code system} and a member row for each symbol, in schedule order. */
    public static GroupRows groupToRows(KeyingGroup group, SystemRow system) {
        GroupRow row = GroupRow.of(system, group.name());
        List<GroupMemberRow> members = group.ordered().stream()
                .map(symbol -> GroupMemberRow.of(row, symbol))
                .toList();
        return new GroupRows(row, members);
    }

    /** The group a row holds, its members in schedule order. */
    public static KeyingGroup groupFromRow(GroupRow row) {
        return new KeyingGroup(row.getName(), KeySymbol.sortSymbols(row.symbols()));
    }

    /** Every group in a system, keyed by name the way coverage wants them. */
    public static Map<String, KeyingGroup> groupsFromRows(List<GroupRow> rows) {
        Map<String, KeyingGroup> found = new LinkedHashMap<>();
        for (GroupRow row : rows) {
            found.put(row.getName(), groupFromRow(row));
        }
        return Collections.unmodifiableMap(found);
    }

    /** Just the symbols, in schedule order, for the cheap listings. */
    public static List<KeySymbol> symbolsIn(List<ScheduleEntryRow> rows) {
        return KeySymbol.sortSymbols(
                rows.stream().map(ScheduleEntryRow::getSymbol).toList());
    }
}
