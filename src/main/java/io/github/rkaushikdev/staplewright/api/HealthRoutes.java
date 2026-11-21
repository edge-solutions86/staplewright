package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.health.Finding;
import io.github.rkaushikdev.staplewright.health.Health;
import io.github.rkaushikdev.staplewright.health.Severity;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The self check over http, for whatever is watching this service.
 *
 * <p>Two routes answer two different questions. {@code /health} is the one a load balancer asks
 * fifty times a minute: is the process up and can it reach the database. It runs no checks, because
 * a readiness probe that walks eight tables falls over under the load it creates. {@code
 * /health/report} is the one somebody looks at on a Monday; it runs everything and is expected to be
 * slow. The report is always a 200, because the request succeeded: the findings are the answer, not
 * an error. A monitor that wants to alarm reads {@code worst} and decides for itself.
 */
@RestController
@RequestMapping("/health")
@Tag(name = "service")
public class HealthRoutes {

    private final Work work;

    public HealthRoutes(Work work) {
        this.work = work;
    }

    /** One finding as the service sends it. */
    static Map<String, Object> findingShape(Finding finding) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("check", finding.check());
        out.put("severity", finding.severity().label());
        out.put("subject", finding.subject());
        out.put("detail", finding.detail());
        return out;
    }

    /** Up, and able to reach the database. Nothing else. */
    @GetMapping
    public Map<String, Object> live() {
        return work.apply(session -> {
            session.entityManager().createNativeQuery("select 1").getSingleResult();
            return Map.of("status", "up", "version", Staplewright.VERSION);
        });
    }

    @GetMapping("/checks")
    public Map<String, Object> checks() {
        return Map.of("checks", Health.checkNames());
    }

    @GetMapping("/report")
    public ResponseEntity<?> report(
            @RequestParam Optional<LocalDate> on, @RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(work.apply(session -> Health.healthReport(session, on.orElseGet(LocalDate::now))), plain);
    }

    @GetMapping("/findings")
    public Map<String, Object> findings(
            @RequestParam Optional<LocalDate> on,
            @RequestParam(defaultValue = "") @Pattern(regexp = "^(|note|warning|fault)$") String severity) {
        LocalDate today = on.orElseGet(LocalDate::now);
        List<Finding> found = work.apply(session -> Health.runChecks(session, today));
        if (!severity.isEmpty()) {
            found = found.stream()
                    .filter(item -> item.severity().label().equals(severity))
                    .toList();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("on", today.toString());
        out.put("worst", Health.worst(found).map(Severity::label).orElse(null));
        out.put("counts", Health.countsBySeverity(found));
        out.put("exit_code", Health.exitCode(found));
        out.put("findings", found.stream().map(HealthRoutes::findingShape).toList());
        return out;
    }

    @GetMapping("/findings/{name}")
    public Map<String, Object> oneCheck(@PathVariable String name, @RequestParam Optional<LocalDate> on) {
        LocalDate today = on.orElseGet(LocalDate::now);
        Health.Check check = Health.checkByName(name);
        List<Finding> found = work.apply(session -> Health.runChecks(session, today, List.of(check)));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("check", name);
        out.put("on", today.toString());
        out.put("findings", found.stream().map(HealthRoutes::findingShape).toList());
        return out;
    }
}
