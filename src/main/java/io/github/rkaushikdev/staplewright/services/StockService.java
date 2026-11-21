package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Blank stock as the shop moves it, with the total and the line together.
 *
 * <p>The rule this exists to hold is that a blank's stock count is never written by anybody except
 * this service, and never without a line to explain it. A total somebody adjusted by hand is a total
 * nobody can reconcile, and the whole reason the ledger went in was that the drawer and the column
 * had been disagreeing for a year with no way to find out where.
 *
 * <p>Restricted blanks get the extra rule: a cut against a restricted part needs a reference,
 * because that is the number somebody follows back when a key turns up that should not exist.
 * Unrestricted brass is two dollars and nobody cares.
 *
 * <p>A count is the interesting movement. It does not say how many moved, it says what was in the
 * drawer, and the service works out the difference and writes that as the line. The difference is
 * the finding.
 */
public final class StockService {

    /** The movements that take stock out of the drawer rather than put it in. */
    public static final Set<String> TAKES_STOCK_OUT = Set.of("cut", "scrapped");

    /**
     * One part number wanted on a purchase order.
     *
     * @param partNumber the part
     * @param quantity how many are ordered
     */
    public record Wanted(String partNumber, int quantity) {}

    /**
     * A part under its reorder level.
     *
     * @param partNumber the part
     * @param stock what the drawer holds
     * @param reorderAt the level the shop wants to hear about it at
     */
    public record Shortfall(String partNumber, int stock, int reorderAt) {}

    private final Session session;
    private final BlankRepository blanks;
    private final StockLineRepository lines;
    private final OrderRepository orders;

    public StockService(Session session) {
        this.session = session;
        this.blanks = new BlankRepository(session);
        this.lines = new StockLineRepository(session);
        this.orders = new OrderRepository(session);
    }

    public Session session() {
        return session;
    }

    public BlankRepository blanks() {
        return blanks;
    }

    public StockLineRepository lines() {
        return lines;
    }

    public OrderRepository orders() {
        return orders;
    }

    /** The blank's row, refusing a part nobody stocks. */
    public BlankRow blank(String partNumber) {
        return blanks.require(partNumber, "no such blank", Facts.of("part", partNumber));
    }

    /** What the drawer holds, by the blank's own count. */
    public int onHand(String partNumber) {
        return blank(partNumber).getStock();
    }

    /**
     * The total worked out from the lines, which should match the column.
     *
     * <p>Kept separate from {@link #onHand} on purpose. Two ways of arriving at the same number is the
     * only way to notice when one of them is wrong. A count counts here as its adjustment, so a drawer
     * that was found short leaves the two agreeing rather than papering over the gap.
     */
    public int ledgerTotal(String partNumber) {
        blank(partNumber);
        return lines.forPart(partNumber).stream().mapToInt(StockLineRow::signed).sum();
    }

    public StockLineRow receive(String partNumber, int quantity, LocalDate when) {
        return receive(partNumber, quantity, when, "");
    }

    /** Brass arrives, usually against a purchase order. */
    public StockLineRow receive(String partNumber, int quantity, LocalDate when, String reference) {
        return move(partNumber, "received", quantity, when, reference, "");
    }

    public StockLineRow cut(String partNumber, int quantity, LocalDate when) {
        return cut(partNumber, quantity, when, "");
    }

    /** Brass leaves the drawer to be cut. A restricted part needs a reference. */
    public StockLineRow cut(String partNumber, int quantity, LocalDate when, String reference) {
        BlankRow blank = blank(partNumber);
        if (blank.isRestricted() && Text.isBlank(reference)) {
            throw new ConflictError(
                    "a restricted blank does not leave the drawer without a reference", Facts.of("part", partNumber));
        }
        return move(partNumber, "cut", quantity, when, reference, "");
    }

    /** Brass that went in the bin. The reason is not optional. */
    public StockLineRow scrap(String partNumber, int quantity, LocalDate when, String note) {
        if (Text.isBlank(note)) {
            throw new InputError("a scrapping says why", Facts.of("part", partNumber));
        }
        return move(partNumber, "scrapped", quantity, when, "", note);
    }

    public StockLineRow putBack(String partNumber, int quantity, LocalDate when) {
        return putBack(partNumber, quantity, when, "");
    }

    /** A blank came back uncut, which happens more than anybody admits. */
    public StockLineRow putBack(String partNumber, int quantity, LocalDate when, String reference) {
        return move(partNumber, "returned", quantity, when, reference, "");
    }

    public CountResult count(String partNumber, int counted, LocalDate when) {
        return count(partNumber, counted, when, "");
    }

    /**
     * Write down what was in the drawer and move the total to match. With no note, the line carries
     * what the count found.
     */
    public CountResult count(String partNumber, int counted, LocalDate when, String note) {
        if (counted < 0) {
            throw new InputError("a drawer cannot hold less than nothing", Facts.of("part", partNumber));
        }
        BlankRow blank = blank(partNumber);
        CountResult result = new CountResult(partNumber, blank.getStock(), counted);
        StockLineRow line = new StockLineRow(partNumber, "counted", counted, when);
        line.setAdjustment(result.difference());
        line.setNote(note.isEmpty() ? result.describe() : note);
        session.add(line);
        blank.setStock(counted);
        session.flush();
        return result;
    }

