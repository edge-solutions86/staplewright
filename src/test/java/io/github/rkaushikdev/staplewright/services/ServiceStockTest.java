package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.services.StockService.Shortfall;
import io.github.rkaushikdev.staplewright.services.StockService.Wanted;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceStockTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 8);
    private static final LocalDate LATER = LocalDate.of(2025, 5, 20);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        KeywaySectionRow section = new KeywaySectionRow("Sargent LA", "L", "Sargent");
        session.add(section);
        session.flush();
        session.add(blank(section, "1145A", true, 50));
        session.add(blank(section, "RA4", false, 20));
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static BlankRow blank(KeywaySectionRow section, String part, boolean restricted, int reorderAt) {
        BlankRow row = new BlankRow(part, section, "Sargent", 6);
        row.setRestricted(restricted);
        row.setReorderAt(reorderAt);
        return row;
    }

    private StockService stocked() {
        return stocked("1145A", 200);
    }

    private StockService stocked(String part, int quantity) {
        StockService service = new StockService(session);
        service.receive(part, quantity, WHEN, "PO-4412");
        return service;
    }

    private static List<String> movementsOf(List<StockLineRow> lines) {
        return lines.stream().map(StockLineRow::getMovement).toList();
    }

    private static List<String> numbers(List<PurchaseOrderRow> orders) {
        return orders.stream().map(PurchaseOrderRow::getNumber).toList();
    }

    private static List<String> parts(List<BlankRow> blanks) {
        return blanks.stream().map(BlankRow::getPartNumber).toList();
    }

    @Test
    void receivingBrassPutsItInTheDrawer() {
        StockService service = stocked();
        assertThat(service.onHand("1145A")).isEqualTo(200);
    }

    @Test
    void aPartNobodyStocksIsA404() {
        assertThatThrownBy(() -> new StockService(session).receive("9999Z", 10, WHEN))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void receivingNothingIsRefused() {
        assertThatThrownBy(() -> new StockService(session).receive("1145A", 0, WHEN))
                .isInstanceOf(InputError.class);
    }

    @Test
    void cuttingTakesItBackOut() {
        StockService service = stocked();
        service.cut("1145A", 6, LATER, "41207");
        assertThat(service.onHand("1145A")).isEqualTo(194);
    }

    @Test
    void aRestrictedBlankDoesNotMoveWithoutAReference() {
        StockService service = stocked();
        assertThatThrownBy(() -> service.cut("1145A", 1, LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anOpenBlankMovesWithoutOne() {
        StockService service = stocked("RA4", 40);
        service.cut("RA4", 2, LATER);
        assertThat(service.onHand("RA4")).isEqualTo(38);
    }

    @Test
    void cuttingMoreThanTheDrawerHoldsIsRefused() {
        StockService service = stocked("RA4", 3);
        CapacityError caught = catchThrowableOfType(CapacityError.class, () -> service.cut("RA4", 4, LATER));
        assertThat(caught.facts().get("held")).isEqualTo(3);
    }

    @Test
    void aRefusedDrawLeavesTheDrawerAlone() {
        StockService service = stocked("RA4", 3);
        assertThatThrownBy(() -> service.cut("RA4", 4, LATER)).isInstanceOf(CapacityError.class);
        assertThat(service.onHand("RA4")).isEqualTo(3);
    }

    @Test
    void scrappingSaysWhy() {
        StockService service = stocked();
        assertThatThrownBy(() -> service.scrap("1145A", 2, LATER, "")).isInstanceOf(InputError.class);
    }

    @Test
    void scrappingTakesItOffTheTotal() {
        StockService service = stocked();
        service.scrap("1145A", 2, LATER, "milled wrong at the factory");
        assertThat(service.onHand("1145A")).isEqualTo(198);
    }

    @Test
    void aBlankThatComesBackGoesInAgain() {
        StockService service = stocked();
        service.cut("1145A", 4, LATER, "41207");
        service.putBack("1145A", 1, LATER, "41207");
        assertThat(service.onHand("1145A")).isEqualTo(197);
    }

    @Test
    void theLedgerTotalAgreesWithTheColumn() {
        StockService service = stocked();
        service.cut("1145A", 4, LATER, "41207");
        assertThat(service.ledgerTotal("1145A")).isEqualTo(service.onHand("1145A"));
    }

    @Test
    void aCountThatAgreesSaysSo() {
        StockService service = stocked();
        CountResult found = service.count("1145A", 200, LATER);
        assertThat(found.agrees()).isTrue();
        assertThat(found.describe()).isEqualTo("1145A counted 200, agrees");
    }

    @Test
    void aCountThatIsShortNamesTheGap() {
        StockService service = stocked();
        CountResult found = service.count("1145A", 188, LATER);
        assertThat(found.difference()).isEqualTo(-12);
        assertThat(found.describe()).isEqualTo("1145A counted 188, 12 short");
    }

    @Test
    void aCountThatIsOverSaysOver() {
        StockService service = stocked();
        assertThat(service.count("1145A", 203, LATER).describe()).endsWith("3 over");
    }

    @Test
    void aCountMovesTheTotalToWhatWasFound() {
        StockService service = stocked();
        service.count("1145A", 188, LATER);
        assertThat(service.onHand("1145A")).isEqualTo(188);
    }

    @Test
    void aCountLeavesALineBehind() {
        StockService service = stocked();
        service.count("1145A", 188, LATER);
        assertThat(movementsOf(service.movements("1145A"))).containsExactly("received", "counted");
    }

    @Test
    void aCountOfLessThanNothingIsRefused() {
        StockService service = stocked();
        assertThatThrownBy(() -> service.count("1145A", -1, LATER)).isInstanceOf(InputError.class);
    }

    @Test
    void aCountKeepsTheTwoTotalsAgreeing() {
        StockService service = stocked();
        service.count("1145A", 188, LATER);
        assertThat(service.ledgerTotal("1145A")).isEqualTo(service.onHand("1145A"));
        assertThat(service.onHand("1145A")).isEqualTo(188);
    }

    @Test
    void theGapACountFoundIsOnItsLine() {
        StockService service = stocked();
        service.count("1145A", 188, LATER);
        assertThat(service.movements("1145A").stream()
                        .map(StockLineRow::getAdjustment)
                        .toList())
                .containsExactly(Optional.empty(), Optional.of(-12));
    }

    @Test
    void theReorderListIsShortestFirst() {
        StockService service = stocked("1145A", 10);
        service.receive("RA4", 19, WHEN);
        assertThat(service.belowReorder().stream().map(Shortfall::partNumber).toList())
                .containsExactly("1145A", "RA4");
    }

    @Test
    void aPartAtItsReorderLevelIsNotOnTheList() {
        StockService service = stocked("1145A", 50);
        service.receive("RA4", 20, WHEN);
        assertThat(service.belowReorder()).isEmpty();
    }

    @Test
    void anOrderGoesInWithALinePerPart() {
        StockService service = new StockService(session);
        PurchaseOrderRow order =
                service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 250)));
        assertThat(order.getState()).isEqualTo("placed");
        assertThat(order.getLines().stream().map(PurchaseLineRow::getOrdered).toList())
                .containsExactly(250);
    }

    @Test
    void anOrderWithNothingOnItIsRefused() {
        assertThatThrownBy(() -> new StockService(session).placeOrder("PO-9001", "lockwood supply", WHEN, List.of()))
                .isInstanceOf(InputError.class);
    }

    @Test
    void anOrderNumberIsNotReused() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 250)));
        assertThatThrownBy(() -> service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("RA4", 10))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anOrderForAPartNobodyStocksIsA404() {
        assertThatThrownBy(() -> new StockService(session)
                        .placeOrder("PO-9001", "supply", WHEN, List.of(new Wanted("9999Z", 10))))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void bookingInMovesTheStockAndTheLine() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 250)));
        PurchaseLineRow line = service.bookIn("PO-9001", "1145A", 100, LATER);
        assertThat(line.getReceived()).isEqualTo(100);
        assertThat(service.onHand("1145A")).isEqualTo(100);
    }

    @Test
    void aPartReceiptLeavesTheOrderPartReceived() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 250)));
        service.bookIn("PO-9001", "1145A", 100, LATER);
        assertThat(service.order("PO-9001").getState()).isEqualTo("part received");
    }

    @Test
    void theLastReceiptClosesTheOrder() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 250)));
        service.bookIn("PO-9001", "1145A", 250, LATER);
        assertThat(service.order("PO-9001").getState()).isEqualTo("received");
    }

    @Test
    void moreArrivingThanWasOrderedIsRefused() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        assertThatThrownBy(() -> service.bookIn("PO-9001", "1145A", 11, LATER)).isInstanceOf(CapacityError.class);
    }

    @Test
    void bookingInAPartThatIsNotOnTheOrder() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        assertThatThrownBy(() -> service.bookIn("PO-9001", "RA4", 1, LATER)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCancelledOrderTakesNothingIn() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        service.cancelOrder("PO-9001", "supplier cannot get the brass");
        assertThatThrownBy(() -> service.bookIn("PO-9001", "1145A", 1, LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anOrderThatArrivedCannotBeCancelled() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        service.bookIn("PO-9001", "1145A", 10, LATER);
        assertThatThrownBy(() -> service.cancelOrder("PO-9001", "changed our mind"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theOutstandingOrdersAreTheOnesStillComing() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        service.placeOrder("PO-9002", "lockwood supply", WHEN, List.of(new Wanted("RA4", 10)));
        service.bookIn("PO-9002", "RA4", 10, LATER);
        assertThat(numbers(service.orders().outstanding())).containsExactly("PO-9001");
    }

    @Test
    void theBlanksCutAgainstATagComeBack() {
        StockService service = stocked();
        service.cut("1145A", 1, LATER, "41207");
        service.cut("1145A", 1, LATER, "41208");
        assertThat(service.cutAgainst("41207").stream()
                        .map(StockLineRow::getReference)
                        .toList())
                .containsExactly("41207");
    }

    @Test
    void aReceiptAgainstANumberIsNotACut() {
        StockService service = stocked();
        assertThat(service.cutAgainst("PO-4412")).isEmpty();
    }

    @Test
    void theTotalsSinceADayAreMostMovedFirst() {
        StockService service = stocked();
        service.receive("RA4", 100, WHEN);
        service.cut("1145A", 3, LATER, "41207");
        service.cut("RA4", 9, LATER);
        assertThat(service.totalsSince(LATER).keySet()).containsExactly("RA4", "1145A");
    }

    @Test
    void aDayAfterEverythingMovedIsEmpty() {
        StockService service = stocked();
        service.cut("1145A", 3, LATER, "41207");
        assertThat(service.totalsSince(LocalDate.of(2025, 12, 1))).isEmpty();
    }

    @Test
    void aCountResultIsReadableOnItsOwn() {
        assertThat(new CountResult("RA4", 10, 10).agrees()).isTrue();
    }

    @Test
    void aMovementOfLessThanNothingIsRefused() {
        assertThatThrownBy(() -> new StockService(session).cut("RA4", -1, LATER))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aPartThatNeverMovedHasAnEmptyLedger() {
        assertThat(new StockService(session).ledgerTotal("RA4")).isZero();
    }

    @Test
    void theMovementsOfAPartNobodyStocksIsA404() {
        assertThatThrownBy(() -> new StockService(session).movements("9999Z")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theLinesSinceADayLeaveOutWhatCameBefore() {
        StockService service = stocked();
        service.cut("1145A", 1, LATER, "41207");
        List<StockLineRow> found = service.lines().since(LATER);
        assertThat(movementsOf(found)).containsExactly("cut");
    }

    @Test
    void theRestrictedPartsAreTheOnesTheFactoryWatches() {
        StockService service = new StockService(session);
        assertThat(parts(service.blanks().restricted())).containsExactly("1145A");
    }

    @Test
    void thePartsOnOneSectionComeBackTogether() {
        StockService service = new StockService(session);
        int sectionId = service.blank("1145A").getSectionId();
        List<BlankRow> found = service.blanks().onSection(sectionId);
        assertThat(parts(found)).containsExactly("1145A", "RA4");
    }

    @Test
    void theOrdersFromOneSupplierComeBackTogether() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        service.placeOrder("PO-9002", "brass and co", WHEN, List.of(new Wanted("RA4", 10)));
        List<PurchaseOrderRow> found = service.orders().fromSupplier("lockwood supply");
        assertThat(numbers(found)).containsExactly("PO-9001");
    }

    @Test
    void aScrappingCarriesItsReasonOntoTheLine() {
        StockService service = stocked();
        StockLineRow line = service.scrap("1145A", 1, LATER, "the milling was out");
        assertThat(line.getNote()).isEqualTo("the milling was out");
    }

    @Test
    void aReceiptAgainstAnOrderCarriesTheOrderNumber() {
        StockService service = new StockService(session);
        service.placeOrder("PO-9001", "lockwood supply", WHEN, List.of(new Wanted("1145A", 10)));
        service.bookIn("PO-9001", "1145A", 10, LATER);
        assertThat(service.movements("1145A").stream()
                        .map(StockLineRow::getReference)
                        .toList())
                .containsExactly("PO-9001");
    }
}
