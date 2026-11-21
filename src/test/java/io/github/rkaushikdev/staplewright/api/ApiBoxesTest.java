package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiBoxesTest {

    private static final String SEALED = "2025-01-06";
    private static final String WALKED = "2025-06-30";
    private static final String LATE = "2025-12-01";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        client = Api.client();
    }

    private Api.Answer install() {
        return install("KB-01", Map.of());
    }

    private Api.Answer install(String number, Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "number", number,
                "building", "MOO",
                "opening", "MOO-101",
                "symbol", "AA1",
                "seal", "44012",
                "sealed_on", SEALED));
        body.putAll(overrides);
        return client.post("/boxes", body);
    }

    private Api.Answer opened() {
        return client.post(
                "/boxes/KB-01/openings",
                Map.of("opened_by", "fire service", "opened_on", WALKED, "note", "kitchen fire"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    private static List<Object> numbers(Map<String, Object> body, String key) {
        return rows(body, key).stream().map(row -> row.get("number")).toList();
    }

    @Test
    void aBoxGoesUp() {
        Api.Answer found = install();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json()).containsEntry("state", "sealed").containsEntry("symbol", "AA1");
    }

    @Test
    void aBoxOnADoorNobodyHasIsA404() {
        assertThat(install("KB-01", Map.of("opening", "MOO-999")).status()).isEqualTo(404);
    }

    @Test
    void aSecondBoxOnOneDoorIsA409() {
        install();
        assertThat(install("KB-02", Map.of()).status()).isEqualTo(409);
    }

    @Test
    void aMasterOnAWallIsA409() {
        assertThat(install("KB-01", Map.of("symbol", "A")).status()).isEqualTo(409);
    }

    @Test
    void aSymbolNobodyCanParseIsA422() {
        assertThat(install("KB-01", Map.of("symbol", "!!")).status()).isEqualTo(400);
    }

    @Test
    void theBoxesComeBackInNumberOrder() {
        install();
        install("KB-02", Map.of("opening", "MOO-110"));
        assertThat(numbers(client.get("/boxes").json(), "boxes")).containsExactly("KB-01", "KB-02");
    }

    @Test
    void theListingCanBeNarrowedToABuilding() {
        install();
        assertThat(rows(client.get("/boxes", "building", "VOS").json(), "boxes"))
                .isEmpty();
    }

    @Test
    void aBoxNobodyInstalledIsA404() {
        assertThat(client.get("/boxes/KB-99").status()).isEqualTo(404);
    }

    @Test
    void readingTheRightSealRecordsTheWalk() {
        install();
        Map<String, Object> found = client.post("/boxes/KB-01/checks", Map.of("seal", "44012", "read_on", WALKED))
                .json();
        assertThat(found).containsEntry("state", "sealed").containsEntry("checked_on", WALKED);
    }

    @Test
    void aSealThatDoesNotMatchOpensTheBox() {
        install();
        Map<String, Object> found = client.post("/boxes/KB-01/checks", Map.of("seal", "44099", "read_on", WALKED))
                .json();
        assertThat(found).containsEntry("state", "opened").containsEntry("opened_by", "nobody signed for it");
    }

    @Test
    void aReadingWithNoSealIsA422() {
        install();
        assertThat(client.post("/boxes/KB-01/checks", Map.of("seal", "", "read_on", WALKED))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void reportingAnOpeningNamesWho() {
        install();
        assertThat(opened().json()).containsEntry("opened_by", "fire service");
    }

    @Test
    void anOpenerNobodyRecognisesIsA422() {
        install();
        assertThat(client.post("/boxes/KB-01/openings", Map.of("opened_by", "the caretaker", "opened_on", WALKED))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void aBoxCannotBeOpenedTwice() {
        install();
        opened();
        assertThat(opened().status()).isEqualTo(409);
    }

    @Test
    void anOpenBoxCanBeFoundEmpty() {
        install();
        opened();
        assertThat(client.post("/boxes/KB-01/emptied", Map.of("found_on", LATE, "note", "gone"))
                        .json())
                .containsEntry("state", "empty");
    }

    @Test
    void aSealedBoxCannotBeFoundEmpty() {
        install();
        assertThat(client.post("/boxes/KB-01/emptied", Map.of("found_on", LATE)).status())
                .isEqualTo(409);
    }

    @Test
    void resealingPutsItBack() {
        install();
        opened();
        Map<String, Object> found = client.post(
                        "/boxes/KB-01/reseal", Map.of("seal", "44120", "symbol", "AA2", "sealed_on", LATE))
                .json();
        assertThat(found)
                .containsEntry("state", "sealed")
                .containsEntry("seal", "44120")
                .containsEntry("opened_on", null);
    }

    @Test
    void resealingWithTheSameNumberIsA409() {
        install();
        opened();
        assertThat(client.post("/boxes/KB-01/reseal", Map.of("seal", "44012", "symbol", "AA1", "sealed_on", LATE))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void takingABoxDownSaysWhy() {
        install();
        assertThat(client.post("/boxes/KB-01/withdrawal", Map.of("taken_on", LATE, "note", ""))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void aWithdrawnBoxIsOffTheWall() {
        install();
        assertThat(client.post("/boxes/KB-01/withdrawal", Map.of("taken_on", LATE, "note", "building came down"))
                        .json())
                .containsEntry("state", "withdrawn");
    }

    @Test
    void theSealHistoryCarriesBothSeals() {
        install();
        opened();
        client.post("/boxes/KB-01/reseal", Map.of("seal", "44120", "symbol", "AA2", "sealed_on", LATE));
        List<Map<String, Object>> seals =
                rows(client.get("/boxes/KB-01/seals", "on", LATE).json(), "seals");
        assertThat(seals.stream().map(row -> row.get("seal"))).containsExactly("44012", "44120");
        assertThat(seals.get(0)).containsEntry("broken_by", "fire service");
        assertThat(seals.get(1)).containsEntry("current", true);
    }

    @Test
    void whichKeyWasInTheBoxOnADay() {
        install();
        opened();
        client.post("/boxes/KB-01/reseal", Map.of("seal", "44120", "symbol", "AA2", "sealed_on", LATE));
        assertThat(client.get("/boxes/KB-01/key-on/2025-03-01").json()).containsEntry("symbol", "AA1");
        assertThat(client.get("/boxes/KB-01/key-on/2025-12-02").json()).containsEntry("symbol", "AA2");
    }

    @Test
    void aDayBeforeTheBoxWentUpHasNoKey() {
        install();
        assertThat(client.get("/boxes/KB-01/key-on/2024-01-01").json()).containsEntry("symbol", null);
    }

    @Test
    void theOpenBoxesComeWithTheExposure() {
        install();
        opened();
        Map<String, Object> body = client.get("/boxes/open").json();
        assertThat(numbers(body, "open")).containsExactly("KB-01");
        assertThat(body.get("exposure")).isEqualTo(Map.of("MOO", 1));
    }

    @Test
    void aSealedCampusHasNothingOpen() {
        install();
        assertThat(rows(client.get("/boxes/open").json(), "open")).isEmpty();
    }

    @Test
    void theBuildingsWithNoBoxAreNamed() {
        install();
        assertThat(client.get("/boxes/uncovered").json().get("buildings")).isEqualTo(List.of("HEI", "VOS"));
    }

    @Test
    void theWalkRoundListsWhatNeedsDoing() {
        install();
        opened();
        Map<String, Object> body = client.get("/boxes/walk-round", "on", LATE).json();
        assertThat(numbers(body, "boxes")).containsExactly("KB-01");
        assertThat(body.get("overdue_reseal")).isEqualTo(List.of("KB-01"));
    }
}
