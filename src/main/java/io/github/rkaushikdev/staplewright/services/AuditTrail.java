package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.AuditEntryRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Writing the audit trail, and reading it back the ways it gets read.
 *
 * <p>Two questions want different indexes. What happened to this key, in order, which is what
 * somebody asks when a key turns up somewhere it should not be. And what did this person do, in
 * order, which is what somebody asks after they have left.
 *
 * <p>Nothing in here deletes. A correction is another entry saying a correction was made, because
 * a trail with a hole in it is worth nothing at all.
 */
public final class AuditTrail {

    /** The kinds of thing an entry is about. */
    public static final String KEY = "key";

    public static final String SYMBOL = "symbol";
    public static final String CYLINDER = "cylinder";
    public static final String INCIDENT = "incident";
    public static final String SYSTEM = "system";
    public static final String REQUEST = "request";

    private static final String IN_ORDER = " order by e.happenedAt, e.id";

    private final Session session;

    public AuditTrail(Session session) {
        this.session = session;
    }

    public Session session() {
        return session;
    }

    /** Write one decision down, with no detail. */
    public AuditEntryRow record(String actor, String action, String subjectKind, String subjectId, LocalDateTime when) {
        return record(actor, action, subjectKind, subjectId, when, "");
    }

    /** Write one decision down. Nothing here is optional but the detail. */
    public AuditEntryRow record(
            String actor, String action, String subjectKind, String subjectId, LocalDateTime when, String detail) {
        if (Text.isBlank(actor)) {
            throw new InputError("an audit entry names who did it", Facts.of("action", action));
        }
        if (Text.isBlank(action)) {
            throw new InputError("an audit entry names what was done", Facts.of("actor", actor));
        }
        if (Text.isBlank(subjectId)) {
            throw new InputError("an audit entry names what it was done to", Facts.of("action", action));
        }
        AuditEntryRow row = new AuditEntryRow(when, actor, action, subjectKind, subjectId);
        row.setDetail(detail);
        session.add(row);
        session.flush();
        return row;
    }

    /** Everything that happened to one thing, oldest first. */
    public List<AuditEntryRow> about(String subjectKind, String subjectId) {
        return session.list(
                "select e from AuditEntryRow e where e.subjectKind = ?1 and e.subjectId = ?2" + IN_ORDER,
                AuditEntryRow.class,
                subjectKind,
                subjectId);
    }

    /** Everything one person did, oldest first. */
    public List<AuditEntryRow> byActor(String actor) {
        return session.list("select e from AuditEntryRow e where e.actor = ?1" + IN_ORDER, AuditEntryRow.class, actor);
    }

    /** Everything that happened between two moments, both ends included. */
    public List<AuditEntryRow> between(LocalDateTime start, LocalDateTime end) {
        if (end.isBefore(start)) {
            throw new InputError("a window runs forward");
        }
        return session.list(
                "select e from AuditEntryRow e where e.happenedAt >= ?1 and e.happenedAt <= ?2" + IN_ORDER,
                AuditEntryRow.class,
                start,
                end);
    }

    /** Every time one thing was done, oldest first. */
    public List<AuditEntryRow> ofAction(String action) {
        return session.list(
                "select e from AuditEntryRow e where e.action = ?1" + IN_ORDER, AuditEntryRow.class, action);
    }

    /** Everybody who has done anything, once each, sorted. */
    public List<String> actors() {
        return session.list("select distinct e.actor from AuditEntryRow e order by e.actor", String.class);
    }

    public int count() {
        return session.all(AuditEntryRow.class).size();
    }

    /** The trail as a person reads it. */
    public List<String> lines(List<AuditEntryRow> rows) {
        return rows.stream().map(AuditEntryRow::describe).toList();
    }
}
