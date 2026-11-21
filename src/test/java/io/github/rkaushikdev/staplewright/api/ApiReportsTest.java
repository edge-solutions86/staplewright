package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiReportsTest {

    private static final String WHEN = "2025-03-04";

    private Api client;

    /** The campus, with one deposit taken on the key out to kmaki. */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(session -> {
            DbCampus.build(session);
            session.add(new DepositLineRow("41207", "kmaki", "charge", 2500, DbCampus.TODAY));
        });
        client = Api.client();
    }

    @SuppressWarnings("unchecked")
    private static List<String> lines(Api.Answer found) {
        return (List<String>) found.json().get("lines");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> keys(Api.Answer found) {
        return (List<Map<String, Object>>) found.json().get("keys");
    }

    @Test
    void aScheduleComesBackAsLines() {
        Api.Answer found = client.get("/reports/systems/chester park/schedule");
        assertThat(found.status()).isEqualTo(200);
        assertThat(lines(found).get(0)).isEqualTo("chester park");
    }

    @Test
    void aScheduleCanComeBackAsText() {
        Api.Answer found = client.get("/reports/systems/chester park/schedule", "plain", true);
        assertThat(found.contentType())
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(found.text()).startsWith("chester park");
    }

    @Test
    void theBittingListIsAnotherShapeOfTheSamePage() {
        Api.Answer found = client.get("/reports/systems/chester park/schedule", "shape", "bittings");
        assertThat(lines(found).get(0)).startsWith("cut");
    }

    @Test
    void theTreeIsAnotherShapeAgain() {
        Api.Answer found = client.get("/reports/systems/chester park/schedule", "shape", "tree");
        assertThat(lines(found)).anyMatch(line -> line.contains("leaf"));
    }

    @Test
    void aShapeNobodyHasHeardOfIsRefused() {
        Api.Answer found = client.get("/reports/systems/chester park/schedule", "shape", "pie");
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void aSystemThatIsNotThereIsA404() {
        assertThat(client.get("/reports/systems/nothing/schedule").status()).isEqualTo(404);
    }

    @Test
    void aStatementCarriesTheKeysAndAPlaceToSign() {
        List<String> found = lines(client.get("/reports/keyholders/kmaki/statement"));
        assertThat(found.get(0)).startsWith("Kirsi Maki");
        assertThat(found.get(found.size() - 1)).startsWith("signed");
    }

    @Test
    void aStatementCanBeDated() {
        Api.Answer found = client.get("/reports/keyholders/kmaki/statement", "on", WHEN);
        assertThat(lines(found)).contains("as at " + WHEN);
    }

    @Test
    void aKeyholderThatIsNotThereIsA404() {
        assertThat(client.get("/reports/keyholders/ghost/statement").status()).isEqualTo(404);
    }

    @Test
    void theDepositStatementShowsTheBalance() {
        List<String> found = lines(client.get("/reports/keyholders/kmaki/deposits"));
        assertThat(found.get(found.size() - 1)).isEqualTo("held $25.00");
    }

    @Test
    void aDepartmentSummaryNeedsADay() {
        assertThat(client.get("/reports/departments/geography").status()).isEqualTo(422);
    }

    @Test
    void aDepartmentSummaryCountsItsPeople() {
        Api.Answer found = client.get("/reports/departments/geography", "on", WHEN);
        assertThat(lines(found).get(0)).startsWith("geography: 4 on the books");
    }

    @Test
    void theChaseListIsQuietWhenNothingIsLate() {
        Api.Answer found = client.get("/reports/overdue", "on", WHEN);
        assertThat(lines(found).get(0)).startsWith("nothing overdue");
    }

    @Test
    void theLettersRunIsQuietWhenNothingIsLate() {
        Api.Answer found = client.get("/reports/letters", "on", WHEN);
        assertThat(lines(found).get(0)).isEqualTo("no letters to send today");
    }

    @Test
    void theCampusNumbersComeBack() {
        Api.Answer found = client.get("/reports/campus");
        assertThat(lines(found)).anyMatch(line -> line.contains("buildings"));
    }

    @Test
    void theCampusNumbersCanBePlain() {
        Api.Answer found = client.get("/reports/campus", "plain", true);
        assertThat(found.text()).contains("buildings");
    }

    @Test
    void theWidestKeysComeBackRanked() {
        List<Map<String, Object>> found = keys(client.get("/reports/exposure"));
        assertThat(found.get(0)).containsEntry("symbol", "GGM");
        assertThat(found.get(0)).containsEntry("openings", 6);
    }

    @Test
    void theExposureListCanBeCutShort() {
        assertThat(keys(client.get("/reports/exposure", "limit", 2))).hasSize(2);
    }

    @Test
    void aLimitOutsideTheRangeIsRefused() {
        assertThat(client.get("/reports/exposure", "limit", 0).status()).isEqualTo(422);
    }

    @Test
    void theKeysByBuildingComeBack() {
        @SuppressWarnings("unchecked")
        Map<String, Integer> found =
                (Map<String, Integer>) client.get("/reports/buildings").json().get("keys_by_building");
        assertThat(found.get("MOO")).isGreaterThanOrEqualTo(found.get("HEI"));
    }
}
