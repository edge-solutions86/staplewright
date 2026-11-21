package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.services.CountResult;
import io.github.rkaushikdev.staplewright.services.StockService;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
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
 * Blank stock over http, for the tablet in the store room.
 *
 * <p>Every movement is a post to its own path rather than a patch that carries a movement name,
 * because the rules differ per movement and a single endpoint would have to branch on the body to
 * decide which of them to apply. A cut needs a reference on a restricted part, a scrapping needs a
 * reason, and a count needs neither and means something else entirely.
 *
 * <p>The count endpoint gives back what it found against what the ledger thought, which is the one
 * number anybody wants from a stocktake.
 */
@RestController
@RequestMapping("/stock")
@Tag(name = "stock")
public class StockRoutes {

    private final Work work;

    public StockRoutes(Work work) {
        this.work = work;
    }

    /** Blanks going in or out of the drawer, against an order, a tag or a job if there is one. */
    public record Movement(
            @NotNull @Min(1) @Max(100000) Integer quantity,
            @NotNull LocalDate movedOn,
            @Size(max = 32) String reference) {

        public Movement {
            reference = reference == null ? "" : reference;
        }
    }

    /** Blanks going in the bin, with the reason. */
    public record Scrapping(
            @NotNull @Min(1) @Max(100000) Integer quantity,
            @NotNull LocalDate movedOn,
            @NotNull @Size(min = 1, max = 512) String note) {}

    /** What was found in the drawer on a stocktake. */
    public record Count(
            @NotNull @Min(0) @Max(1000000) Integer counted, @NotNull LocalDate movedOn, @Size(max = 512) String note) {

        public Count {
            note = note == null ? "" : note;
        }
    }

    /** One part number on a new order. */
    public record OrderLine(
            @NotNull @Size(min = 1, max = 32) String partNumber, @NotNull @Min(1) @Max(100000) Integer quantity) {}

    /** An order going to a supplier, with something on it. */
    public record NewOrder(
            @NotNull @Size(min = 1, max = 32) String number,
            @NotNull @Size(min = 1, max = 128) String supplier,
            @NotNull LocalDate placedOn,
            @NotNull @Size(min = 1) List<@Valid @NotNull OrderLine> lines) {}

    /** Brass arriving against an order. */
    public record Delivery(
            @NotNull @Size(min = 1, max = 32) String partNumber,
            @NotNull @Min(1) @Max(100000) Integer quantity,
            @NotNull LocalDate arrivedOn) {}

    /** Why an order is no longer expected. */
    public record Cancellation(@NotNull @Size(min = 1, max = 512) String note) {}

    static Map<String, Object> blankShape(BlankRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("part_number", row.getPartNumber());
        out.put("manufacturer", row.getManufacturer());
        out.put("chambers", row.getChambers());
        out.put("restricted", row.isRestricted());
        out.put("stock", row.getStock());
        out.put("reorder_at", row.getReorderAt());
        return out;
    }

