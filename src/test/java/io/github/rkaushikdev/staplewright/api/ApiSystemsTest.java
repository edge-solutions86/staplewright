package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.StaplewrightError;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ApiSystemsTest {

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::chartAndFamily);
        client = Api.client();
    }

    private Api.Answer openSystem() {
        return openSystem("chester park");
    }

    private Api.Answer openSystem(String name) {
        return client.post(
                "/systems", Map.of("name", name, "chart_id", 1, "family_name", "Sargent LA", "top_section", "L"));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    static Stream<Arguments> everyRefusalGetsTheStatusItDeserves() {
        return Stream.of(
                Arguments.of(new NotFoundError("no"), 404),
                Arguments.of(new ConflictError("no"), 409),
                Arguments.of(new CapacityError("no"), 409),
                Arguments.of(new StaplewrightError("no"), 400));
    }

    @ParameterizedTest
    @MethodSource
    void everyRefusalGetsTheStatusItDeserves(StaplewrightError error, int status) {
        assertThat(Errors.statusFor(error)).isEqualTo(status);
    }

    @Test
    void healthSaysUp() {
        Api.Answer found = client.get("/health");
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.json().get("status")).isEqualTo("up");
    }

    @Test
    void theSettingsCanBeRead() {
        Api.Answer found = client.get("/settings");
        assertThat(found.status()).isEqualTo(200);
        assertThat((List<?>) found.json().get("settings")).hasSize(9);
    }

    @Test
    void openingASystemGivesItBack() {
        Api.Answer found = openSystem();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("name")).isEqualTo("chester park");
    }

    @Test
    void aSystemCannotBeOpenedTwice() {
        openSystem();
        Api.Answer found = openSystem();
        assertThat(found.status()).isEqualTo(409);
        assertThat(found.json().get("code")).isEqualTo("conflict");
    }

    @Test
    void aSystemThatIsNotThereIsA404() {
        Api.Answer found = client.get("/systems/nothing");
        assertThat(found.status()).isEqualTo(404);
        assertThat(found.json().get("code")).isEqualTo("not_found");
    }

    @Test
    void aNewSystemHoldsOnlyTheTopKey() {
        openSystem();
        Map<String, Object> found = client.get("/systems/chester park").json();
        assertThat(found.get("symbols")).isEqualTo(1);
        assertThat(found.get("open_symbols")).isEqualTo(1);
    }

    @Test
    void drawingASymbolPutsItOnTheSchedule() {
        openSystem();
        Api.Answer found = client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json()).isEqualTo(Map.of("symbol", "A", "section", "LA"));
    }

    @Test
    void aSymbolWithNoParentIsRefused() {
        openSystem();
        Api.Answer found = client.post("/systems/chester park/symbols", Map.of("symbol", "AA1"));
        assertThat(found.status()).isEqualTo(400);
        assertThat(found.json().get("code")).isEqualTo("hierarchy");
    }

    @Test
    void theScheduleReadsBackInOrder() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        client.post("/systems/chester park/symbols", Map.of("symbol", "AA", "section", "LC"));
        Map<String, Object> found = client.get("/systems/chester park/schedule").json();
        assertThat(listOf(found, "entries").stream().map(entry -> entry.get("symbol")))
                .containsExactly("GGM", "A", "AA");
    }

    @Test
    void cuttingASymbolWritesTheCut() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        Api.Answer found = client.put("/systems/chester park/symbols/A/cut", Map.of("bitting", "642446"));
        assertThat(found.status()).isEqualTo(200);
        assertThat(found.json().get("bitting")).isEqualTo("642446");
    }

    @Test
    void aCutTheRulesRefuseIsAConflict() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        assertThat(client.put("/systems/chester park/symbols/A/cut", Map.of("bitting", "192156"))
                        .status())
                .isEqualTo(409);
    }

    @Test
    void aBittingThatIsNotABittingIsRefused() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        assertThat(client.put("/systems/chester park/symbols/A/cut", Map.of("bitting", "64q446"))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void anEmptyBittingNeverReachesTheDomain() {
        openSystem();
        assertThat(client.put("/systems/chester park/symbols/A/cut", Map.of("bitting", ""))
                        .status())
                .isEqualTo(422);
    }

    @Test
    void uncuttingClearsTheCut() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        client.put("/systems/chester park/symbols/A/cut", Map.of("bitting", "642446"));
        Map<String, Object> found =
                client.delete("/systems/chester park/symbols/A/cut").json();
        assertThat(found).containsEntry("bitting", null);
    }

    @Test
    void theReviewReportsWhatIsOpen() {
        openSystem();
        client.post("/systems/chester park/symbols", Map.of("symbol", "A", "section", "LA"));
        Map<String, Object> found = client.get("/systems/chester park/review").json();
        assertThat(listOf(found, "findings").stream()
                        .map(item -> item.get("code"))
                        .collect(Collectors.toSet()))
                .containsExactly("uncut");
    }

    @Test
    void aStepOutsideTheRangeIsRefused() {
        Map<String, Object> body =
                new HashMap<>(Map.of("name", "voss", "chart_id", 1, "family_name", "Sargent LA", "top_section", "L"));
        body.put("progression_step", 99);
        assertThat(client.post("/systems", body).status()).isEqualTo(422);
    }
}
