package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class ReportsStockTest {

    private static final LocalDate SINCE = LocalDate.of(2025, 1, 1);
    private static final LocalDate UNTIL = LocalDate.of(2025, 6, 30);
    private static final KeywaySectionRow SECTION = new KeywaySectionRow("Sargent LA", "LC", "Sargent");

    private static BlankRow aBlank() {
        return aBlank("1145A", 200);
    }

    private static BlankRow aBlank(String part, int stock) {
        return aBlank(part, stock, row -> {});
    }

    private static BlankRow aBlank(String part, int stock, Consumer<BlankRow> overrides) {
        BlankRow row = new BlankRow(part, SECTION, "Sargent", 6);
        row.setRestricted(true);
        row.setReorderAt(50);
        row.setStock(stock);
        overrides.accept(row);
        return row;
    }

    private static StockLineRow aLine(String movement, int quantity, LocalDate when) {
        return aLine(movement, quantity, when, row -> {});
    }

    private static StockLineRow aLine(String movement, int quantity, LocalDate when, Consumer<StockLineRow> overrides) {
        StockLineRow row = new StockLineRow("1145A", movement, quantity, when);
        overrides.accept(row);
        return row;
    }

    private static List<StockLineRow> aRun() {
        return List.of(
                aLine("received", 100, LocalDate.of(2025, 2, 1), row -> row.setReference("PO-4412")),
                aLine("cut", 30, LocalDate.of(2025, 3, 4), row -> row.setReference("41207")),
                aLine("returned", 2, LocalDate.of(2025, 3, 5)),
                aLine("scrapped", 4, LocalDate.of(2025, 4, 9), row -> row.setNote("milled wrong")));
    }

    private static List<StockLineRow> with(List<StockLineRow> run, StockLineRow... more) {
        List<StockLineRow> lines = new ArrayList<>(run);
        lines.addAll(List.of(more));
        return lines;
    }

    private static PurchaseOrderRow anOrder() {
        return anOrder(250, 0, "placed");
    }

    private static PurchaseOrderRow anOrder(int ordered, int received, String state) {
        PurchaseOrderRow order = new PurchaseOrderRow("PO-9001", "lockwood supply", LocalDate.of(2025, 5, 1));
        order.setState(state);
        PurchaseLineRow line = PurchaseLineRow.of(order, "1145A", ordered);
        line.setReceived(received);
        return order;
    }

    private static Reconciliation reconciled(List<StockLineRow> lines) {
        return StockPages.reconcile(aBlank(), lines, SINCE, UNTIL);
    }

    @Test
    void theClosingTotalIsWhereTheDrawerIsNow() {
        assertThat(reconciled(aRun()).closing()).isEqualTo(200);
    }

    @Test
    void theOpeningTotalIsWorkedBackwards() {
        assertThat(reconciled(aRun()).opening()).isEqualTo(200 - (100 - 30 + 2 - 4));
    }

    @Test
    void aMovementAfterThePeriodIsTakenOffTheClosing() {
        List<StockLineRow> lines =
                with(aRun(), aLine("cut", 10, LocalDate.of(2025, 8, 1), row -> row.setReference("41300")));
        assertThat(reconciled(lines).closing()).isEqualTo(210);
    }

    @Test
    void aMovementBeforeThePeriodIsLeftInTheOpening() {
        List<StockLineRow> lines = new ArrayList<>();
        lines.add(aLine("received", 50, LocalDate.of(2024, 12, 1)));
        lines.addAll(aRun());
        assertThat(reconciled(lines).received()).isEqualTo(100);
    }

    @Test
    void theMovementsAreTotalledByKind() {
        Reconciliation found = reconciled(aRun());
        assertThat(List.of(found.received(), found.cut(), found.returned(), found.scrapped()))
                .containsExactly(100, 30, 2, 4);
    }

    @Test
    void aCleanRunAccountsForEverything() {
        Reconciliation found = reconciled(aRun());
        assertThat(found.agrees()).isTrue();
        assertThat(found.unaccounted()).isEqualTo(0);
    }

    @Test
    void aCountInsideThePeriodShowsUpAsAGap() {
        List<StockLineRow> lines =
                with(aRun(), aLine("counted", 180, LocalDate.of(2025, 5, 2), row -> row.setAdjustment(-12)));
        Reconciliation found = reconciled(lines);
        assertThat(found.counted()).isEqualTo(180);
        assertThat(found.unaccounted()).isEqualTo(-12);
        assertThat(found.agrees()).isFalse();
    }

    @Test
    void aCountThatAgreedLeavesThePartAccountedFor() {
        List<StockLineRow> lines =
                with(aRun(), aLine("counted", 168, LocalDate.of(2025, 5, 2), row -> row.setAdjustment(0)));
        assertThat(reconciled(lines).agrees()).isTrue();
    }

    @Test
    void twoCountsAddTheirGapsUp() {
        List<StockLineRow> lines = List.of(
                aLine("counted", 190, LocalDate.of(2025, 3, 2), row -> row.setAdjustment(-10)),
                aLine("counted", 188, LocalDate.of(2025, 5, 2), row -> row.setAdjustment(-2)));
        assertThat(reconciled(lines).unaccounted()).isEqualTo(-12);
    }

    @Test
    void aPeriodWithNothingInItOpensWhereItCloses() {
        Reconciliation found = reconciled(List.of());
        assertThat(found.opening()).isEqualTo(found.closing());
        assertThat(found.closing()).isEqualTo(200);
    }

    @Test
    void aReconciliationThatIsShortSaysHowShort() {
        Reconciliation found = new Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 88);
        assertThat(found.unaccounted()).isEqualTo(-12);
        assertThat(found.describe()).isEqualTo("1145A 12 unaccounted");
    }

    @Test
    void aReconciliationThatIsOverSaysOver() {
        assertThat(new Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 103).describe())
                .endsWith("3 over");
    }

    @Test
    void aSheetReadsOpeningToClosing() {
        List<String> lines = StockPages.reconciliationSheet(reconciled(aRun()));
        assertThat(lines.get(0)).isEqualTo("1145A from 132 to 200");
        assertThat(Text.strip(lines.get(lines.size() - 1))).isEqualTo("1145A accounts for every blank");
    }

    @Test
    void theSheetCarriesEveryMovementKind() {
        List<String> lines = StockPages.reconciliationSheet(reconciled(aRun()));
        assertThat(lines).anyMatch(line -> Text.strip(line).startsWith("scrapped"));
    }

    @Test
    void theLogIsOldestFirstWithAHeading() {
        List<String> lines = StockPages.movementLog(aRun());
        assertThat(lines.get(0)).startsWith("day");
        assertThat(lines.get(1)).startsWith("2025-02-01");
    }

    @Test
    void theLogShowsWhatALineDidToTheDrawer() {
        assertThat(StockPages.movementLog(aRun()).get(2)).contains("-30");
    }

    @Test
    void theLogFallsBackToTheNoteWhenThereIsNoReference() {
        assertThat(StockPages.movementLog(aRun())).anyMatch(line -> line.contains("milled wrong"));
    }

    @Test
    void nothingShortSaysSo() {
        assertThat(StockPages.shrinkage(List.of(reconciled(aRun())))).containsExactly("every part accounts for itself");
    }

    @Test
    void theWorstShortfallComesFirst() {
        List<String> lines = StockPages.shrinkage(List.of(
                new Reconciliation("RA4", 100, 0, 0, 0, 0, 0, 97),
                new Reconciliation("1145A", 100, 0, 0, 0, 0, 0, 60)));
        assertThat(lines.get(0)).isEqualTo("2 parts do not account for themselves");
        assertThat(lines.get(1)).contains("1145A");
    }

    @Test
    void nothingBelowItsLevelSaysSo() {
        assertThat(StockPages.reorderSheet(List.of(aBlank()))).containsExactly("nothing is below its reorder level");
    }

    @Test
    void theReorderSheetWorksOutWhatToOrder() {
        List<String> lines = StockPages.reorderSheet(List.of(aBlank("1145A", 10)));
        assertThat(lines.get(0)).startsWith("part");
        List<String> words = Text.words(lines.get(1));
        assertThat(words.get(words.size() - 1)).isEqualTo("90");
    }

    @Test
    void theShortestPartIsFirstOnTheSheet() {
        List<String> lines =
                StockPages.reorderSheet(List.of(aBlank("1145A", 40), aBlank("RA4", 1, row -> row.setReorderAt(20))));
        assertThat(lines.get(1)).startsWith("RA4");
    }

    @Test
    void noRestrictedBrassSaysSo() {
        assertThat(StockPages.restrictedPosition(List.of(aBlank("1145A", 200, row -> row.setRestricted(false)))))
                .containsExactly("no restricted blanks are stocked");
    }

    @Test
    void theRestrictedPositionTotalsTheBuilding() {
        List<String> lines = StockPages.restrictedPosition(List.of(aBlank(), aBlank("RA4", 40)));
        assertThat(lines.get(0)).isEqualTo("240 restricted blanks across 2 part numbers");
        assertThat(Text.strip(lines.get(1))).startsWith("1145A");
    }

    @Test
    void nothingOnOrderSaysSo() {
        assertThat(StockPages.outstandingOrders(List.of())).containsExactly("nothing is on order");
    }

    @Test
    void aReceivedOrderIsNotOutstanding() {
        assertThat(StockPages.outstandingOrders(List.of(anOrder(250, 250, "received"))))
                .containsExactly("nothing is on order");
    }

    @Test
    void anOpenOrderListsWhatIsStillComing() {
        List<String> lines = StockPages.outstandingOrders(List.of(anOrder(250, 100, "placed")));
        assertThat(lines.get(0)).isEqualTo("1 orders outstanding");
        assertThat(Text.strip(lines.get(lines.size() - 1))).isEqualTo("1145A       150 to come");
    }

    @Test
    void aLineThatFullyArrivedIsLeftOff() {
        List<String> lines = StockPages.outstandingOrders(List.of(anOrder(10, 10, "part received")));
        assertThat(lines).hasSize(2);
    }

    @Test
    void thePackReadsInTheOrderSomebodySignsIt() {
        Reconciliation found = reconciled(aRun());
        List<String> lines =
                StockPages.stockReport(List.of(aBlank("1145A", 10)), List.of(found), List.of(anOrder()), SINCE, UNTIL);
        assertThat(lines.get(0)).isEqualTo("blank stock 2025-01-01 to 2025-06-30");
        assertThat(lines).anyMatch(line -> line.startsWith("1 orders outstanding"));
        assertThat(lines).anyMatch(line -> line.startsWith("part"));
    }
}
