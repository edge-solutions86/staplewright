package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.services.CounterService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The counter over http: request, decide, issue, take back.
 *
 * <p>The dates are on the requests rather than read off the clock inside the service. A shop that
 * is catching up on three weeks of paperwork needs to say what day something happened on, and a
 * service that quietly used today would put every one of those entries on the wrong date.
 */
@RestController
@Tag(name = "counter")
public class KeyRoutes {

    private final Work work;

    public KeyRoutes(Work work) {
        this.work = work;
    }

    /** Somebody asking for one key. */
    public record NewRequest(
            @NotNull @Size(min = 1, max = 32) String keyholder,
            @NotNull @Size(min = 1, max = 16) String symbol,
            @NotNull @Size(min = 1, max = 32) String building,
            @NotNull @Size(min = 1, max = 512) String reason,
            @NotNull LocalDate raisedOn,
            Boolean isMaster) {

        public NewRequest {
            isMaster = isMaster != null && isMaster;
        }
    }

    /** A signature on a request, one way or the other. */
    public record Decision(
            @NotNull @Size(min = 1, max = 32) String signer,
            @NotNull LocalDate decidedOn,
            @Size(max = 512) String note) {

        public Decision {
            note = note == null ? "" : note;
        }
    }

    /** A key going over the counter: the deposit is the standing rate unless one is named. */
    public record Issue(@NotNull LocalDate issuedOn, LocalDate dueOn, @Min(0) Integer depositCents) {}

    /** A key coming off the books, back or gone. */
    public record Close(@NotNull LocalDate closedOn, @Size(max = 512) String note) {

        public Close {
            note = note == null ? "" : note;
        }
    }

    static Map<String, Object> requestShape(KeyRequestRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("keyholder", row.getKeyholder());
        out.put("symbol", row.getSymbol());
        out.put("building", row.getBuilding());
        out.put("state", row.getState());
        out.put("is_master", row.isMaster());
        out.put("decided_by", row.getDecidedBy());
        return out;
    }

    static Map<String, Object> keyShape(IssuedKeyRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tag", row.getTag());
        out.put("symbol", row.getSymbol());
        out.put("keyholder", row.getKeyholder());
        out.put("state", row.getState());
        out.put("issued_on", row.getIssuedOn().toString());
        out.put("due_on", row.getDueOn().map(LocalDate::toString).orElse(null));
        out.put("deposit_cents", row.getDepositCents());
        return out;
    }

    private <T> T counter(Function<CounterService, T> call) {
        return work.apply(session ->
                call.apply(new CounterService(session, work.settings().deposits())));
    }

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> raiseRequest(@Valid @RequestBody NewRequest body) {
        return counter(service -> requestShape(service.raiseRequest(
                body.keyholder(), body.symbol(), body.building(), body.reason(), body.raisedOn(), body.isMaster())));
    }

    @GetMapping("/requests/{number}")
    public Map<String, Object> readRequest(@PathVariable String number) {
        return counter(service -> requestShape(service.requests().get(number)));
    }

    @PostMapping("/requests/{number}/approval")
    public Map<String, Object> approve(@PathVariable String number, @Valid @RequestBody Decision body) {
        return counter(service -> requestShape(service.approve(number, body.signer(), body.decidedOn(), body.note())));
    }

    @PostMapping("/requests/{number}/denial")
    public Map<String, Object> deny(@PathVariable String number, @Valid @RequestBody Decision body) {
        return counter(service -> requestShape(service.deny(number, body.signer(), body.decidedOn(), body.note())));
    }

    @PostMapping("/requests/{number}/key")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> issue(@PathVariable String number, @Valid @RequestBody Issue body) {
        return counter(service -> keyShape(service.issue(
                number, body.issuedOn(), Optional.ofNullable(body.dueOn()), Optional.ofNullable(body.depositCents()))));
    }

    @GetMapping("/keys/{tag}")
    public Map<String, Object> readKey(@PathVariable String tag) {
        return counter(service -> keyShape(service.keys().get(tag)));
    }

    @PostMapping("/keys/{tag}/return")
    public Map<String, Object> takeBack(@PathVariable String tag, @Valid @RequestBody Close body) {
        return counter(service -> keyShape(service.takeBack(tag, body.closedOn(), body.note())));
    }

    @PostMapping("/keys/{tag}/loss")
    public Map<String, Object> reportLost(@PathVariable String tag, @Valid @RequestBody Close body) {
        return counter(service -> keyShape(service.reportLost(tag, body.closedOn(), body.note())));
    }

    @GetMapping("/keyholders/{identifier}/keys")
    public Map<String, Object> holdings(@PathVariable String identifier) {
        return work.apply(session -> {
            DepositBook book = Loaders.loadDeposits(session);
            List<IssuedKey> keys = Loaders.loadLedger(session).heldBy(identifier);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("keyholder", identifier);
            out.put(
                    "keys",
                    keys.stream()
                            .map(key -> {
                                Map<String, Object> line = new LinkedHashMap<>();
                                line.put("tag", key.tag());
                                line.put("symbol", key.symbol());
                                line.put("deposit", book.heldOn(key.tag()));
                                return line;
                            })
                            .toList());
            out.put("deposits_held", book.balanceOf(identifier));
            return out;
        });
    }
}
