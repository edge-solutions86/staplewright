package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CliStockTest {

    private static final String WHEN = "2025-04-08";
    private static final String LATER = "2025-05-20";

    /** Two parts in the drawer and one order half delivered. */
    @BeforeEach
    void shop() {
        TestDatabase.fresh().inSession(CliStockTest::fill);
    }

    private static void fill(Session session) {
        KeywaySectionRow section = new KeywaySectionRow("Sargent LA", "L", "Sargent");
        session.add(section);
        session.flush();
        session.add(blank(section, "1145A", true, 50, 200));
        session.add(blank(section, "RA4", false, 20, 40));
        PurchaseOrderRow order = new PurchaseOrderRow("PO-9001", "lockwood supply", LocalDate.of(2025, 5, 1));
        session.add(order);
        session.flush();
        PurchaseLineRow line = PurchaseLineRow.of(order, "1145A", 250);
        line.setReceived(100);
        session.add(line);
    }

    private static BlankRow blank(KeywaySectionRow section, String part, boolean restricted, int reorderAt, int stock) {
        BlankRow row = new BlankRow(part, section, "Sargent", 6);
        row.setRestricted(restricted);
        row.setReorderAt(reorderAt);
        row.setStock(stock);
        return row;
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    @Test
    void showingAPartPrintsBothTotals() {
        List<String> lines = call("stock", "show", "1145A").lines();
        assertThat(lines.get(0)).isEqualTo("1145A Sargent 6 chamber");
        assertThat(lines.get(1)).isEqualTo("drawer 200, ledger 0");
    }

    @Test
    void aRestrictedPartSaysItIsRestricted() {
        assertThat(call("stock", "show", "1145A").output()).contains("restricted");
    }

    @Test
    void anOpenPartSaysNothingAboutRestriction() {
        assertThat(call("stock", "show", "RA4").output()).doesNotContain("restricted");
    }

    @Test
    void aPartNobodyStocksIsRefused() {
        assertThat(call("stock", "show", "9999Z").exitCode()).isNotZero();
    }

    @Test
    void receivingDefaultsToOne() {
        assertThat(call("stock", "receive", "1145A", "--on", WHEN).output()).contains("1145A now 201");
    }

    @Test
    void receivingABatchTakesACount() {
        Cli.Result found = call("stock", "receive", "1145A", "--count", "100", "--on", WHEN);
        assertThat(found.output()).contains("1145A now 300");
    }

    @Test
    void cuttingARestrictedBlankWithoutAReferenceIsRefused() {
        assertThat(call("stock", "cut", "1145A", "--on", LATER).exitCode()).isNotZero();
    }

    @Test
    void cuttingWithAReferenceGoesThrough() {
        Cli.Result found = call("stock", "cut", "1145A", "--against", "41207", "--on", LATER);
        assertThat(found.output()).contains("1145A now 199");
    }

    @Test
    void cuttingAnOpenBlankNeedsNothing() {
        assertThat(call("stock", "cut", "RA4", "--on", LATER).output()).contains("RA4 now 39");
    }

    @Test
    void cuttingMoreThanTheDrawerHoldsIsRefused() {
        Cli.Result found = call("stock", "cut", "RA4", "--count", "41", "--on", LATER);
        assertThat(found.exitCode()).isNotZero();
    }

    @Test
    void scrappingTakesTheReasonAsAnArgument() {
        Cli.Result found = call("stock", "scrap", "RA4", "milled wrong", "--count", "2", "--on", LATER);
        assertThat(found.output()).contains("RA4 now 38");
    }

    @Test
    void countingReportsTheGap() {
        Cli.Result found = call("stock", "count", "1145A", "188", "--on", LATER);
        assertThat(found.output().strip()).isEqualTo("1145A counted 188, 12 short");
    }

    @Test
    void aCountThatAgreesSaysSo() {
        Cli.Result found = call("stock", "count", "RA4", "40", "--on", LATER);
        assertThat(found.output().strip()).endsWith("agrees");
    }

    @Test
    void aNegativeCountIsRejectedAsUnreadable() {
        assertThat(call("stock", "count", "RA4", "-1").exitCode()).isNotZero();
    }

    @Test
    void theLogHasAHeadingAndTheMovements() {
        call("stock", "receive", "RA4", "--count", "5", "--on", WHEN);
        List<String> lines = call("stock", "log", "RA4").lines();
        assertThat(lines.get(0)).startsWith("day");
        assertThat(lines.get(1)).startsWith("2025-04-08");
    }

    @Test
    void aPartWithNoMovementsPrintsJustTheHeading() {
        assertThat(call("stock", "log", "RA4").lines()).hasSize(1);
    }

    @Test
    void reconcilingReadsOpeningToClosing() {
        call("stock", "receive", "RA4", "--count", "5", "--on", WHEN);
        List<String> lines = call("stock", "reconcile", "RA4", "--from", "2025-01-01", "--to", "2025-12-31")
                .lines();
        assertThat(lines.get(0)).isEqualTo("RA4 from 40 to 45");
    }

    @Test
    void reconcilingDefaultsToTheYearSoFar() {
        assertThat(call("stock", "reconcile", "RA4").exitCode()).isZero();
    }

    @Test
    void theReorderSheetNamesWhatIsShort() {
        call("stock", "cut", "RA4", "--count", "25", "--on", LATER);
        List<String> lines = call("stock", "reorder").lines();
        assertThat(lines.get(0)).startsWith("part");
        assertThat(lines.get(1)).startsWith("RA4");
    }

    @Test
    void nothingShortSaysSo() {
        assertThat(call("stock", "reorder").output()).contains("nothing is below");
    }

    @Test
    void theRestrictedPositionTotalsTheBuilding() {
        assertThat(call("stock", "restricted").output()).contains("200 restricted blanks across 1 part numbers");
    }

    @Test
    void theOpenOrdersListWhatIsStillComing() {
        List<String> lines = call("stock", "orders").lines();
        assertThat(lines.get(0)).isEqualTo("1 orders outstanding");
        assertThat(lines.get(lines.size() - 1)).contains("150 to come");
    }

    @Test
    void theShrinkageReportIsQuietWhenEverythingAddsUp() {
        assertThat(call("stock", "shrinkage").output()).contains("every part accounts for itself");
    }

    @Test
    void aCountThatDidNotAgreeShowsUpAsShrinkage() {
        call("stock", "count", "1145A", "188", "--on", LATER);
        Cli.Result found = call("stock", "shrinkage", "--from", "2025-01-01");
        assertThat(found.output()).contains("1145A");
    }

    @Test
    void thePackPrintsTopToBottom() {
        List<String> lines = call("stock", "report", "--from", "2025-01-01").lines();
        assertThat(lines.get(0)).startsWith("blank stock 2025-01-01 to ");
        assertThat(lines).anyMatch(line -> line.startsWith("1 orders outstanding"));
    }
}
