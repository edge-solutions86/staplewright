package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import io.github.rkaushikdev.staplewright.services.EscrowService;
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
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sealed boxes over http, which is what the walk round app talks to.
 *
 * <p>Reading a seal is a post rather than a put, because reading a seal is an event and not a
 * state: two people walking the same corridor and reading the same tag leave two records of having
 * looked, not one record overwritten twice. Every other change has its own path, because the rules
 * differ and a body carrying the name of what it wants would have to branch on itself.
 */
@RestController
@RequestMapping("/boxes")
@Tag(name = "boxes")
public class BoxRoutes {

    private final Work work;

    public BoxRoutes(Work work) {
        this.work = work;
    }

    /** A box going up on a wall. */
    public record NewBox(
            @NotNull @Size(min = 1, max = 32) String number,
            @NotNull @Size(min = 1, max = 32) String building,
            @NotNull @Size(min = 1, max = 32) String opening,
            @NotNull @Size(min = 1, max = 16) String symbol,
            @NotNull @Size(min = 1, max = 32) String seal,
            @NotNull LocalDate sealedOn) {}

    /** A seal number read off a box on the walk round. */
    public record SealReading(@NotNull @Size(min = 1, max = 32) String seal, @NotNull LocalDate readOn) {}

    /** Somebody saying they opened a box. */
    public record Opening(@NotNull OpenedBy openedBy, @NotNull LocalDate openedOn, @Size(max = 512) String note) {
        public Opening {
            note = note == null ? "" : note;
        }
    }

    /** An open box found with nothing in it. */
    public record Emptied(@NotNull LocalDate foundOn, @Size(max = 512) String note) {
        public Emptied {
            note = note == null ? "" : note;
        }
    }

    /** A new key and a new seal. */
    public record Reseal(
            @NotNull @Size(min = 1, max = 32) String seal,
            @NotNull @Size(min = 1, max = 16) String symbol,
            @NotNull LocalDate sealedOn) {}

    /** A box coming off the wall, with the reason. */
    public record Withdrawal(@NotNull LocalDate takenOn, @NotNull @Size(min = 1, max = 512) String note) {}

    static Map<String, Object> rowShape(KeyBoxRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("building", row.getBuildingCode());
        out.put("opening", row.getOpeningNumber());
        out.put("symbol", row.getSymbol().render());
        out.put("seal", row.getSeal());
        out.put("state", row.getState());
        out.put("sealed_on", row.getSealedOn().toString());
        out.put("checked_on", row.getCheckedOn().map(LocalDate::toString).orElse(null));
        out.put("opened_on", row.getOpenedOn().map(LocalDate::toString).orElse(null));
        out.put("opened_by", row.getOpenedBy().isEmpty() ? null : row.getOpenedBy());
        out.put("note", row.getNote());
        return out;
    }

    static Map<String, Object> boxShape(KeyBox box) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", box.number());
        out.put("building", box.building());
        out.put("opening", box.opening());
        out.put("symbol", box.symbol().render());
        out.put("state", box.state().value());
        out.put("last_seen", box.lastSeen().toString());
        return out;
    }

    static Map<String, Object> sealShape(SealRow row, LocalDate today) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("seal", row.getSeal());
        out.put("symbol", row.getSymbol().render());
        out.put("sealed_on", row.getSealedOn().toString());
        out.put("broken_on", row.getBrokenOn().map(LocalDate::toString).orElse(null));
        out.put("broken_by", row.getBrokenBy().isEmpty() ? null : row.getBrokenBy());
        out.put("days_held", row.daysHeld(today));
        out.put("current", row.isCurrent());
        return out;
    }

    private <T> T service(Function<EscrowService, T> call) {
        return work.apply(
                session -> call.apply(new EscrowService(session, work.settings().boxes())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> install(@Valid @RequestBody NewBox body) {
        return service(boxes -> rowShape(boxes.install(
                body.number(),
                body.building(),
                body.opening(),
                KeySymbol.parse(body.symbol()),
                body.seal(),
                body.sealedOn())));
    }

    @GetMapping
    public Map<String, Object> listing(@RequestParam(defaultValue = "") String building) {
        return service(boxes -> {
            List<KeyBoxRow> rows =
                    building.isEmpty() ? boxes.boxes().all() : boxes.boxes().inBuilding(building);
            return Map.of("boxes", rows.stream().map(BoxRoutes::rowShape).toList());
        });
    }

    @GetMapping("/open")
    public Map<String, Object> openBoxes() {
        return service(boxes -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("open", boxes.openBoxes().stream().map(BoxRoutes::boxShape).toList());
            out.put("exposure", boxes.exposure());
            return out;
        });
    }

    @GetMapping("/uncovered")
    public Map<String, Object> uncovered() {
        return service(boxes -> Map.of("buildings", boxes.uncovered()));
    }

    @GetMapping("/walk-round")
    public Map<String, Object> walkRound(@RequestParam LocalDate on) {
        return service(boxes -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("on", on.toString());
            out.put(
                    "boxes",
                    boxes.walkRound(on).stream().map(BoxRoutes::boxShape).toList());
            out.put(
                    "overdue_reseal",
                    boxes.overdueReseal(on).stream().map(KeyBox::number).toList());
            return out;
        });
    }

    @GetMapping("/{number}")
    public Map<String, Object> read(@PathVariable String number) {
        return service(boxes -> rowShape(boxes.row(number)));
    }

    @GetMapping("/{number}/seals")
    public Map<String, Object> seals(@PathVariable String number, @RequestParam Optional<LocalDate> on) {
        LocalDate today = on.orElseGet(LocalDate::now);
        return service(boxes -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", number);
            out.put(
                    "seals",
                    boxes.history(number).stream()
                            .map(row -> sealShape(row, today))
                            .toList());
            return out;
        });
    }

    @GetMapping("/{number}/key-on/{when}")
    public Map<String, Object> keyOn(
            @PathVariable String number, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate when) {
        return service(boxes -> {
            Optional<KeySymbol> found = boxes.keyOn(number, when);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("number", number);
            out.put("on", when.toString());
            out.put("symbol", found.map(KeySymbol::render).orElse(null));
            return out;
        });
    }

    @PostMapping("/{number}/checks")
    public Map<String, Object> check(@PathVariable String number, @Valid @RequestBody SealReading body) {
        return service(boxes -> rowShape(boxes.check(number, body.seal(), body.readOn())));
    }

    @PostMapping("/{number}/openings")
    public Map<String, Object> opened(@PathVariable String number, @Valid @RequestBody Opening body) {
        return service(boxes -> rowShape(boxes.reportOpened(number, body.openedBy(), body.openedOn(), body.note())));
    }

    @PostMapping("/{number}/emptied")
    public Map<String, Object> emptied(@PathVariable String number, @Valid @RequestBody Emptied body) {
        return service(boxes -> rowShape(boxes.empty(number, body.foundOn(), body.note())));
    }

    @PostMapping("/{number}/reseal")
    public Map<String, Object> reseal(@PathVariable String number, @Valid @RequestBody Reseal body) {
        return service(
                boxes -> rowShape(boxes.reseal(number, body.seal(), KeySymbol.parse(body.symbol()), body.sealedOn())));
    }

    @PostMapping("/{number}/withdrawal")
    public Map<String, Object> withdraw(@PathVariable String number, @Valid @RequestBody Withdrawal body) {
        return service(boxes -> rowShape(boxes.withdraw(number, body.takenOn(), body.note())));
    }
}
