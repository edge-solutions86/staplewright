package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Cause;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.services.IncidentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Incidents over http: report a loss, read the assessment, raise the work.
 *
 * <p>The assessment is a read rather than a write, so it can be asked for as often as anybody likes
 * and always reflects the cylinders as they are today. That matters: an incident from March
 * reassessed in June gives a different answer because the campus changed, and the June answer is
 * the one that decides whether the work still needs doing.
 */
@RestController
@RequestMapping("/incidents")
@Tag(name = "incidents")
public class IncidentRoutes {

    private final Work work;

    public IncidentRoutes(Work work) {
        this.work = work;
    }

    /** A key reported missing. The cut is retired unless the body says to keep it. */
    public record Report(
            @NotNull @Size(min = 1, max = 32) String tag,
            @NotNull Cause cause,
            @NotNull LocalDate reportedOn,
            @Size(max = 512) String note,
            Boolean retireCut) {

        public Report {
            note = note == null ? "" : note;
            retireCut = retireCut == null || retireCut;
        }
    }

    /** A work order raised off an incident, booked for a day when one is given. */
    public record Booking(LocalDate scheduledFor) {}

    static Map<String, Object> incidentShape(IncidentRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("tag", row.getTag());
        out.put("symbol", row.getSymbol());
        out.put("cause", row.getCause());
        out.put("severity", row.getSeverity());
        out.put("reported_on", row.getReportedOn().toString());
        out.put("keyholder", row.getKeyholder());
        return out;
    }

    static Map<String, Object> assessmentShape(Assessment found) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", found.incident().number());
        out.put("severity", found.severity().label());
        out.put("openings", found.openings());
        out.put("buildings", found.buildings());
        out.put("cylinders", found.cylinders());
        out.put("exterior_openings", found.exteriorOpenings());
        out.put("keys_to_recut", found.keysToRecut());
        out.put("holders_affected", found.holdersAffected());
        return out;
    }

    private <T> T service(Function<IncidentService, T> call) {
        return work.apply(session ->
                call.apply(new IncidentService(session, work.settings().scope())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> report(@Valid @RequestBody Report body) {
        return service(incidents -> incidentShape(
                incidents.report(body.tag(), body.cause(), body.reportedOn(), body.note(), body.retireCut())));
    }

    @GetMapping("/{number}")
    public Map<String, Object> read(@PathVariable String number) {
        return service(incidents -> incidentShape(incidents.incidents().get(number)));
    }

    @GetMapping("/{number}/assessment")
    public Map<String, Object> assessment(@PathVariable String number) {
        return service(incidents -> assessmentShape(incidents.assessment(number)));
    }

    @GetMapping("/{number}/plan")
    public Map<String, Object> plan(@PathVariable String number) {
        return service(incidents -> {
            ScopeDecision decision = incidents.plan(number);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", number);
            out.put("scope", decision.scope().label());
            out.put("reason", decision.reason());
            out.put("over_cap", decision.overCap());
            out.put("cylinders", decision.cylinders());
            out.put(
                    "symbols",
                    decision.symbols().stream().map(KeySymbol::render).toList());
            return out;
        });
    }

    @PostMapping("/{number}/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> raiseOrder(@PathVariable String number, @Valid @RequestBody Booking body) {
        return service(incidents -> {
            WorkOrderRow order = body.scheduledFor() == null
                    ? incidents.raiseOrder(number)
                    : incidents.raiseOrder(number, body.scheduledFor());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", order.getNumber());
            out.put("incident", order.getIncidentNumber().orElse(null));
            out.put("state", order.getState());
            out.put(
                    "scheduled_for",
                    order.getScheduledFor().map(LocalDate::toString).orElse(null));
            out.put("lines", order.getLines().size());
            return out;
        });
    }
}
