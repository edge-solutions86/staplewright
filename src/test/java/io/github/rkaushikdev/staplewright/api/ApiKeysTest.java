package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers;
import io.github.rkaushikdev.staplewright.services.CounterService;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.People;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiKeysTest {

    private static final String WHEN = People.TODAY.toString();
    private static final String LATER = "2025-06-02";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(session -> {
            Directory directory = People.aDirectory();
            for (Keyholder person : directory) {
                session.add(ControlMappers.keyholderToRow(person));
            }
            session.flush();
            for (Authorizer signer : directory.authorizers()) {
                session.add(ControlMappers.authorizerToRow(signer));
            }
            session.flush();
            new CounterService(session).openSeries();
        });
        client = Api.client();
    }

    private Api.Answer raiseRequest() {
        return raiseRequest(Map.of());
    }

    private Api.Answer raiseRequest(Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("keyholder", "kmaki");
        body.put("symbol", "AA1");
        body.put("building", "MOO");
        body.put("reason", "moved into 214");
        body.put("raised_on", WHEN);
        body.putAll(overrides);
        return client.post("/requests", body);
    }

    private String approved() {
        String number = (String) raiseRequest().json().get("number");
        client.post("/requests/" + number + "/approval", Map.of("signer", "rvirta", "decided_on", WHEN));
        return number;
    }

    private String issued() {
        String number = approved();
        return (String) client.post("/requests/" + number + "/key", Map.of("issued_on", WHEN))
                .json()
                .get("tag");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    @Test
    void aRequestComesBackWithItsNumber() {
        Api.Answer found = raiseRequest();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("number")).isEqualTo("R-0001");
        assertThat(found.json().get("state")).isEqualTo("submitted");
    }

    @Test
    void aRequestNeedsAReason() {
        assertThat(raiseRequest(Map.of("reason", "")).status()).isEqualTo(422);
    }

    @Test
    void aRequestForSomebodyWhoIsNotThere() {
        assertThat(raiseRequest(Map.of("keyholder", "ghost")).status()).isEqualTo(404);
    }

    @Test
    void aRequestReadsBack() {
        String number = (String) raiseRequest().json().get("number");
        Api.Answer found = client.get("/requests/" + number);
        assertThat(found.json().get("symbol")).isEqualTo("AA1");
    }

    @Test
    void aRequestThatIsNotThere() {
        assertThat(client.get("/requests/R-9999").status()).isEqualTo(404);
    }

    @Test
    void theRightSignatureApproves() {
        String number = (String) raiseRequest().json().get("number");
        Api.Answer found =
                client.post("/requests/" + number + "/approval", Map.of("signer", "rvirta", "decided_on", WHEN));
        assertThat(found.json().get("state")).isEqualTo("approved");
        assertThat(found.json().get("decided_by")).isEqualTo("rvirta");
    }

    @Test
    void aSignatureThatDoesNotReachIsAConflict() {
        String number = (String) raiseRequest().json().get("number");
        Api.Answer found =
                client.post("/requests/" + number + "/approval", Map.of("signer", "phamm", "decided_on", WHEN));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aDenialNeedsANote() {
        String number = (String) raiseRequest().json().get("number");
        Api.Answer found =
                client.post("/requests/" + number + "/denial", Map.of("signer", "rvirta", "decided_on", WHEN));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void aDenialWithANoteLands() {
        String number = (String) raiseRequest().json().get("number");
        Map<String, Object> body = Map.of("signer", "rvirta", "decided_on", WHEN, "note", "the room is shared");
        Api.Answer found = client.post("/requests/" + number + "/denial", body);
        assertThat(found.json().get("state")).isEqualTo("denied");
    }

    @Test
    void issuingGivesBackATagAndADeposit() {
        String number = approved();
        Api.Answer found = client.post("/requests/" + number + "/key", Map.of("issued_on", WHEN));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("tag")).isEqualTo("41000");
        assertThat(found.json().get("deposit_cents")).isEqualTo(2500);
    }

    @Test
    void aDepositCanBeWaived() {
        String number = approved();
        Map<String, Object> body = Map.of("issued_on", WHEN, "deposit_cents", 0);
        assertThat(client.post("/requests/" + number + "/key", body).json().get("deposit_cents"))
                .isEqualTo(0);
    }

    @Test
    void aNegativeDepositNeverReachesTheService() {
        String number = approved();
        Map<String, Object> body = Map.of("issued_on", WHEN, "deposit_cents", -1);
        assertThat(client.post("/requests/" + number + "/key", body).status()).isEqualTo(422);
    }

    @Test
    void anUnapprovedRequestIsNotFilled() {
        String number = (String) raiseRequest().json().get("number");
        Api.Answer found = client.post("/requests/" + number + "/key", Map.of("issued_on", WHEN));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aKeyReadsBack() {
        String tag = issued();
        assertThat(client.get("/keys/" + tag).json().get("state")).isEqualTo("out");
    }

    @Test
    void aReturnedKeySaysSo() {
        String tag = issued();
        Api.Answer found = client.post("/keys/" + tag + "/return", Map.of("closed_on", LATER));
        assertThat(found.json().get("state")).isEqualTo("returned");
    }

    @Test
    void aLostKeySaysSo() {
        String tag = issued();
        Map<String, Object> body = Map.of("closed_on", LATER, "note", "left on a bus");
        assertThat(client.post("/keys/" + tag + "/loss", body).json().get("state"))
                .isEqualTo("lost");
    }

    @Test
    void aKeyCannotComeBackTwice() {
        String tag = issued();
        client.post("/keys/" + tag + "/return", Map.of("closed_on", LATER));
        assertThat(client.post("/keys/" + tag + "/return", Map.of("closed_on", LATER))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void holdingsListWhatSomebodyHas() {
        issued();
        Api.Answer found = client.get("/keyholders/kmaki/keys");
        assertThat(listOf(found.json(), "keys").stream().map(key -> key.get("tag")))
                .containsExactly("41000");
        assertThat(found.json().get("deposits_held")).isEqualTo(2500);
    }

    @Test
    void somebodyHoldingNothingListsNothing() {
        Api.Answer found = client.get("/keyholders/dsalo/keys");
        assertThat(listOf(found.json(), "keys")).isEmpty();
        assertThat(found.json().get("deposits_held")).isEqualTo(0);
    }
}