    private StockLineRow move(
            String partNumber, String movement, int quantity, LocalDate when, String reference, String note) {
        if (quantity < 1) {
            throw new InputError("a movement moves at least one", Facts.of("part", partNumber));
        }
        BlankRow blank = blank(partNumber);
        int change = TAKES_STOCK_OUT.contains(movement) ? -quantity : quantity;
        if (blank.getStock() + change < 0) {
            throw new CapacityError(
                    "the drawer does not hold that many",
                    Facts.of("part", partNumber, "held", blank.getStock(), "wanted", quantity));
        }
        StockLineRow line = new StockLineRow(partNumber, movement, quantity, when);
        line.setReference(reference);
        line.setNote(note);
        session.add(line);
        blank.setStock(blank.getStock() + change);
        session.flush();
        return line;
    }

    /** An order, with a line per part number, refusing an empty one. */
    public PurchaseOrderRow placeOrder(String number, String supplier, LocalDate when, List<Wanted> wanted) {
        if (wanted.isEmpty()) {
            throw new InputError("an order has something on it", Facts.of("order", number));
        }
        if (orders.find(number).isPresent()) {
            throw new ConflictError("that order number is already used", Facts.of("order", number));
        }
        PurchaseOrderRow order = new PurchaseOrderRow(number, supplier, when);
        session.add(order);
        session.flush();
        for (Wanted each : wanted) {
            blank(each.partNumber());
            session.add(PurchaseLineRow.of(order, each.partNumber(), each.quantity()));
        }
        session.flush();
        return order;
    }

    /** The order's row, refusing a number nobody placed. */
    public PurchaseOrderRow order(String number) {
        return orders.require(number, "no such purchase order", Facts.of("order", number));
    }

    /** Brass arrives against an order, which moves stock and the line. */
    public PurchaseLineRow bookIn(String number, String partNumber, int quantity, LocalDate when) {
        PurchaseOrderRow order = order(number);
        if (order.getState().equals("cancelled")) {
            throw new ConflictError("that order was cancelled", Facts.of("order", number));
        }
        PurchaseLineRow line = orderLine(order, partNumber);
        if (quantity > line.outstanding()) {
            throw new CapacityError(
                    "more arrived than was ordered",
                    Facts.of("order", number, "part", partNumber, "outstanding", line.outstanding()));
        }
        receive(partNumber, quantity, when, number);
        line.setReceived(line.getReceived() + quantity);
        order.setState(isComplete(order) ? "received" : "part received");
        session.flush();
        return line;
    }

    /** Stop expecting an order, which is not the same as it never existing. */
    public PurchaseOrderRow cancelOrder(String number, String note) {
        PurchaseOrderRow order = order(number);
        if (order.getState().equals("received")) {
            throw new ConflictError("that order already arrived", Facts.of("order", number));
        }
        order.setState("cancelled");
        order.setNote(note);
        session.flush();
        return order;
    }

    private static PurchaseLineRow orderLine(PurchaseOrderRow order, String partNumber) {
        return order.getLines().stream()
                .filter(line -> line.getPartNumber().equals(partNumber))
                .findFirst()
                .orElseThrow(() -> new NotFoundError(
                        "that part is not on that order", Facts.of("order", order.getNumber(), "part", partNumber)));
    }

    private static boolean isComplete(PurchaseOrderRow order) {
        return order.getLines().stream().allMatch(line -> line.outstanding() == 0);
    }

    /** Parts under their reorder level, shortest first. */
    public List<Shortfall> belowReorder() {
        return blanks.belowReorder().stream()
                .map(row -> new Shortfall(row.getPartNumber(), row.getStock(), row.getReorderAt()))
                .sorted(Comparator.comparingInt((Shortfall item) -> item.stock() - item.reorderAt())
                        .thenComparing(Shortfall::partNumber))
                .toList();
    }

    /** Every movement of one part, oldest first, refusing a part nobody stocks. */
    public List<StockLineRow> movements(String partNumber) {
        blank(partNumber);
        return List.copyOf(lines.forPart(partNumber));
    }

    /** Every blank that left the drawer against one number. */
    public List<StockLineRow> cutAgainst(String reference) {
        return lines.against(reference).stream()
                .filter(row -> row.getMovement().equals("cut"))
                .toList();
    }

    /** How much of each part was cut since a day, most first. */
    public Map<String, Integer> totalsSince(LocalDate when) {
        List<Object[]> found = session.list(
                "select l.partNumber, sum(l.quantity) from StockLineRow l"
                        + " where l.movedOn >= ?1 and l.movement = 'cut' group by l.partNumber",
                Object[].class,
                when);
        Map<String, Integer> totals = new LinkedHashMap<>();
        found.stream()
                .map(pair -> Map.entry((String) pair[0], ((Number) pair[1]).intValue()))
                .sorted(Comparator.comparing((Map.Entry<String, Integer> pair) -> -pair.getValue())
                        .thenComparing(Map.Entry::getKey))
                .forEach(pair -> totals.put(pair.getKey(), pair.getValue()));
        return Collections.unmodifiableMap(totals);
    }
}