    static Map<String, Object> lineShape(StockLineRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.getId());
        out.put("part_number", row.getPartNumber());
        out.put("movement", row.getMovement());
        out.put("quantity", row.getQuantity());
        out.put("signed", row.signed());
        out.put("moved_on", row.getMovedOn().toString());
        out.put("reference", row.getReference());
        out.put("note", row.getNote());
        return out;
    }

    static Map<String, Object> orderShape(PurchaseOrderRow row) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("number", row.getNumber());
        out.put("supplier", row.getSupplier());
        out.put("placed_on", row.getPlacedOn().toString());
        out.put("state", row.getState());
        out.put(
                "lines",
                row.getLines().stream().map(StockRoutes::orderLineShape).toList());
        return out;
    }

    private static Map<String, Object> orderLineShape(PurchaseLineRow line) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("part_number", line.getPartNumber());
        out.put("ordered", line.getOrdered());
        out.put("received", line.getReceived());
        out.put("outstanding", line.outstanding());
        return out;
    }

    private <T> T service(Function<StockService, T> call) {
        return work.apply(session -> call.apply(new StockService(session)));
    }

    @GetMapping("/blanks")
    public Map<String, Object> blanks(
            @Parameter(description = "only the restricted parts") @RequestParam(defaultValue = "false")
                    boolean restricted) {
        return service(stock -> {
            List<BlankRow> found =
                    restricted ? stock.blanks().restricted() : stock.blanks().all();
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("blanks", found.stream().map(StockRoutes::blankShape).toList());
            return out;
        });
    }

    @GetMapping("/blanks/{partNumber}")
    public Map<String, Object> blank(@PathVariable String partNumber) {
        return service(stock -> {
            Map<String, Object> out = blankShape(stock.blank(partNumber));
            out.put("ledger_total", stock.ledgerTotal(partNumber));
            return out;
        });
    }

    @GetMapping("/blanks/{partNumber}/movements")
    public Map<String, Object> movements(@PathVariable String partNumber) {
        return service(stock -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("part_number", partNumber);
            out.put(
                    "movements",
                    stock.movements(partNumber).stream()
                            .map(StockRoutes::lineShape)
                            .toList());
            return out;
        });
    }

    @PostMapping("/blanks/{partNumber}/receipts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> receive(@PathVariable String partNumber, @Valid @RequestBody Movement body) {
        return service(
                stock -> lineShape(stock.receive(partNumber, body.quantity(), body.movedOn(), body.reference())));
    }

    @PostMapping("/blanks/{partNumber}/cuts")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> cut(@PathVariable String partNumber, @Valid @RequestBody Movement body) {
        return service(stock -> lineShape(stock.cut(partNumber, body.quantity(), body.movedOn(), body.reference())));
    }

    @PostMapping("/blanks/{partNumber}/scrappings")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> scrap(@PathVariable String partNumber, @Valid @RequestBody Scrapping body) {
        return service(stock -> lineShape(stock.scrap(partNumber, body.quantity(), body.movedOn(), body.note())));
    }

    @PostMapping("/blanks/{partNumber}/returns")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> putBack(@PathVariable String partNumber, @Valid @RequestBody Movement body) {
        return service(
                stock -> lineShape(stock.putBack(partNumber, body.quantity(), body.movedOn(), body.reference())));
    }

    @PostMapping("/blanks/{partNumber}/count")
    public Map<String, Object> count(@PathVariable String partNumber, @Valid @RequestBody Count body) {
        return service(stock -> {
            CountResult found = stock.count(partNumber, body.counted(), body.movedOn(), body.note());
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("part_number", found.partNumber());
            out.put("expected", found.expected());
            out.put("counted", found.counted());
            out.put("difference", found.difference());
            out.put("agrees", found.agrees());
            out.put("note", found.describe());
            return out;
        });
    }

    @GetMapping("/reorder")
    public Map<String, Object> reorder() {
        return service(stock -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(
                    "below",
                    stock.belowReorder().stream()
                            .map(item -> {
                                Map<String, Object> row = new LinkedHashMap<>();
                                row.put("part_number", item.partNumber());
                                row.put("stock", item.stock());
                                row.put("reorder_at", item.reorderAt());
                                return row;
                            })
                            .toList());
            return out;
        });
    }

    @GetMapping("/cut-against/{reference}")
    public Map<String, Object> against(@PathVariable String reference) {
        return service(stock -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("reference", reference);
            out.put(
                    "cuts",
                    stock.cutAgainst(reference).stream()
                            .map(StockRoutes::lineShape)
                            .toList());
            return out;
        });
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> place(@Valid @RequestBody NewOrder body) {
        List<StockService.Wanted> wanted = body.lines().stream()
                .map(line -> new StockService.Wanted(line.partNumber(), line.quantity()))
                .toList();
        return service(stock -> orderShape(stock.placeOrder(body.number(), body.supplier(), body.placedOn(), wanted)));
    }

    @GetMapping("/orders")
    public Map<String, Object> outstanding() {
        return service(stock -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put(
                    "orders",
                    stock.orders().outstanding().stream()
                            .map(StockRoutes::orderShape)
                            .toList());
            return out;
        });
    }

    @GetMapping("/orders/{number}")
    public Map<String, Object> readOrder(@PathVariable String number) {
        return service(stock -> orderShape(stock.order(number)));
    }

    @PostMapping("/orders/{number}/deliveries")
    public Map<String, Object> bookIn(@PathVariable String number, @Valid @RequestBody Delivery body) {
        return service(stock -> {
            stock.bookIn(number, body.partNumber(), body.quantity(), body.arrivedOn());
            return orderShape(stock.order(number));
        });
    }

    @PostMapping("/orders/{number}/cancellation")
    public Map<String, Object> cancel(@PathVariable String number, @Valid @RequestBody Cancellation body) {
        return service(stock -> orderShape(stock.cancelOrder(number, body.note())));
    }
}
