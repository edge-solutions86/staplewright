package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsRekeyTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 15);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private IncidentRow anIncident() {
        return anIncident(incident -> {});
    }

    private IncidentRow anIncident(Consumer<IncidentRow> overrides) {
        IncidentRow row = new IncidentRow("I-2025-004", "41207", "AA1", "lost", WHEN);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private WorkOrderRow anOrder() {
        return anOrder("W-1041", order -> {});
    }

    private WorkOrderRow anOrder(Consumer<WorkOrderRow> overrides) {
        return anOrder("W-1041", overrides);
    }

    private WorkOrderRow anOrder(String number, Consumer<WorkOrderRow> overrides) {
        WorkOrderRow row = new WorkOrderRow(number);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    @Test
    void theRekeyTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("incidents", "work_orders", "work_order_lines", "retired_bittings");
    }

    @Test
    void anIncidentRoundTrips() {
        IncidentRow row = anIncident();
        assertThat(row.getSeverity()).isEqualTo("routine");
        assertThat(row.getOrders()).isEmpty();
        assertThat(row.toString()).contains("I-2025-004");
    }

    @Test
    void aTheftHasToBeWrittenUp() {
        assertThatThrownBy(() -> anIncident(incident -> incident.setCause("stolen")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aTheftWithANoteIsAccepted() {
        IncidentRow row = anIncident(incident -> {
            incident.setCause("stolen");
            incident.setNote("taken from a truck on a friday");
        });
        assertThat(row.getCause()).isEqualTo("stolen");
    }

    @Test
    void anOrderHangsOffAnIncident() {
        anIncident();
        anOrder(order -> order.setIncidentNumber("I-2025-004"));
        session.expireAll();
        WorkOrderRow order = session.get(WorkOrderRow.class, "W-1041").orElseThrow();
        assertThat(order.getIncident().orElseThrow().getSymbol()).isEqualTo("AA1");
        assertThat(order.getState()).isEqualTo("drafted");
        assertThat(order.toString()).contains("W-1041");
    }

    @Test
    void anOrderWithoutAnIncidentIsAllowed() {
        WorkOrderRow order = anOrder();
        assertThat(order.getIncident()).isEmpty();
    }

    @Test
    void aScheduledOrderNeedsADay() {
        assertThatThrownBy(() -> anOrder(order -> order.setState("scheduled")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aScheduledOrderWithADayIsFine() {
        WorkOrderRow order = anOrder(scheduled -> {
            scheduled.setState("scheduled");
            scheduled.setScheduledFor(LATER);
            scheduled.setTechnician("jkoivu");
        });
        assertThat(order.getScheduledFor()).contains(LATER);
    }

    @Test
    void linesHangOffAnOrder() {
        WorkOrderRow order = anOrder();
        session.addAll(List.of(
                WorkOrderLineRow.of(order, "C-1001", "MOO-214"), WorkOrderLineRow.of(order, "C-1002", "MOO-216")));
        session.flush();
        session.refresh(order);
        assertThat(order.getLines()).hasSize(2);
        assertThat(order.pending()).hasSize(2);
        assertThat(order.getLines().get(0).toString()).contains("C-1001");
    }

    @Test
    void aSettledLineDropsOutOfPending() {
        WorkOrderRow order = anOrder();
        WorkOrderLineRow line = WorkOrderLineRow.of(order, "C-1001", "MOO-214");
        line.setState("installed");
        session.add(line);
        session.flush();
        session.refresh(order);
        assertThat(order.pending()).isEmpty();
    }

    @Test
    void aCylinderIsOnAnOrderOnce() {
        WorkOrderRow order = anOrder();
        session.add(WorkOrderLineRow.of(order, "C-1001", "MOO-214"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(WorkOrderLineRow.of(order, "C-1001", "MOO-216"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameCylinderOnAnotherOrderIsFine() {
        WorkOrderRow first = anOrder();
        WorkOrderRow second = anOrder("W-1042", order -> {});
        session.add(WorkOrderLineRow.of(first, "C-1001", "MOO-214"));
        session.add(WorkOrderLineRow.of(second, "C-1001", "MOO-214"));
        session.flush();
        assertThat(session.all(WorkOrderLineRow.class)).hasSize(2);
    }

    @Test
    void deletingAnOrderTakesItsLines() {
        WorkOrderRow order = anOrder();
        session.add(WorkOrderLineRow.of(order, "C-1001", "MOO-214"));
        session.flush();
        session.delete(order);
        session.flush();
        assertThat(session.all(WorkOrderLineRow.class)).isEmpty();
    }

    @Test
    void aLineNeedsAnOrderThatExists() {
        assertThatThrownBy(() -> {
                    session.add(new WorkOrderLineRow("W-9999", "C-1001", "MOO-214"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRetiredCutRoundTrips() {
        session.add(new RetiredBittingRow(b("342156"), "compromised", WHEN));
        session.expireAll();
        RetiredBittingRow found = session.one(RetiredBittingRow.class);
        assertThat(found.getBitting()).isEqualTo(b("342156"));
        assertThat(found.isPermanent()).isTrue();
        assertThat(found.toString()).contains("342156");
    }

    @Test
    void aSupersededCutIsNotPermanent() {
        RetiredBittingRow row = new RetiredBittingRow(b("362356"), "superseded", WHEN);
        session.add(row);
        session.flush();
        assertThat(row.isPermanent()).isFalse();
    }

    @Test
    void aCutIsRetiredOnce() {
        session.add(new RetiredBittingRow(b("342156"), "compromised", WHEN));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(new RetiredBittingRow(b("342156"), "superseded", LATER));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theRetiredTableStandsOnItsOwn() {
        session.add(new RetiredBittingRow(b("342156"), "compromised", WHEN));
        session.expireAll();
        assertThat(session.one(RetiredBittingRow.class).getSymbol()).isEmpty();
    }
}
