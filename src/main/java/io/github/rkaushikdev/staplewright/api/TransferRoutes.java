package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.control.Transfer;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import io.github.rkaushikdev.staplewright.services.TransferService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Handovers over http, and the two views of who has a key.
 *
 * <p>{@code /keys/{tag}/holder} is the endpoint everything else should be calling. The ledger view
 * is still there under the counter routes and it is still correct about who signed for the key,
 * but it is not the answer to the question people actually ask, which is who has it now.
 *
 * <p>The confirmation is a post to its own path and takes no body beyond a date, because there is
 * nothing to say. Either the person receiving the key signs or they do not, and a payload with a
 * state in it would let somebody confirm a handover into any state they liked.
 */
@RestController
@Tag(name = "handovers")
public class TransferRoutes {

    private final Work work;

    public TransferRoutes(Work work) {
        this.work = work;
    }

    /** Somebody saying they handed a key on. */
    public record NewClaim(
            @NotNull @Size(min = 1, max = 32) String tag,
            @NotNull @Size(min = 1, max = 32) String passedTo,
            @NotNull LocalDate claimedOn) {}

    /** The person receiving the key signing for it. */
    public record Confirmation(@NotNull LocalDate confirmedOn, @Size(max = 512) String note) {

        public Confirmation {
            note = note == null ? "" : note;
        }
    }

    /** The person named saying they never got it, and why. */
    public record Refusal(@NotNull LocalDate refusedOn, @NotNull @Size(min = 1, max = 512) String note) {}

    /** Whoever claimed the handover taking it back. */
    public record Cancellation(@NotNull LocalDate cancelledOn, @Size(max = 512) String note) {

        public Cancellation {
            note = note == null ? "" : note;
        }
    }

    static Map<String, Object> transferShape(TransferRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("tag", row.getTag());
        out.put("symbol", row.getSymbol());
        out.put("passed_by", row.getPassedBy());
        out.put("passed_to", row.getPassedTo());
        out.put("claimed_on", row.getClaimedOn().toString());
        out.put("state", row.getState());
        out.put("settled_on", row.getSettledOn().map(LocalDate::toString).orElse(null));
        out.put("note", row.getNote());
        return out;
    }

    private <T> T handovers(Function<TransferService, T> call) {
        return work.apply(session ->
                call.apply(new TransferService(session, work.settings().transfers())));
    }

    @PostMapping("/handovers")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> claim(@Valid @RequestBody NewClaim body) {
        return handovers(service -> transferShape(service.claim(body.tag(), body.passedTo(), body.claimedOn())));
    }

    @GetMapping("/handovers")
    public Map<String, Object> listing(
            @RequestParam(defaultValue = "claimed") @Pattern(regexp = "^(claimed|confirmed|refused|lapsed|cancelled)$")
                    String state) {
        return handovers(service -> Map.of(
                "handovers",
                service.transfers().inState(state).stream()
                        .map(TransferRoutes::transferShape)
                        .toList()));
    }

    @GetMapping("/handovers/late")
    public Map<String, Object> late(@RequestParam LocalDate on) {
        return handovers(service -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("on", on.toString());
            out.put("late", service.late(on).stream().map(Transfer::describe).toList());
            out.put("exposure", service.exposure(on));
            return out;
        });
    }

    @GetMapping("/handovers/strays")
    public Map<String, Object> strays(@RequestParam LocalDate on) {
        return handovers(service -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("on", on.toString());
            out.put(
                    "strays",
                    service.strays(on).stream()
                            .map(stray -> {
                                Map<String, Object> line = new LinkedHashMap<>();
                                line.put("tag", stray.tag());
                                line.put("ledger", stray.ledgerHolder());
                                line.put("actually", stray.holder());
                                return line;
                            })
                            .toList());
            return out;
        });
    }

    @GetMapping("/handovers/{number}")
    public Map<String, Object> read(@PathVariable String number) {
        return handovers(service -> transferShape(service.row(number)));
    }

    @PostMapping("/handovers/{number}/confirmation")
    public Map<String, Object> confirm(@PathVariable String number, @Valid @RequestBody Confirmation body) {
        return handovers(service -> transferShape(service.confirm(number, body.confirmedOn(), body.note())));
    }

    @PostMapping("/handovers/{number}/refusal")
    public Map<String, Object> refuse(@PathVariable String number, @Valid @RequestBody Refusal body) {
        return handovers(service -> transferShape(service.refuse(number, body.refusedOn(), body.note())));
    }

    @PostMapping("/handovers/{number}/cancellation")
    public Map<String, Object> cancel(@PathVariable String number, @Valid @RequestBody Cancellation body) {
        return handovers(service -> transferShape(service.cancel(number, body.cancelledOn(), body.note())));
    }

    @PostMapping("/handovers/lapse")
    public Map<String, Object> sweep(@RequestParam LocalDate on) {
        return handovers(service -> {
            List<TransferRow> found = service.lapseOverdue(on);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("lapsed", found.stream().map(TransferRow::getNumber).toList());
            out.put("count", found.size());
            return out;
        });
    }

    @GetMapping("/keys/{tag}/holder")
    public Map<String, Object> holder(@PathVariable String tag, @RequestParam Optional<LocalDate> on) {
        LocalDate today = on.orElseGet(LocalDate::now);
        return handovers(service -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("tag", tag);
            out.put("on", today.toString());
            out.put("holder", service.holderOf(tag, today));
            out.put(
                    "chain",
                    service.chainFor(tag).stream().map(Transfer::passedTo).toList());
            return out;
        });
    }

    @GetMapping("/keyholders/{identifier}/holdings")
    public Map<String, Object> holdings(@PathVariable String identifier, @RequestParam Optional<LocalDate> on) {
        LocalDate today = on.orElseGet(LocalDate::now);
        return handovers(service -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("keyholder", identifier);
            out.put("on", today.toString());
            out.put("tags", service.holdings(identifier, today));
            out.put(
                    "waiting_on",
                    service.waitingOn(identifier).stream()
                            .map(TransferRow::getNumber)
                            .toList());
            return out;
        });
    }
}
