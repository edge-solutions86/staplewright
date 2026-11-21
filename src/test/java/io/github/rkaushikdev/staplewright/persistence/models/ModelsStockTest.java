package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsStockTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private BlankRow aBlank() {
        KeywaySectionRow section = session.all(KeywaySectionRow.class).stream()
                .findFirst()
                .orElseGet(() -> {
                    KeywaySectionRow made = new KeywaySectionRow("Sargent LA", "L", "Sargent");
                    session.add(made);
                    session.flush();
                    return made;
                });
        BlankRow row = new BlankRow("1145A", section, "Sargent", 6);
        row.setRestricted(true);
        session.add(row);
        session.flush();
        return row;
    }

    private StockLineRow aLine() {
        return aLine("1145A", "received", 100, WHEN, line -> {});
    }

    private StockLineRow aLine(Consumer<StockLineRow> overrides) {
        return aLine("1145A", "received", 100, WHEN, overrides);
    }

    private StockLineRow aLine(String movement, int quantity) {
        return aLine("1145A", movement, quantity, WHEN, line -> {});
    }

    private StockLineRow aLine(String movement, int quantity, Consumer<StockLineRow> overrides) {
        return aLine("1145A", movement, quantity, WHEN, overrides);
    }

    private StockLineRow aLine(
            String partNumber, String movement, int quantity, LocalDate movedOn, Consumer<StockLineRow> overrides) {
        StockLineRow row = new StockLineRow(partNumber, movement, quantity, movedOn);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private PurchaseOrderRow anOrder() {
        PurchaseOrderRow row = new PurchaseOrderRow("PO-4412", "lockwood supply", WHEN);
        session.add(row);
        session.flush();
        return row;
    }

    @Test
    void aReceiptGoesInAndComesBack() {
        aBlank();
        StockLineRow row = aLine(line -> line.setReference("PO-4412"));
        assertThat(row.getId()).isNotNull();
        assertThat(row.getReference()).isEqualTo("PO-4412");
    }

    @Test
    void aReceiptAddsToTheRunningTotal() {
        aBlank();
        assertThat(aLine().signed()).isEqualTo(100);
    }

    @Test
    void cuttingTakesOffTheTotal() {
        aBlank();
        assertThat(aLine("cut", 4).signed()).isEqualTo(-4);
    }

    @Test
    void scrappingTakesOffTheTotal() {
        aBlank();
        StockLineRow line = aLine("scrapped", 2, scrapped -> scrapped.setNote("milled wrong"));
        assertThat(line.signed()).isEqualTo(-2);
    }

    @Test
    void aReturnPutsStockBack() {
        aBlank();
        assertThat(aLine("returned", 3).signed()).isEqualTo(3);
    }

    @Test
    void aMovementOfNothingIsRefused() {
        aBlank();
        assertThatThrownBy(() -> aLine("received", 0)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aMovementNobodyRecognisesIsRefused() {
        aBlank();
        assertThatThrownBy(() -> aLine("borrowed", 100)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void scrappingWithoutAReasonIsRefused() {
        aBlank();
        assertThatThrownBy(() -> aLine("scrapped", 1)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aLineAgainstAPartNobodyStocksIsRefused() {
        assertThatThrownBy(() -> aLine("9999Z", "received", 100, WHEN, line -> {}))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aLineKnowsWhichBlankItMoved() {
        aBlank();
        StockLineRow line = aLine();
        session.expireAll();
        StockLineRow found = session.get(StockLineRow.class, line.getId()).orElseThrow();
        assertThat(found.getBlank().getManufacturer()).isEqualTo("Sargent");
    }

    @Test
    void anOrderStartsPlaced() {
        assertThat(anOrder().getState()).isEqualTo("placed");
    }

    @Test
    void anOrderStateNobodyRecognisesIsRefused() {
        assertThatThrownBy(() -> {
                    PurchaseOrderRow row = new PurchaseOrderRow("PO-1", "lockwood supply", WHEN);
                    row.setState("posted");
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aLineHangsOffItsOrder() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        session.add(new PurchaseLineRow(order.getNumber(), "1145A", 250));
        session.flush();
        session.refresh(order);
        assertThat(order.getLines().stream().map(PurchaseLineRow::getPartNumber))
                .containsExactly("1145A");
    }

    @Test
    void aFreshLineIsAllOutstanding() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        PurchaseLineRow line = new PurchaseLineRow(order.getNumber(), "1145A", 250);
        session.add(line);
        session.flush();
        assertThat(line.outstanding()).isEqualTo(250);
    }

    @Test
    void aPartReceiptLeavesTheRestOutstanding() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        PurchaseLineRow line = new PurchaseLineRow(order.getNumber(), "1145A", 250);
        line.setReceived(100);
        session.add(line);
        session.flush();
        assertThat(line.outstanding()).isEqualTo(150);
    }

    @Test
    void moreArrivingThanWasOrderedIsRefused() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        assertThatThrownBy(() -> {
                    PurchaseLineRow line = new PurchaseLineRow(order.getNumber(), "1145A", 10);
                    line.setReceived(11);
                    session.add(line);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anOrderForNothingIsRefused() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        assertThatThrownBy(() -> {
                    session.add(new PurchaseLineRow(order.getNumber(), "1145A", 0));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingAnOrderTakesItsLinesWithIt() {
        aBlank();
        PurchaseOrderRow order = anOrder();
        session.add(PurchaseLineRow.of(order, "1145A", 250));
        session.flush();
        session.delete(order);
        session.flush();
        assertThat(session.all(PurchaseLineRow.class)).isEmpty();
    }

    @Test
    void theLinesForAPartComeBackInTheOrderTheyHappened() {
        aBlank();
        aLine();
        aLine("1145A", "cut", 2, LocalDate.of(2025, 5, 1), line -> {});
        assertThat(session.list("select l from StockLineRow l order by l.id", StockLineRow.class).stream()
                        .map(StockLineRow::getMovement))
                .containsExactly("received", "cut");
    }

    @Test
    void aCountLineMovesTheTotalByWhatItFound() {
        aBlank();
        StockLineRow line = aLine("counted", 97, counted -> {
            counted.setAdjustment(-3);
            counted.setNote("annual count");
        });
        assertThat(line.getQuantity()).isEqualTo(97);
        assertThat(line.signed()).isEqualTo(-3);
    }

    @Test
    void aCountWithoutAnAdjustmentIsRefused() {
        aBlank();
        assertThatThrownBy(() -> aLine("counted", 97, counted -> counted.setNote("annual count")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aMovementThatIsNotACountCarriesNoAdjustment() {
        aBlank();
        assertThatThrownBy(() -> aLine("received", 10, received -> received.setAdjustment(2)))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCountThatAgreedMovesNothing() {
        aBlank();
        StockLineRow line = aLine("counted", 97, counted -> counted.setAdjustment(0));
        assertThat(line.signed()).isZero();
    }
}
