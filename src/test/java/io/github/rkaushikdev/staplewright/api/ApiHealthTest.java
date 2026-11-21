package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.health.Health;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiHealthTest {

    private static final String TODAY = "2025-06-30";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        client = Api.client();
    }

    private static void unmapACylinder() {
        TestDatabase.database()
                .inSession(session ->
                        session.get(CylinderRow.class, "C-1001").orElseThrow().setGroup(null));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> findings(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("findings");
    }

    @Test
    void theLivenessProbeSaysUp() {
        assertThat(client.get("/health").json()).isEqualTo(Map.of("status", "up", "version", Staplewright.VERSION));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCheckNamesAreListed() {
        List<String> found = (List<String>) client.get("/health/checks").json().get("checks");
        assertThat(found).contains("unkeyed_openings").isSorted();
    }

    @Test
    void aCheckCanBeLookedUpByName() {
        assertThat(Health.checkByName("unkeyed_openings")).isSameAs(Health.CHECKS.get(0));
    }

    @Test
    void aCheckNobodyWroteIsA404() {
        assertThatThrownBy(() -> Health.checkByName("payroll_reconciliation")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theNamesMatchTheChecks() {
        assertThat(new HashSet<>(Health.checkNames()))
                .hasSize(Health.checkNames().size());
        assertThat(Health.checkNames()).hasSameSizeAs(Health.CHECKS);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theReportComesBackAsLines() {
        List<String> lines =
                (List<String>) client.get("/health/report", "on", TODAY).json().get("lines");
        assertThat(lines.get(0)).isEqualTo("self check at 2025-06-30");
    }

    @Test
    void theReportComesBackAsPaperWhenAsked() {
        Api.Answer found = client.get("/health/report", "on", TODAY, "plain", true);
        assertThat(found.contentType())
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
    }

    @Test
    void theReportDefaultsToToday() {
        assertThat(client.get("/health/report").status()).isEqualTo(200);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theFindingsCarryTheCounts() {
        Map<String, Object> body = client.get("/health/findings", "on", TODAY).json();
        assertThat((Map<String, Object>) body.get("counts")).containsOnlyKeys("note", "warning", "fault");
        assertThat(body.get("on")).isEqualTo(TODAY);
    }

    @Test
    void aCleanCampusHasNoWorst() {
        assertThat(client.get("/health/findings", "on", TODAY).json().get("exit_code"))
                .isEqualTo(0);
    }

    @Test
    void aFaultShowsUpInTheFindings() {
        unmapACylinder();
        Map<String, Object> body = client.get("/health/findings", "on", TODAY).json();
        assertThat(body.get("worst")).isEqualTo("fault");
        assertThat(body.get("exit_code")).isEqualTo(1);
        assertThat(findings(body)).anyMatch(item -> item.get("subject").equals("C-1001"));
    }

    @Test
    void theFindingsCanBeNarrowedToOneSeverity() {
        unmapACylinder();
        Map<String, Object> body =
                client.get("/health/findings", "on", TODAY, "severity", "fault").json();
        assertThat(findings(body)).isNotEmpty().allMatch(item -> item.get("severity")
                .equals("fault"));
    }

    @Test
    void aSeverityNobodyUsesIsA422() {
        assertThat(client.get("/health/findings", "on", TODAY, "severity", "urgent")
                        .status())
                .isEqualTo(422);
    }

    @Test
    void oneCheckCanBeRunOnItsOwn() {
        unmapACylinder();
        Map<String, Object> body =
                client.get("/health/findings/unkeyed_openings", "on", TODAY).json();
        assertThat(body.get("check")).isEqualTo("unkeyed_openings");
        assertThat(findings(body).stream().map(item -> item.get("subject"))).containsExactly("C-1001");
    }

    @Test
    void oneCheckThatFindsNothingComesBackEmpty() {
        assertThat(findings(client.get("/health/findings/uninstalled_cylinders", "on", TODAY)
                        .json()))
                .isEmpty();
    }

    @Test
    void aCheckNobodyWroteIsA404OverHttp() {
        assertThat(client.get("/health/findings/payroll").status()).isEqualTo(404);
    }

    @Test
    void theReportIsAlwaysATwoHundredEvenWithFaults() {
        unmapACylinder();
        assertThat(client.get("/health/report", "on", TODAY).status()).isEqualTo(200);
    }
}
