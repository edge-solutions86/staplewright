package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiStockTest {

    private static final String WHEN = "2025-04-08";
    private static final String LATER = "2025-05-20";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(ApiStockTest::blanks);
        client = Api.client();
    }

    private static void blanks(Session session) {
        KeywaySectionRow section = new KeywaySectionRow("Sargent LA", "L", "Sargent");
        session.add(section);
        session.flush();
        session.add(blank(section, "1145A", true, 50, 200));
        session.add(blank(section, "RA4", false, 20, 40));
    }

    private static BlankRow blank(KeywaySectionRow section, String part, boolean restricted, int reorderAt, int stock) {
        BlankRow row = new BlankRow(part, section, "Sargent", 6);
        row.setRestricted(restricted);
        row.setReorderAt(reorderAt);
        row.setStock(stock);
        return row;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    private static List<Object> field(List<Map<String, Object>> rows, String key) {
        return rows.stream().map(row -> row.get(key)).toList();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> facts(Api.Answer found) {
        return (Map<String, Object>) found.json().get("facts");
    }

    private Api.Answer anOrder() {
        return anOrder("PO-9001", 250);
    }

    private Api.Answer anOrder(String number, int quantity) {
        return client.post(
                "/stock/orders",
                Map.of(
                        "number",
                        number,
                        "supplier",
                        "lockwood supply",
                        "placed_on",
                        WHEN,
                        "lines",
                        List.of(Map.of("part_number", "1145A", "quantity", quantity))));
    }

    @Test
    void theBlanksComeBackInPartNumberOrder() {
        Map<String, Object> body = client.get("/stock/blanks").json();
        assertThat(field(listOf(body, "blanks"), "part_number")).containsExactly("1145A", "RA4");
    }

    @Test
    void onlyTheRestrictedPartsWhenAsked() {
        Map<String, Object> body =
                client.get("/stock/blanks", "restricted", true).json();
        assertThat(field(listOf(body, "blanks"), "part_number")).containsExactly("1145A");
    }

    @Test
    void oneBlankCarriesBothTotals() {
        Map<String, Object> body = client.get("/stock/blanks/1145A").json();
        assertThat(body.get("stock")).isEqualTo(200);
        assertThat(body.get("ledger_total")).isEqualTo(0);
    }

    @Test
    void aPartNobodyStocksIsA404() {
        assertThat(client.get("/stock/blanks/9999Z").status()).isEqualTo(404);
    }

    @Test
    void receivingBrassLeavesAMovement() {
        Api.Answer found = client.post(
                "/stock/blanks/1145A/receipts", Map.of("quantity", 100, "moved_on", WHEN, "reference", "PO-4412"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("signed")).isEqualTo(100);
        assertThat(client.get("/stock/blanks/1145A").json().get("stock")).isEqualTo(300);
    }

    @Test
    void receivingNothingIsRejectedBeforeItReachesTheService() {
        Api.Answer found = client.post("/stock/blanks/1145A/receipts", Map.of("quantity", 0, "moved_on", WHEN));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void cuttingARestrictedBlankNeedsAReference() {
        Api.Answer found = client.post("/stock/blanks/1145A/cuts", Map.of("quantity", 2, "moved_on", LATER));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void cuttingWithAReferenceTakesItOut() {
        Api.Answer found =
                client.post("/stock/blanks/1145A/cuts", Map.of("quantity", 2, "moved_on", LATER, "reference", "41207"));
        assertThat(found.json().get("signed")).isEqualTo(-2);
    }

    @Test
    void cuttingAnOpenBlankNeedsNothing() {
        Api.Answer found = client.post("/stock/blanks/RA4/cuts", Map.of("quantity", 2, "moved_on", LATER));
        assertThat(found.status()).isEqualTo(201);
    }

    @Test
    void cuttingMoreThanTheDrawerHoldsIsA409() {
        Api.Answer found = client.post("/stock/blanks/RA4/cuts", Map.of("quantity", 41, "moved_on", LATER));
        assertThat(found.status()).isEqualTo(409);
        assertThat(facts(found).get("held")).isEqualTo(40);
    }

    @Test
    void scrappingSaysWhyOrItIsRejected() {
        Api.Answer found =
                client.post("/stock/blanks/RA4/scrappings", Map.of("quantity", 1, "moved_on", LATER, "note", ""));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void scrappingWithAReasonTakesItOff() {
        Api.Answer found = client.post(
                "/stock/blanks/RA4/scrappings", Map.of("quantity", 1, "moved_on", LATER, "note", "milled wrong"));
        assertThat(found.json().get("signed")).isEqualTo(-1);
    }

    @Test
    void aBlankThatComesBackGoesInAgain() {
        Api.Answer found = client.post("/stock/blanks/RA4/returns", Map.of("quantity", 1, "moved_on", LATER));
        assertThat(found.json().get("movement")).isEqualTo("returned");
    }

    @Test
    void aCountReportsTheGap() {
        Map<String, Object> found = client.post("/stock/blanks/1145A/count", Map.of("counted", 188, "moved_on", LATER))
                .json();
        assertThat(found.get("expected")).isEqualTo(200);
        assertThat(found.get("difference")).isEqualTo(-12);
        assertThat(found.get("agrees")).isEqualTo(false);
    }

    @Test
    void aCountThatAgreesSaysSo() {
        Map<String, Object> found = client.post("/stock/blanks/RA4/count", Map.of("counted", 40, "moved_on", LATER))
                .json();
        assertThat(found.get("agrees")).isEqualTo(true);
        assertThat((String) found.get("note")).endsWith("agrees");
    }

    @Test
    void aCountMovesTheTotal() {
        client.post("/stock/blanks/1145A/count", Map.of("counted", 188, "moved_on", LATER));
        assertThat(client.get("/stock/blanks/1145A").json().get("stock")).isEqualTo(188);
    }

    @Test
    void theMovementsComeBackInOrder() {
        client.post("/stock/blanks/RA4/cuts", Map.of("quantity", 1, "moved_on", LATER));
        client.post("/stock/blanks/RA4/returns", Map.of("quantity", 1, "moved_on", LATER));
        Map<String, Object> body = client.get("/stock/blanks/RA4/movements").json();
        assertThat(field(listOf(body, "movements"), "movement")).containsExactly("cut", "returned");
    }

    @Test
    void theReorderListNamesWhatIsShort() {
        client.post("/stock/blanks/RA4/cuts", Map.of("quantity", 25, "moved_on", LATER));
        Map<String, Object> body = client.get("/stock/reorder").json();
        assertThat(field(listOf(body, "below"), "part_number")).containsExactly("RA4");
    }

    @Test
    void whatWasCutAgainstATagComesBack() {
        client.post("/stock/blanks/1145A/cuts", Map.of("quantity", 1, "moved_on", LATER, "reference", "41207"));
        Map<String, Object> body = client.get("/stock/cut-against/41207").json();
        assertThat(field(listOf(body, "cuts"), "reference")).containsExactly("41207");
    }

    @Test
    void anOrderGoesInWithItsLines() {
        Api.Answer found = anOrder();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("state")).isEqualTo("placed");
        assertThat(listOf(found.json(), "lines").get(0).get("outstanding")).isEqualTo(250);
    }

    @Test
    void anOrderWithNoLinesIsRejected() {
        Api.Answer found = client.post(
                "/stock/orders",
                Map.of("number", "PO-9001", "supplier", "lockwood supply", "placed_on", WHEN, "lines", List.of()));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void anOrderNumberIsNotReused() {
        anOrder();
        assertThat(anOrder().status()).isEqualTo(409);
    }

    @Test
    void aPartDeliveryLeavesTheOrderOpen() {
        anOrder();
        Map<String, Object> found = client.post(
                        "/stock/orders/PO-9001/deliveries",
                        Map.of("part_number", "1145A", "quantity", 100, "arrived_on", LATER))
                .json();
        assertThat(found.get("state")).isEqualTo("part received");
        assertThat(listOf(found, "lines").get(0).get("outstanding")).isEqualTo(150);
    }

    @Test
    void theLastDeliveryClosesTheOrder() {
        anOrder("PO-9001", 10);
        Map<String, Object> found = client.post(
                        "/stock/orders/PO-9001/deliveries",
                        Map.of("part_number", "1145A", "quantity", 10, "arrived_on", LATER))
                .json();
        assertThat(found.get("state")).isEqualTo("received");
    }

    @Test
    void moreArrivingThanWasOrderedIsA409() {
        anOrder("PO-9001", 10);
        Api.Answer found = client.post(
                "/stock/orders/PO-9001/deliveries",
                Map.of("part_number", "1145A", "quantity", 11, "arrived_on", LATER));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void onlyTheOpenOrdersAreListed() {
        anOrder("PO-9001", 10);
        anOrder("PO-9002", 10);
        client.post(
                "/stock/orders/PO-9002/deliveries",
                Map.of("part_number", "1145A", "quantity", 10, "arrived_on", LATER));
        Map<String, Object> body = client.get("/stock/orders").json();
        assertThat(field(listOf(body, "orders"), "number")).containsExactly("PO-9001");
    }

    @Test
    void cancellingNeedsAReason() {
        anOrder();
        assertThat(client.post("/stock/orders/PO-9001/cancellation", Map.of("note", ""))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void aCancelledOrderSaysSo() {
        anOrder();
        Api.Answer found = client.post("/stock/orders/PO-9001/cancellation", Map.of("note", "supplier cannot get it"));
        assertThat(found.json().get("state")).isEqualTo("cancelled");
    }

    @Test
    void anOrderNobodyPlacedIsA404() {
        assertThat(client.get("/stock/orders/PO-0000").status()).isEqualTo(404);
    }
}
