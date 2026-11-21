package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import java.util.List;
import java.util.Optional;

/** Keying systems by name, and the schedule lines and groups inside one. */
public final class SystemRepository extends Repository<SystemRow> {

    public SystemRepository(Session session) {
        super(session, SystemRow.class, "keying system", Sort.ascending("name"));
    }

    public Optional<SystemRow> byName(String name) {
        return one(Where.equal("name", name));
    }

    /** The systems still in use, which is every one not archived. */
    public List<SystemRow> live() {
        return rows(Where.isFalse("archived"));
    }

    /** Every line of one system's schedule. */
    public List<ScheduleEntryRow> entries(int systemId) {
        return rowsOf(ScheduleEntryRow.class, Where.equal("system.id", systemId));
    }

    /** One system's keying groups, by name. */
    public List<GroupRow> groups(int systemId) {
        return rowsOf(GroupRow.class, List.of(Sort.ascending("name")), Where.equal("system.id", systemId));
    }

    /** The lines of one system's schedule that have not been cut to a bitting yet. */
    public List<ScheduleEntryRow> uncutSymbols(int systemId) {
        return rowsOf(ScheduleEntryRow.class, Where.equal("system.id", systemId), Where.isNull("bitting"));
    }
}
