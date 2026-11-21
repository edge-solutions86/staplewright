package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.Stamp;
import io.github.rkaushikdev.staplewright.persistence.models.BlindCodeRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.services.StampingService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stamping over http, with the one route that hands back the whole table.
 *
 * <p>{@code /stamping/book} exists because something has to be able to restore it, and it is the
 * only route in this service that would be worth an attacker's time on its own. It is separate from
 * everything else so that whatever sits in front of this service can be told about exactly one path.
 * {@code /stamping/identify/{code}} is the opposite: one code, one symbol, nothing to iterate. A
 * caller that wants the table by guessing codes has to ask four thousand times, and that is a shape
 * a log notices.
 */
@RestController
@RequestMapping("/stamping")
@Tag(name = "stamping")
public class StampingRoutes {

    private final Work work;

    public StampingRoutes(Work work) {
        this.work = work;
    }

    /** A symbol being given a blind code. */
    public record Assignment(
            @NotNull @Size(min = 1, max = 16) String symbol,
            @NotNull LocalDate assignedOn,
            @Size(max = 128) String assignedBy) {
        public Assignment {
            assignedBy = assignedBy == null ? "" : assignedBy;
        }
    }

    /** A run for the stamping machine. */
    public record Run(
            @NotNull @Size(min = 1) List<String> tags,
            @NotNull LocalDate stampedOn,
            @Size(max = 128) String stampedBy) {
        public Run {
            stampedBy = stampedBy == null ? "" : stampedBy;
        }
    }

    static Map<String, Object> stampShape(Stamp stamp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tag", stamp.tag());
        out.put("symbol", stamp.symbol());
        out.put("text", stamp.text());
        out.put("marking", stamp.marking().value());
        out.put("blind", stamp.isBlind());
        return out;
    }

    private <T> T service(Function<StampingService, T> call) {
        return work.apply(session ->
                call.apply(new StampingService(session, work.settings().stamping())));
    }

    @GetMapping("/identify/{code}")
    public Map<String, Object> identify(@PathVariable String code) {
        return service(stamping -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("code", code);
            out.put("symbol", stamping.identify(code));
            return out;
        });
    }

    @GetMapping("/symbols")
    public Map<String, Object> symbols() {
        return service(stamping -> Map.of("out", StampingService.issuedSymbols(stamping.session())));
    }

    @GetMapping("/blank")
    public Map<String, Object> blank() {
        return service(stamping -> Map.of("tags", StampingService.unstampedOut(stamping.session(), stamping.policy())));
    }

    @GetMapping("/keys/{tag}")
    public Map<String, Object> oneKey(@PathVariable String tag, @RequestParam Optional<LocalDate> on) {
        return service(stamping -> stampShape(stamping.stampKey(tag, on.orElseGet(LocalDate::now))));
    }

    @PostMapping("/runs")
    public Map<String, Object> run(@Valid @RequestBody Run body) {
        return service(stamping -> Map.of(
                "stamps",
                stamping.stampBatch(body.tags(), body.stampedOn(), body.stampedBy()).stream()
                        .map(StampingRoutes::stampShape)
                        .toList()));
    }

    @GetMapping("/sheet")
    public ResponseEntity<?> sheet(@RequestParam LocalDate on, @RequestParam(defaultValue = "false") boolean plain) {
        List<String> lines = service(stamping -> stamping.sheet(
                stamping.keys().out().stream().map(IssuedKeyRow::getTag).toList(), on));
        return Pages.page(lines, plain);
    }

    @PostMapping("/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> assign(@Valid @RequestBody Assignment body) {
        return service(stamping -> {
            BlindCodeRow row = stamping.assign(KeySymbol.parse(body.symbol()), body.assignedOn(), body.assignedBy());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", row.getSymbol());
            out.put("code", row.getCode());
            return out;
        });
    }

    @GetMapping("/codes/{symbol}")
    public Map<String, Object> readCode(@PathVariable String symbol) {
        return service(stamping -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", symbol);
            out.put("code", stamping.codeFor(KeySymbol.parse(symbol)));
            return out;
        });
    }

    @DeleteMapping("/codes/{symbol}")
    public Map<String, Object> retire(@PathVariable String symbol) {
        return service(stamping -> {
            BlindCodeRow row = stamping.retireCode(KeySymbol.parse(symbol));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("symbol", row.getSymbol());
            out.put("retired", true);
            return out;
        });
    }

    /** The whole table. One route, so one thing to put a rule in front of. */
    @GetMapping("/book")
    public Map<String, Object> book() {
        return service(stamping -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("warning", "this table turns every stamped number into a symbol");
            out.put(
                    "codes",
                    stamping.theWholeBook().stream()
                            .map(pair -> {
                                Map<String, Object> each = new LinkedHashMap<>();
                                each.put("symbol", pair.symbol());
                                each.put("code", pair.code());
                                return each;
                            })
                            .toList());
            return out;
        });
    }
}
