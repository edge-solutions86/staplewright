package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.services.IncidentService;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiIncidentsTest {

    private static final String WHEN = "2025-04-08";

    private Api client;

    /** The campus with both counters open, so the first report is the first number. */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(session -> {
            DbCampus.build(session);
            new IncidentService(session).openSeries();
        });
        client = Api.client();
    }

    private Api.Answer report() {
        return report("41207");
    }

    private Api.Answer report(String tag) {
        return report(tag, Map.of());
    }

    /** A lost key reported on the day, with any field of the body overridden. */
    private Api.Answer report(String tag, Map<String, Object> overrides) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("tag", tag);
        body.put("cause", "lost");
        body.put("reported_on", WHEN);
        body.putAll(overrides);
        return client.post("/incidents", body);
    }

    private String reported(String tag) {
        return reported(tag, Map.of());
    }

    private String reported(String tag, Map<String, Object> overrides) {
        return (String) report(tag, overrides).json().get("number");
    }

    @Test
    void reportingGivesBackANumberAndAGrade() {
        Api.Answer found = report();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("number")).isEqualTo("I-0001");
        assertThat(found.json().get("severity")).isEqualTo("routine");
    }

    @Test
    void aGrandMasterIsGradedCritical() {
        Api.Answer found = report("41211");
        assertThat(found.json().get("severity")).isEqualTo("critical");
    }

    @Test
    void aTheftNeedsANote() {
        Api.Answer found = report("41207", Map.of("cause", "stolen"));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void aTheftWithANoteLands() {
        Api.Answer found = report("41207", Map.of("cause", "stolen", "note", "taken from a coat in the hall"));
        assertThat(found.json().get("cause")).isEqualTo("stolen");
    }

    @Test
    void aCauseNobodyHasHeardOfNeverReachesTheService() {
        Api.Answer found = report("41207", Map.of("cause", "mislaid"));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void aTagNobodyIssuedIsA404() {
        Api.Answer found = report("99999");
        assertThat(found.status()).isEqualTo(404);
    }

    @Test
    void aKeyCannotBeReportedTwice() {
        report();
        assertThat(report().status()).isEqualTo(409);
    }

    @Test
    void anIncidentReadsBack() {
        String number = reported("41207");
        Api.Answer found = client.get("/incidents/" + number);
        assertThat(found.json().get("symbol")).isEqualTo("AA1");
    }

    @Test
    void anIncidentThatIsNotThere() {
        assertThat(client.get("/incidents/I-9999").status()).isEqualTo(404);
    }

    @Test
    void theAssessmentListsTheDoorsAndTheCylinders() {
        String number = reported("41207");
        Map<String, Object> found =
                client.get("/incidents/" + number + "/assessment").json();
        assertThat(found.get("openings")).isEqualTo(List.of("MOO-214", "MOO-216"));
        assertThat(found.get("cylinders")).isEqualTo(List.of("C-1001", "C-1002"));
        assertThat(found.get("keys_to_recut")).isEqualTo(List.of("41208"));
    }

    @Test
    void theAssessmentNamesTheExteriorDoors() {
        String number = reported("41210");
        Map<String, Object> found =
                client.get("/incidents/" + number + "/assessment").json();
        assertThat(found.get("exterior_openings")).isEqualTo(List.of("MOO-101"));
    }

    @Test
    void thePlanNamesTheScopeAndTheReason() {
        String number = reported("41207");
        Map<String, Object> found = client.get("/incidents/" + number + "/plan").json();
        assertThat(found.get("scope")).isEqualTo("group");
        assertThat(found.get("cylinders")).isEqualTo(List.of("C-1001", "C-1002"));
        assertThat(found.get("symbols")).isEqualTo(List.of("AA1"));
    }

    @Test
    void aMasterPlansAWiderScope() {
        String number = reported("41211");
        assertThat(client.get("/incidents/" + number + "/plan").json().get("scope"))
                .isEqualTo("system");
    }

    @Test
    void anOrderIsRaisedWithALineACylinder() {
        String number = reported("41207");
        Api.Answer found = client.post("/incidents/" + number + "/orders", Map.of());
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json().get("lines")).isEqualTo(2);
        assertThat(found.json().get("incident")).isEqualTo(number);
    }

    @Test
    void anOrderCanBeBookedForADay() {
        String number = reported("41207");
        Api.Answer found = client.post("/incidents/" + number + "/orders", Map.of("scheduled_for", "2025-04-15"));
        assertThat(found.json().get("scheduled_for")).isEqualTo("2025-04-15");
    }

    @Test
    void anIncidentThatCallsForNoWorkRaisesNothing() {
        String number = reported("41207", Map.of("retire_cut", false));
        client.post("/incidents/" + number + "/orders", Map.of());
        String second = reported("41208");
        assertThat(client.post("/incidents/" + second + "/orders", Map.of()).status())
                .isIn(201, 409);
    }
}
