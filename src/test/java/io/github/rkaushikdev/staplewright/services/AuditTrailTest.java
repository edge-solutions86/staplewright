package io.github.rkaushikdev.staplewright.services;

import static io.github.rkaushikdev.staplewright.services.AuditTrail.KEY;
import static io.github.rkaushikdev.staplewright.services.AuditTrail.SYMBOL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.AuditEntryRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AuditTrailTest {

    private static final LocalDateTime MORNING = LocalDateTime.of(2025, 4, 8, 9, 12);
    private static final LocalDateTime NOON = LocalDateTime.of(2025, 4, 8, 12, 30);
    private static final LocalDateTime EVENING = LocalDateTime.of(2025, 4, 8, 17, 5);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private AuditTrail aTrail() {
        AuditTrail trail = new AuditTrail(session);
        trail.record("rvirta", "issued", KEY, "41207", MORNING, "against R-0001");
        trail.record("rvirta", "returned", KEY, "41207", EVENING);
        trail.record("phamm", "cut", SYMBOL, "AA1", NOON);
        return trail;
    }

    private static List<String> actions(List<AuditEntryRow> rows) {
        return rows.stream().map(AuditEntryRow::getAction).toList();
    }

    @Test
    void theAuditTableIsOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("audit_entries");
    }

    @Test
    void anEntryRecordsWhoDidWhatToWhich() {
        AuditEntryRow row = new AuditTrail(session).record("rvirta", "issued", KEY, "41207", MORNING);
        assertThat(row.getActor()).isEqualTo("rvirta");
        assertThat(row.subject()).isEqualTo("key/41207");
        assertThat(row.toString()).contains("issued");
    }

    @Test
    void anEntryDescribesItselfForAPrintedTrail() {
        AuditEntryRow row = new AuditTrail(session).record("rvirta", "issued", KEY, "41207", MORNING);
        assertThat(row.describe()).startsWith("2025-04-08 09:12").contains("key/41207");
    }

    @ParameterizedTest
    @ValueSource(strings = {"actor", "action", "subject_id"})
    void anEntryThatNamesNothingIsRefused(String blank) {
        String actor = blank.equals("actor") ? "  " : "rvirta";
        String action = blank.equals("action") ? "  " : "issued";
        String subject = blank.equals("subject_id") ? "  " : "41207";
        assertThatThrownBy(() -> new AuditTrail(session).record(actor, action, KEY, subject, MORNING))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theDatabaseRefusesAnUnnamedActorToo() {
        assertThatThrownBy(() -> {
                    session.add(new AuditEntryRow(MORNING, "", "issued", KEY, "41207"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void everythingAboutOneKeyComesBackInOrder() {
        assertThat(actions(aTrail().about(KEY, "41207"))).containsExactly("issued", "returned");
    }

    @Test
    void aSubjectNobodyTouchedHasNoTrail() {
        assertThat(aTrail().about(KEY, "99999")).isEmpty();
    }

    @Test
    void everythingOnePersonDidComesBackInOrder() {
        AuditTrail trail = aTrail();
        assertThat(actions(trail.byActor("rvirta"))).containsExactly("issued", "returned");
        assertThat(trail.byActor("nobody")).isEmpty();
    }

    @Test
    void aWindowReturnsWhatHappenedInsideIt() {
        assertThat(actions(aTrail().between(MORNING, NOON))).containsExactly("issued", "cut");
    }

    @Test
    void aWindowThatRunsBackwardIsRefused() {
        AuditTrail trail = aTrail();
        assertThatThrownBy(() -> trail.between(EVENING, MORNING)).isInstanceOf(InputError.class);
    }

    @Test
    void oneActionCanBePulledOut() {
        assertThat(aTrail().ofAction("cut").stream().map(AuditEntryRow::subject))
                .containsExactly("symbol/AA1");
    }

    @Test
    void theActorsAreListedOnceEach() {
        assertThat(aTrail().actors()).containsExactly("phamm", "rvirta");
    }

    @Test
    void theTrailCountsItself() {
        assertThat(aTrail().count()).isEqualTo(3);
    }

    @Test
    void anEmptyTrailCountsNothing() {
        assertThat(new AuditTrail(session).count()).isZero();
    }

    @Test
    void theTrailPrintsAsLines() {
        AuditTrail trail = aTrail();
        List<String> lines = trail.lines(trail.about(KEY, "41207"));
        assertThat(lines).hasSize(2).allMatch(line -> line.contains("key/41207"));
    }

    @Test
    void twoEntriesAtTheSameMomentKeepTheirOrder() {
        AuditTrail trail = new AuditTrail(session);
        trail.record("rvirta", "first", KEY, "41207", MORNING);
        trail.record("rvirta", "second", KEY, "41207", MORNING);
        assertThat(actions(trail.about(KEY, "41207"))).containsExactly("first", "second");
    }
}
