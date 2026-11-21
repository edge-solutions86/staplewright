package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.services.SystemService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Keying systems over http: open one, draw a symbol, cut it, read the schedule back.
 *
 * <p>The bodies are checked before they reach the domain, so a request carrying a bitting with a
 * letter in it is refused with the field named. Everything past that is a service call and a shape.
 */
@RestController
@RequestMapping("/systems")
@Tag(name = "systems")
public class SystemRoutes {

    private final Work work;

    public SystemRoutes(Work work) {
        this.work = work;
    }

    /** What it takes to open a keying system. */
    public record NewSystem(
            @NotNull @Size(min = 1, max = 128) String name,
            @NotNull @Positive Integer chartId,
            @NotNull @Size(min = 1, max = 128) String familyName,
            @NotNull @Size(min = 1, max = 32) String topSection,
            @Min(1) @Max(6) Integer progressionStep) {

        public NewSystem {
            progressionStep = progressionStep == null ? 2 : progressionStep;
        }
    }

    /** A symbol going on the schedule, cut or not. */
    public record NewSymbol(@NotNull @Size(min = 1, max = 16) String symbol, @Size(max = 32) String section) {}

    /** A bitting going onto a symbol. */
    public record Cut(@NotNull @Size(min = 1, max = 48) String bitting) {}

    static Map<String, Object> entryShape(ScheduleEntry entry) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("symbol", entry.symbol().render());
        out.put("level", entry.symbol().level());
        out.put("section", entry.section());
        out.put("bitting", entry.bitting().map(Bitting::render).orElse(null));
        out.put("note", entry.note());
        return out;
    }

    private <T> T service(Function<SystemService, T> call) {
        return work.apply(
                session -> call.apply(new SystemService(session, work.settings().cutting())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> openSystem(@Valid @RequestBody NewSystem body) {
        return service(systems -> {
            SystemRow row = systems.openSystem(
                    body.name(), body.chartId(), body.familyName(), body.topSection(), body.progressionStep());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", row.getName());
            out.put("id", row.getId());
            out.put("step", row.getProgressionStep());
            return out;
        });
    }

    @GetMapping("/{name}")
    public Map<String, Object> readSystem(@PathVariable String name) {
        return service(systems -> {
            SystemRow row = systems.system(name);
            KeyingSchedule schedule = systems.schedule(name);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("name", row.getName());
            out.put("family", row.getFamilyName());
            out.put("top_section", row.getTopSection());
            out.put("archived", row.isArchived());
            out.put("symbols", schedule.size());
            out.put("open_symbols", schedule.openSymbols().size());
            return out;
        });
    }

    @GetMapping("/{name}/schedule")
    public Map<String, Object> readSchedule(@PathVariable String name) {
        return service(systems -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("system", name);
            out.put(
                    "entries",
                    systems.schedule(name).entries().stream()
                            .map(SystemRoutes::entryShape)
                            .toList());
            return out;
        });
    }

    @PostMapping("/{name}/symbols")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> drawSymbol(@PathVariable String name, @Valid @RequestBody NewSymbol body) {
        return service(systems -> {
            ScheduleEntryRow row =
                    systems.draw(name, KeySymbol.parse(body.symbol()), Optional.ofNullable(body.section()));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", row.getSymbol().render());
            out.put("section", row.getSection());
            return out;
        });
    }

    @PutMapping("/{name}/symbols/{symbol}/cut")
    public Map<String, Object> cutSymbol(
            @PathVariable String name, @PathVariable String symbol, @Valid @RequestBody Cut body) {
        Bitting cut = Bitting.parse(body.bitting());
        return service(systems -> {
            ScheduleEntryRow row = systems.cut(name, KeySymbol.parse(symbol), cut);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", row.getSymbol().render());
            out.put("bitting", cut.render());
            return out;
        });
    }

    @DeleteMapping("/{name}/symbols/{symbol}/cut")
    public Map<String, Object> uncutSymbol(@PathVariable String name, @PathVariable String symbol) {
        return service(systems -> {
            ScheduleEntryRow row = systems.uncut(name, KeySymbol.parse(symbol));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", row.getSymbol().render());
            out.put("bitting", null);
            return out;
        });
    }

    @GetMapping("/{name}/review")
    public Map<String, Object> reviewSystem(@PathVariable String name) {
        return service(systems -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("system", name);
            out.put(
                    "findings",
                    systems.review(name).stream().map(Finding::asDict).toList());
            return out;
        });
    }
}
