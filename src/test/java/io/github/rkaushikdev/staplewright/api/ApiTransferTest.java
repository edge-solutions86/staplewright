package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.health.Finding;
import io.github.rkaushikdev.staplewright.health.Health;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.services.TransferService;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiTransferTest {

    private static final String CLAIMED = "2025-03-04";
    private static final String SOON = "2025-03-10";
    private static final String LATE = "2025-05-01";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(session -> {
            for (String name : List.of("kmaki", "rlaakso", "jkoivu")) {
                KeyholderRow person = new KeyholderRow(name, name, "staff", "grounds");
                person.setStanding("active");
                session.add(person);
            }
            session.flush();
            session.addAll(List.of(key("41207", "AA1"), key("41211", "A")));
            new TransferService(session).openSeries();
        });
        client = Api.client();
    }

    private static IssuedKeyRow key(String tag, String symbol) {
        IssuedKeyRow row = new IssuedKeyRow(tag, symbol, "kmaki", LocalDate.of(2025, 1, 6));
        row.setDueOn(LocalDate.of(2026, 1, 6));
        row.setState("out");
        return row;
    }

    private Api.Answer claim() {
        return claim("41207", "rlaakso");
    }

    private Api.Answer claim(String tag, String passedTo) {
        return client.post("/handovers", Map.of("tag", tag, "passed_to", passedTo, "claimed_on", CLAIMED));
    }

    private void confirmed() {
        claim();
        client.post("/handovers/T-00001/confirmation", Map.of("confirmed_on", SOON));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> items(Map<String, Object> body, String key) {
        return (List<Object>) body.get(key);
    }

    private static List<Object> numbers(Map<String, Object> body) {
        return listOf(body, "handovers").stream().map(row -> row.get("number")).toList();
    }

    @Test
    void aClaimGetsANumber() {
        Api.Answer found = claim();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("number")).isEqualTo("T-00001");
        assertThat(found.json().get("state")).isEqualTo("claimed");
    }

    @Test
    void aClaimNamesWhoTheLedgerSaysHadIt() {
        assertThat(claim().json().get("passed_by")).isEqualTo("kmaki");
    }

    @Test
    void aKeyNobodyIssuedIsA404() {
        assertThat(claim("99999", "rlaakso").status()).isEqualTo(404);
    }

    @Test
    void aMasterDoesNotMoveBetweenPeople() {
        assertThat(claim("41211", "rlaakso").status()).isEqualTo(409);
    }

    @Test
    void aHandoverToSomebodyNobodyHasIsA404() {
        assertThat(claim("41207", "nobody").status()).isEqualTo(404);
    }

    @Test
    void oneKeyCannotBeClaimedTwice() {
        claim();
        assertThat(claim("41207", "jkoivu").status()).isEqualTo(409);
    }

    @Test
    void aClaimDoesNotMoveTheKey() {
        claim();
        Map<String, Object> body = client.get("/keys/41207/holder", "on", LATE).json();
        assertThat(body.get("holder")).isEqualTo("kmaki");
        assertThat(items(body, "chain")).isEmpty();
    }

    @Test
    void confirmingMovesTheKey() {
        claim();
        Api.Answer found =
                client.post("/handovers/T-00001/confirmation", Map.of("confirmed_on", SOON, "note", "picked it up"));
        assertThat(found.json().get("state")).isEqualTo("confirmed");
        Map<String, Object> body = client.get("/keys/41207/holder", "on", LATE).json();
        assertThat(body.get("holder")).isEqualTo("rlaakso");
        assertThat(items(body, "chain")).containsExactly("rlaakso");
    }

    @Test
    void aRefusalNeedsAReason() {
        claim();
        Api.Answer found = client.post("/handovers/T-00001/refusal", Map.of("refused_on", SOON, "note", ""));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void aRefusalLeavesTheKeyWhereItWas() {
        claim();
        client.post("/handovers/T-00001/refusal", Map.of("refused_on", SOON, "note", "never got it"));
        assertThat(client.get("/keys/41207/holder", "on", LATE).json().get("holder"))
                .isEqualTo("kmaki");
    }

    @Test
    void aClaimCanBeTakenBack() {
        claim();
        Api.Answer found = client.post("/handovers/T-00001/cancellation", Map.of("cancelled_on", SOON));
        assertThat(found.json().get("state")).isEqualTo("cancelled");
    }

    @Test
    void aHandoverNobodyClaimedIsA404() {
        assertThat(client.get("/handovers/T-99999").status()).isEqualTo(404);
    }

    @Test
    void theOpenClaimsAreListed() {
        claim();
        assertThat(numbers(client.get("/handovers").json())).containsExactly("T-00001");
    }

    @Test
    void aStateNobodyRecognisesIsA422() {
        assertThat(client.get("/handovers", "state", "posted").status()).isEqualTo(422);
    }

    @Test
    void theSettledClaimsCanBeListedToo() {
        confirmed();
        assertThat(numbers(client.get("/handovers", "state", "confirmed").json()))
                .containsExactly("T-00001");
    }

    @Test
    void theLateClaimsComeWithTheExposure() {
        claim();
        Map<String, Object> body = client.get("/handovers/late", "on", LATE).json();
        assertThat(items(body, "late")).hasSize(1);
        assertThat(body.get("exposure")).isEqualTo(Map.of("rlaakso", 1));
    }

    @Test
    void nothingIsLateInsideTheWindow() {
        claim();
        assertThat(items(client.get("/handovers/late", "on", SOON).json(), "late"))
                .isEmpty();
    }

    @Test
    void theStraysNameTheLedgerAndTheTruth() {
        confirmed();
        Map<String, Object> body = client.get("/handovers/strays", "on", LATE).json();
        assertThat(listOf(body, "strays"))
                .containsExactly(Map.of("tag", "41207", "ledger", "kmaki", "actually", "rlaakso"));
    }

    @Test
    void aShopWithNoHandoversHasNoStrays() {
        assertThat(listOf(client.get("/handovers/strays", "on", LATE).json(), "strays"))
                .isEmpty();
    }

    @Test
    void theSweepClosesEveryLateClaim() {
        claim();
        Map<String, Object> body = client.post("/handovers/lapse?on=" + LATE).json();
        assertThat(items(body, "lapsed")).containsExactly("T-00001");
        assertThat(client.get("/handovers/T-00001").json().get("state")).isEqualTo("lapsed");
    }

    @Test
    void theSweepLeavesTheRecentClaimsAlone() {
        claim();
        assertThat(client.post("/handovers/lapse?on=" + SOON).json().get("count"))
                .isEqualTo(0);
    }

    @Test
    void whatAPersonActuallyHolds() {
        confirmed();
        Map<String, Object> body =
                client.get("/keyholders/rlaakso/holdings", "on", LATE).json();
        assertThat(items(body, "tags")).containsExactly("41207");
    }

    @Test
    void whatAPersonStillOwesASignatureOn() {
        claim();
        Map<String, Object> body =
                client.get("/keyholders/rlaakso/holdings", "on", SOON).json();
        assertThat(items(body, "waiting_on")).containsExactly("T-00001");
    }

    @Test
    void theSelfCheckReportsAKeyThatWandered() {
        confirmed();
        List<String> lines =
                TestDatabase.database().fromSession(session -> Health.healthReport(session, LocalDate.of(2025, 5, 1)));
        assertThat(lines).anyMatch(line -> line.contains("keys that changed hands"));
    }

    @Test
    void theSelfCheckReportsAClaimNobodyConfirmed() {
        claim();
        List<Finding> findings =
                TestDatabase.database().fromSession(session -> Health.runChecks(session, LocalDate.of(2025, 5, 1)));
        assertThat(Health.exitCode(findings)).isEqualTo(1);
        assertThat(findings).anyMatch(item -> item.check().equals("handovers nobody confirmed"));
    }
}
