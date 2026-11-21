package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.persistence.models.AnnualReturnRow;
import io.github.rkaushikdev.staplewright.reports.AnnualReturn;
import io.github.rkaushikdev.staplewright.services.AnnualService;
import io.github.rkaushikdev.staplewright.util.Numbers;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The annual return over http, for whatever risk management is pasting it into.
 *
 * <p>Everything here is a read except the filing, and the filing is a post that takes the day
 * rather than the numbers. A caller that could send the numbers could file whatever it liked, and
 * the point of the return is that the numbers came off the ledger on a day somebody can name.
 */
@RestController
@RequestMapping("/returns")
@Tag(name = "returns")
public class AnnualRoutes {

    private final Work work;

    public AnnualRoutes(Work work) {
        this.work = work;
    }

    /** The day a return is measured on, and who signs it off. */
    public record Filing(@NotNull LocalDate measuredOn, @Size(max = 128) String filedBy, @Size(max = 512) String note) {

        public Filing {
            filedBy = filedBy == null ? "" : filedBy;
            note = note == null ? "" : note;
        }
    }

    /** Why a filed year is not to be used. */
    public record Withdrawal(@NotNull @Size(min = 1, max = 512) String note) {}

    static Map<String, Object> returnShape(AnnualReturnRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("year", row.getYear());
        out.put("measured_on", row.getMeasuredOn().toString());
        out.put("keys_out", row.getKeysOut());
        out.put("masters_out", row.getMastersOut());
        out.put("unaccounted", row.getUnaccounted());
        out.put("keyholders", row.getKeyholders());
        out.put("keys_per_holder", Numbers.round(row.keysPerHolder(), 2));
        out.put("filed_by", row.getFiledBy().isEmpty() ? null : row.getFiledBy());
        out.put("note", row.getNote());
        return out;
    }

    private <T> T annual(Function<AnnualService, T> call) {
        return work.apply(session -> call.apply(new AnnualService(session)));
    }

    @GetMapping
    public Map<String, Object> listing() {
        return annual(service -> Map.of(
                "returns",
                service.returns().all().stream().map(AnnualRoutes::returnShape).toList()));
    }

    @GetMapping("/measure")
    public Map<String, Object> measure(@RequestParam LocalDate on) {
        return annual(service -> {
            AnnualReturn found = service.measure(on);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("year", found.year());
            out.put("keys_out", found.keysOut());
            out.put("masters_out", found.mastersOut());
            out.put("unaccounted", found.unaccounted());
            out.put("keyholders", found.keyholders());
            out.put("keys_per_holder", Numbers.round(found.keysPerHolder(), 2));
            return out;
        });
    }

    @GetMapping("/sheet")
    public ResponseEntity<?> sheet(@RequestParam LocalDate on, @RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(annual(service -> service.sheet(on)), plain);
    }

    @GetMapping("/trend")
    public ResponseEntity<?> trend(@RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(annual(AnnualService::trend), plain);
    }

    @GetMapping("/masters")
    public ResponseEntity<?> masters(@RequestParam(defaultValue = "false") boolean plain) {
        return Pages.page(annual(AnnualService::masters), plain);
    }

    @GetMapping("/concentration")
    public Map<String, Object> concentration(@RequestParam(defaultValue = "5") @Min(1) @Max(50) int top) {
        return annual(service -> Map.of(
                "holders",
                service.concentration(top).stream()
                        .map(tally -> {
                            Map<String, Object> line = new LinkedHashMap<>();
                            line.put("keyholder", tally.name());
                            line.put("keys", tally.count());
                            return line;
                        })
                        .toList()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> fileIt(@Valid @RequestBody Filing body) {
        return annual(service -> returnShape(service.fileReturn(body.measuredOn(), body.filedBy(), body.note())));
    }

    @GetMapping("/{year}")
    public Map<String, Object> read(@PathVariable int year) {
        return annual(service -> returnShape(service.filed(year)));
    }

    @PostMapping("/{year}/withdrawal")
    public Map<String, Object> withdraw(@PathVariable int year, @Valid @RequestBody Withdrawal body) {
        return annual(service -> returnShape(service.withdraw(year, body.note())));
    }
}
