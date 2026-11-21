package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiAnnualTest {

    private static final String TODAY = "2025-06-30";
    private static final String LAST_YEAR = "2024-06-30";

    private Api client;

    /** The campus on the shared database, which both the service and the command line work on. */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        client = Api.client();
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> items(Map<String, Object> body, String key) {
        return (List<Object>) body.get(key);
    }

    @Test
    void theMeasurementComesBackWithoutFiling() {
        Map<String, Object> body = client.get("/returns/measure", "on", TODAY).json();
        assertThat(body.get("year")).isEqualTo(2025);
        assertThat(body.get("keys_out")).isEqualTo(5);
        assertThat(items(client.get("/returns").json(), "returns")).isEmpty();
    }

    @Test
    void theAverageIsRounded() {
        assertThat(client.get("/returns/measure", "on", TODAY).json().get("keys_per_holder"))
                .isEqualTo(1.25);
    }

    @Test
    void filingWritesTheYearDown() {
        Api.Answer found = client.post("/returns", Map.of("measured_on", TODAY, "filed_by", "rvirta"));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("year")).isEqualTo(2025);
        assertThat(found.json().get("filed_by")).isEqualTo("rvirta");
    }

    @Test
    void aYearCannotBeFiledTwice() {
        client.post("/returns", Map.of("measured_on", TODAY));
        assertThat(client.post("/returns", Map.of("measured_on", TODAY)).status())
                .isEqualTo(409);
    }

    @Test
    void aYearNobodyFiledIsA404() {
        assertThat(client.get("/returns/2019").status()).isEqualTo(404);
    }

    @Test
    void aFiledYearReadsBack() {
        client.post("/returns", Map.of("measured_on", TODAY));
        assertThat(client.get("/returns/2025").json().get("keys_out")).isEqualTo(5);
    }

    @Test
    void withdrawingNeedsAReason() {
        client.post("/returns", Map.of("measured_on", TODAY));
        assertThat(client.post("/returns/2025/withdrawal", Map.of("note", "")).status())
                .isEqualTo(422);
    }

    @Test
    void aWithdrawnYearSaysSo() {
        client.post("/returns", Map.of("measured_on", TODAY));
        Api.Answer found = client.post("/returns/2025/withdrawal", Map.of("note", "counted twice"));
        assertThat((String) found.json().get("note")).startsWith("withdrawn:");
    }

    @Test
    void theSheetComesBackAsLines() {
        Map<String, Object> body = client.get("/returns/sheet", "on", TODAY).json();
        assertThat(items(body, "lines").get(0)).isEqualTo("key control return for 2025");
    }

    @Test
    void theSheetComesBackAsPaperWhenAsked() {
        Api.Answer found = client.get("/returns/sheet", "on", TODAY, "plain", true);
        assertThat(found.contentType().orElseThrow()).startsWith("text/plain");
    }

    @Test
    void theTrendSaysWhenNothingIsFiled() {
        assertThat(items(client.get("/returns/trend").json(), "lines")).containsExactly("no returns have been filed");
    }

    @Test
    void theTrendGrowsARowPerYear() {
        client.post("/returns", Map.of("measured_on", LAST_YEAR));
        client.post("/returns", Map.of("measured_on", TODAY));
        assertThat(items(client.get("/returns/trend").json(), "lines")).hasSize(3);
    }

    @Test
    void theMastersComeBack() {
        Map<String, Object> body = client.get("/returns/masters").json();
        assertThat(items(body, "lines").get(0)).isEqualTo("3 master keys are out");
    }

    @Test
    void theConcentrationIsMostFirst() {
        Map<String, Object> body = client.get("/returns/concentration").json();
        assertThat(items(body, "holders").get(0)).isEqualTo(Map.of("keyholder", "phamm", "keys", 2));
    }

    @Test
    void theConcentrationCanBeCutShort() {
        Map<String, Object> body =
                client.get("/returns/concentration", "top", 1).json();
        assertThat(items(body, "holders")).hasSize(1);
    }

    @Test
    void aTopOfNothingIsA422() {
        assertThat(client.get("/returns/concentration", "top", 0).status()).isEqualTo(422);
    }

    @Test
    void theTakeCommandPrintsTheSheet() {
        List<String> lines = call("return", "take", "--on", TODAY).lines();
        assertThat(lines.get(0)).isEqualTo("key control return for 2025");
    }

    @Test
    void theFileCommandWritesTheYear() {
        Cli.Result found = call("return", "file", "--on", TODAY, "--by", "rvirta");
        assertThat(found.output().strip()).isEqualTo("2025 filed: 5 out, 0 unaccounted");
    }

    @Test
    void filingTwiceIsRefusedFromTheCommandLine() {
        call("return", "file", "--on", TODAY);
        assertThat(call("return", "file", "--on", TODAY).exitCode()).isNotZero();
    }

    @Test
    void withdrawingSaysSo() {
        call("return", "file", "--on", TODAY);
        Cli.Result found = call("return", "withdraw", "2025", "counted the shops twice");
        assertThat(found.output().strip()).isEqualTo("2025 withdrawn");
    }

    @Test
    void withdrawingAYearNobodyFiledIsRefused() {
        assertThat(call("return", "withdraw", "2019", "whatever").exitCode()).isNotZero();
    }

    @Test
    void theTrendCommandPrintsTheYears() {
        call("return", "file", "--on", LAST_YEAR);
        call("return", "file", "--on", TODAY);
        List<String> lines = call("return", "trend").lines();
        assertThat(lines.get(0)).startsWith("year");
        assertThat(lines.get(1)).startsWith("2024");
    }

    @Test
    void theMastersCommandListsThem() {
        assertThat(call("return", "masters").output()).contains("3 master keys are out");
    }

    @Test
    void theUnaccountedCommandIsQuietOnACleanYear() {
        assertThat(call("return", "unaccounted", "--on", TODAY).output()).contains("every key out is accounted for");
    }

    @Test
    void theConcentrationCommandNamesTheTopHolder() {
        assertThat(call("return", "concentration").lines().get(0)).startsWith("phamm");
    }

    @Test
    void theMovementCommandSaysThereIsNothingToCompare() {
        String output = call("return", "movement", "--on", TODAY).output();
        assertThat(output).contains("no return was filed last year");
    }

    @Test
    void theMovementCommandComparesAgainstAFiledYear() {
        call("return", "file", "--on", LAST_YEAR);
        List<String> lines = call("return", "movement", "--on", TODAY).lines();
        assertThat(lines.get(0)).isEqualTo("against 2024");
        assertThat(lines.get(1)).contains("level 0");
    }
}
