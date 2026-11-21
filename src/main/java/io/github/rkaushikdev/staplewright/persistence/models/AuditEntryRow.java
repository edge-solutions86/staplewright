package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.util.Text;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * One thing somebody did, written down when they did it.
 *
 * <p>The audit trail is the only record of who decided something. Every other table holds the
 * current state: the schedule says what a symbol is cut to now, the ledger says who holds a key
 * now, and none of them say who changed it or when. That is fine until an auditor asks why a grand
 * master was issued to a contractor in March, and the answer has to be better than the row that
 * exists today.
 *
 * <p>So the trail is append only, and it is written by the services rather than by the database. A
 * trigger would catch more, and it would also record the rollback of a transaction nobody meant to
 * run, which is noise. What is recorded here is a decision somebody made, by somebody named.
 */
@Entity
@Table(
        name = "audit_entries",
        indexes = {
            @Index(name = "ix_audit_entries_subject", columnList = "subject_kind, subject_id"),
            @Index(name = "ix_audit_entries_happened_at", columnList = "happened_at")
        })
public class AuditEntryRow {

    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm", Locale.ROOT);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false)
    private LocalDateTime happenedAt;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String actor;

    @Column(nullable = false, length = Schema.SHORT)
    private String action;

    @Column(nullable = false, length = Schema.SHORT)
    private String subjectKind;

    @Column(nullable = false, length = Schema.SHORT)
    private String subjectId;

    @Column(nullable = false, length = Schema.LONG)
    private String detail = "";

    protected AuditEntryRow() {}

    /** One decision, with no detail until set. */
    public AuditEntryRow(LocalDateTime happenedAt, String actor, String action, String subjectKind, String subjectId) {
        this.happenedAt = happenedAt;
        this.actor = actor;
        this.action = action;
        this.subjectKind = subjectKind;
        this.subjectId = subjectId;
    }

    public Integer getId() {
        return id;
    }

    public LocalDateTime getHappenedAt() {
        return happenedAt;
    }

    public String getActor() {
        return actor;
    }

    public String getAction() {
        return action;
    }

    /** What sort of thing the decision was about: a key, a system, a box. */
    public String getSubjectKind() {
        return subjectKind;
    }

    /** Which one of them, by its tag, name or number. */
    public String getSubjectId() {
        return subjectId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    /** The subject as one word, its kind and its identifier: {@code key/41207}. */
    public String subject() {
        return subjectKind + "/" + subjectId;
    }

    /** One line of the printed trail: when to the minute, who, what, and to what. */
    public String describe() {
        return MINUTES.format(happenedAt) + "  " + Text.left(actor, 12) + Text.left(action, 16) + subject();
    }

    @Override
    public String toString() {
        return "<AuditEntryRow " + action + " " + subject() + ">";
    }
}
