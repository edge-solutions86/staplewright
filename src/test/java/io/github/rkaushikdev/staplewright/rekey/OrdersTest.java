package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Keyed.aCoverage;
import static io.github.rkaushikdev.staplewright.support.Keyed.aLedger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OrdersTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate LATER = LocalDate.of(2025, 4, 15);

    private static final List<OrderLine> LINES =
            List.of(new OrderLine("C-1001", "MOO-214"), new OrderLine("C-1002", "MOO-216"));

    private static WorkOrder anOrder() {
        return new WorkOrder("W-1041", "I-2025-004", LINES);
    }

    private static WorkOrderBook aBook() {
        WorkOrderBook book = new WorkOrderBook();
        book.raiseOrder(anOrder());
        return book;
    }

    private static WorkOrderBook started() {
        WorkOrderBook book = aBook();
        book.schedule("W-1041", "jkoivu", WHEN);
        book.start("W-1041");
        return book;
    }

    private static ScopeDecision aDecision() {
        CoverageMap coverage = aCoverage();
        Incident incident = new Incident("I-1", "41207", "AA1", Cause.LOST, WHEN);
        return ScopeDecision.decide(Assessment.assess(incident, coverage, aLedger()), coverage);
    }

    private static List<String> numbers(Iterable<WorkOrder> orders) {
        List<String> found = new ArrayList<>();
        orders.forEach(order -> found.add(order.number()));
        return found;
    }

    @Test
    void anOrderNeedsANumberAndSomeWork() {
        assertThatThrownBy(() -> new WorkOrder(" ", "I-2025-004", LINES)).isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new WorkOrder("W-1041", "I-2025-004", List.of()))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCylinderCannotBeOnOneOrderTwice() {
        assertThatThrownBy(() -> new WorkOrder(
                        "W-1041",
                        "I-2025-004",
                        List.of(new OrderLine("C-1001", "MOO-214"), new OrderLine("C-1001", "MOO-216"))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aNewOrderIsDraftedWithEverythingPending() {
        WorkOrder order = aBook().order("W-1041");
        assertThat(order.state()).isSameAs(OrderState.DRAFTED);
        assertThat(order.pending()).hasSize(2);
        assertThat(order.openings()).containsExactly("MOO-214", "MOO-216");
        assertThat(order.summary()).contains("2 cylinders");
    }

    @Test
    void aScopeDecisionTurnsIntoLines() {
        List<OrderLine> lines = OrderLine.linesFrom(aDecision(), List.of("MOO-214", "MOO-216"));
        assertThat(lines).extracting(OrderLine::serial).containsExactly("C-1001", "C-1002");
        assertThat(lines).allMatch(line -> line.state() == LineState.PENDING);
    }

    @Test
    void linesNeedADoorEach() {
        ScopeDecision found = aDecision();
        assertThatThrownBy(() -> OrderLine.linesFrom(found, List.of("MOO-214"))).isInstanceOf(InputError.class);
    }

    @Test
    void schedulingNamesATechnicianAndADay() {
        WorkOrderBook book = aBook();
        WorkOrder order = book.schedule("W-1041", "jkoivu", WHEN);
        assertThat(order.state()).isSameAs(OrderState.SCHEDULED);
        assertThat(order.technician()).isEqualTo("jkoivu");
        assertThat(order.scheduledFor()).contains(WHEN);
    }

    @Test
    void schedulingCanBeRedoneBeforeItStarts() {
        WorkOrderBook book = aBook();
        book.schedule("W-1041", "jkoivu", WHEN);
        WorkOrder order = book.schedule("W-1041", "phamm", LATER);
        assertThat(order.technician()).isEqualTo("phamm");
    }

    @Test
    void somebodyHasToDoTheWork() {
        WorkOrderBook book = aBook();
        assertThatThrownBy(() -> book.schedule("W-1041", "  ", WHEN)).isInstanceOf(InputError.class);
    }

    @Test
    void anOrderCannotStartBeforeItIsScheduled() {
        WorkOrderBook book = aBook();
        assertThatThrownBy(() -> book.start("W-1041")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aStartedOrderCannotBeRescheduled() {
        WorkOrderBook book = started();
        assertThatThrownBy(() -> book.schedule("W-1041", "phamm", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aLineMovesThroughPinnedAndInstalled() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.PINNED);
        assertThat(book.order("W-1041").line("C-1001").state()).isSameAs(LineState.PINNED);
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        assertThat(book.order("W-1041").line("C-1001").isSettled()).isTrue();
    }

    @Test
    void aPinnedLineIsNotSettled() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.PINNED);
        assertThat(book.order("W-1041").pending()).hasSize(2);
    }

    @Test
    void aSkippedLineSaysWhy() {
        WorkOrderBook book = started();
        assertThatThrownBy(() -> book.markLine("W-1041", "C-1001", LineState.SKIPPED))
                .isInstanceOf(InputError.class);
        WorkOrder order = book.markLine("W-1041", "C-1001", LineState.SKIPPED, "room occupied, exam in it");
        assertThat(order.line("C-1001").note()).startsWith("room occupied");
        assertThat(order.line("C-1001").isSettled()).isTrue();
    }

    @Test
    void aLineCannotBeMovedToWhereItAlreadyIs() {
        WorkOrderBook book = started();
        assertThatThrownBy(() -> book.markLine("W-1041", "C-1001", LineState.PENDING))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCylinderNotOnTheOrder() {
        WorkOrderBook book = started();
        assertThatThrownBy(() -> book.markLine("W-1041", "C-9999", LineState.PINNED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anOrderWillNotCloseWithAPendingCylinder() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> book.close("W-1041", LATER));
        assertThat(caught.facts().get("pending")).isEqualTo(List.of("C-1002"));
    }

    @Test
    void anOrderClosesWhenEveryLineIsSettled() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        book.markLine("W-1041", "C-1002", LineState.SKIPPED, "wrong format, ordered a new one");
        WorkOrder order = book.close("W-1041", LATER);
        assertThat(order.state()).isSameAs(OrderState.DONE);
        assertThat(order.closedOn()).contains(LATER);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void anOrderThatNeverStartedCannotClose() {
        WorkOrderBook book = aBook();
        book.schedule("W-1041", "jkoivu", WHEN);
        assertThatThrownBy(() -> book.close("W-1041", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void cancellingSaysWhyAndKeepsTheLines() {
        WorkOrderBook book = started();
        WorkOrder order = book.cancel("W-1041", LATER, "department paid for new hardware instead");
        assertThat(order.state()).isSameAs(OrderState.CANCELLED);
        assertThat(order.lines()).hasSize(2);
        assertThat(order.isOpen()).isFalse();
    }

    @Test
    void aCancellationWithoutAReasonIsRefused() {
        WorkOrderBook book = started();
        assertThatThrownBy(() -> book.cancel("W-1041", LATER, " ")).isInstanceOf(InputError.class);
    }

    @Test
    void aClosedOrderCannotBeCancelled() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        book.markLine("W-1041", "C-1002", LineState.INSTALLED);
        book.close("W-1041", LATER);
        assertThatThrownBy(() -> book.cancel("W-1041", LATER, "changed our minds"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anOrderNumberCannotBeReused() {
        WorkOrderBook book = aBook();
        assertThatThrownBy(() -> book.raiseOrder(anOrder())).isInstanceOf(ConflictError.class);
    }

    @Test
    void anOrderCannotBeFiledAlreadyStarted() {
        WorkOrderBook book = new WorkOrderBook();
        assertThatThrownBy(() -> book.raiseOrder(anOrder().withState(OrderState.STARTED)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingForAnOrderThatIsNotThere() {
        assertThatThrownBy(() -> aBook().order("W-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void ordersComeBackByTechnicianIncidentAndDay() {
        WorkOrderBook book = aBook();
        book.raiseOrder(anOrder().withNumber("W-1042").withIncident("I-2025-009"));
        book.schedule("W-1041", "jkoivu", WHEN);
        book.schedule("W-1042", "phamm", LATER);
        assertThat(numbers(book.forTechnician("jkoivu"))).containsExactly("W-1041");
        assertThat(numbers(book.forIncident("I-2025-009"))).containsExactly("W-1042");
        assertThat(numbers(book.dueOn(LATER))).containsExactly("W-1042");
    }

    @Test
    void aClosedOrderIsNotDueAnyMore() {
        WorkOrderBook book = started();
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        book.markLine("W-1041", "C-1002", LineState.INSTALLED);
        book.close("W-1041", LATER);
        assertThat(book.dueOn(WHEN)).isEmpty();
        assertThat(book.openOrders()).isEmpty();
    }

    @Test
    void theWorkloadCountsPendingCylindersPerPerson() {
        WorkOrderBook book = aBook();
        book.raiseOrder(anOrder().withNumber("W-1042"));
        book.schedule("W-1041", "jkoivu", WHEN);
        book.schedule("W-1042", "jkoivu", LATER);
        book.start("W-1041");
        book.markLine("W-1041", "C-1001", LineState.INSTALLED);
        assertThat(book.workload()).isEqualTo(Map.of("jkoivu", 3));
    }

    @Test
    void anUnassignedOrderIsNobodysWorkload() {
        assertThat(aBook().workload()).isEmpty();
    }

    @Test
    void theBookIsAContainer() {
        WorkOrderBook book = aBook();
        assertThat(book.contains("W-1041")).isTrue();
        assertThat(book.contains("W-9999")).isFalse();
        assertThat(book.size()).isEqualTo(1);
        assertThat(numbers(book)).containsExactly("W-1041");
    }
}
