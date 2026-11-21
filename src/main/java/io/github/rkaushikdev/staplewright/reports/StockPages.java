package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stock reports, which is the paperwork a restricted keyway contract needs.
 *
 * <p>The factory that sells this shop restricted blanks does so on a signed undertaking to account
 * for every one of them. So the reconciliation is not a management report, it is a contractual
 * obligation, and it has to show the opening total, everything that moved, and the closing total in
 * a way that adds up on paper.
 *
 * <p>The shrinkage report is the one people argue about. A blank that cannot be accounted for is not
 * the same as a blank that was scrapped, and putting them in one column is how a shop convinces
 * itself its losses are fine.
 */
public final class StockPages {

    static final int PART_WIDTH = 12;
    static final int MOVEMENT_WIDTH = 10;

    /** The movements that put blanks into the drawer. */
    public static final Set<String> IN_MOVEMENTS = Set.of("received", "returned");

    /** The movements that take blanks out of it. */
    public static final Set<String> OUT_MOVEMENTS = Set.of("cut", "scrapped");

    private static final List<String> KINDS = List.of("received", "returned", "cut", "scrapped", "counted");

    private StockPages() {}

    /**
     * Opening total, the movements between two days, and the closing total, both days included.
     *
     * <p>The opening total is worked backwards from where the drawer is now, because the drawer is the
     * only thing anybody can point at. Working forwards from a total somebody wrote down years ago
     * compounds every mistake since.
     *
     * @throws IllegalArgumentException for a line whose movement is none of the five the drawer knows
     */
    public static Reconciliation reconcile(
            BlankRow blank, Collection<StockLineRow> lines, LocalDate since, LocalDate until) {
        List<StockLineRow> inside = lines.stream()
                .filter(line ->
                        !line.getMovedOn().isBefore(since) && !line.getMovedOn().isAfter(until))
                .toList();
        int after = lines.stream()
                .filter(line -> line.getMovedOn().isAfter(until))
                .mapToInt(StockLineRow::signed)
                .sum();
        int closing = blank.getStock() - after;
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (String kind : KINDS) {
            totals.put(kind, 0);
        }
        for (StockLineRow line : inside) {
            if (!totals.containsKey(line.getMovement())) {
                throw new IllegalArgumentException("no such movement: " + line.getMovement());
            }
            totals.merge(line.getMovement(), line.getQuantity(), Integer::sum);
        }
        int opening = closing - inside.stream().mapToInt(StockLineRow::signed).sum();
        return new Reconciliation(
                blank.getPartNumber(),
                opening,
                totals.get("received"),
                totals.get("returned"),
                totals.get("cut"),
                totals.get("scrapped"),
                totals.get("counted"),
                closing);
    }

    /** One part, as the columns the undertaking asks for. */
    public static List<String> reconciliationSheet(Reconciliation found) {
        return List.of(
                found.partNumber() + " from " + found.opening() + " to " + found.closing(),
                "  received   " + Text.right(found.received(), 6),
                "  returned   " + Text.right(found.returned(), 6),
                "  cut        " + Text.right(found.cut(), 6),
                "  scrapped   " + Text.right(found.scrapped(), 6),
                "  expected   " + Text.right(found.expected(), 6),
                "  " + found.describe());
    }

    /** Every line, oldest first, with what it did to the drawer, which is the back of the pack. */
    public static List<String> movementLog(Collection<StockLineRow> lines) {
        List<String> rows = new ArrayList<>();
        rows.add(Layout.columns(List.of("day", "movement", "quantity", "against"), 12, MOVEMENT_WIDTH, 10));
        lines.stream()
                .sorted(Comparator.comparing(StockLineRow::getMovedOn)
                        .thenComparingInt(line -> line.getId() == null ? 0 : line.getId()))
                .forEach(line -> rows.add(Layout.columns(
                        List.of(
                                line.getMovedOn().toString(),
                                line.getMovement(),
                                String.valueOf(line.signed()),
                                line.getReference().isEmpty() ? line.getNote() : line.getReference()),
                        12,
                        MOVEMENT_WIDTH,
                        10)));
        return List.copyOf(rows);
    }

    /** Parts that do not account for themselves, worst shortfall first. */
    public static List<String> shrinkage(Collection<Reconciliation> found) {
        List<Reconciliation> shortOnes = found.stream()
                .filter(item -> !item.agrees())
                .sorted(Comparator.comparingInt(Reconciliation::unaccounted).thenComparing(Reconciliation::partNumber))
                .toList();
        if (shortOnes.isEmpty()) {
            return List.of("every part accounts for itself");
        }
        List<String> lines = new ArrayList<>();
        lines.add(shortOnes.size() + " parts do not account for themselves");
        for (Reconciliation item : shortOnes) {
            lines.add("  " + item.describe());
        }
        return List.copyOf(lines);
    }

    /** What to put on the next order, shortest against its level first. */
    public static List<String> reorderSheet(Collection<BlankRow> blanks) {
        List<BlankRow> shortOnes = blanks.stream()
                .filter(row -> row.getStock() < row.getReorderAt())
                .sorted(Comparator.comparingInt((BlankRow row) -> row.getStock() - row.getReorderAt())
                        .thenComparing(BlankRow::getPartNumber))
                .toList();
        if (shortOnes.isEmpty()) {
            return List.of("nothing is below its reorder level");
        }
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("part", "held", "level", "order"), PART_WIDTH, 8, 8));
        for (BlankRow row : shortOnes) {
            int wanted = Math.max(row.getReorderAt() * 2 - row.getStock(), 1);
            lines.add(Layout.columns(
                    List.of(row.getPartNumber(), row.getStock(), row.getReorderAt(), wanted), PART_WIDTH, 8, 8));
        }
        return List.copyOf(lines);
    }

    /** What is still coming, oldest order first, with what is left on each of its lines. */
    public static List<String> outstandingOrders(Collection<PurchaseOrderRow> orders) {
        List<PurchaseOrderRow> openOrders = orders.stream()
                .filter(row -> row.getState().equals("placed") || row.getState().equals("part received"))
                .sorted(Comparator.comparing(PurchaseOrderRow::getPlacedOn).thenComparing(PurchaseOrderRow::getNumber))
                .toList();
        if (openOrders.isEmpty()) {
            return List.of("nothing is on order");
        }
        List<String> lines = new ArrayList<>();
        lines.add(openOrders.size() + " orders outstanding");
        for (PurchaseOrderRow order : openOrders) {
            lines.add(order.getNumber() + " " + order.getSupplier() + " placed " + order.getPlacedOn());
            order.getLines().stream()
                    .sorted(Comparator.comparing(PurchaseLineRow::getPartNumber))
                    .filter(line -> line.outstanding() != 0)
                    .forEach(line -> lines.add(
                            "  " + Text.left(line.getPartNumber(), PART_WIDTH) + line.outstanding() + " to come"));
        }
        return List.copyOf(lines);
    }

    /** How much restricted brass is in the building, which is the exposure, the biggest holding first. */
    public static List<String> restrictedPosition(Collection<BlankRow> blanks) {
        List<BlankRow> held = blanks.stream()
                .filter(BlankRow::isRestricted)
                .sorted(Comparator.comparingInt(BlankRow::getStock).reversed().thenComparing(BlankRow::getPartNumber))
                .toList();
        if (held.isEmpty()) {
            return List.of("no restricted blanks are stocked");
        }
        int total = held.stream().mapToInt(BlankRow::getStock).sum();
        List<String> lines = new ArrayList<>();
        lines.add(total + " restricted blanks across " + held.size() + " part numbers");
        for (BlankRow row : held) {
            lines.add("  " + Text.left(row.getPartNumber(), PART_WIDTH) + Text.right(row.getStock(), 6));
        }
        return List.copyOf(lines);
    }

    /** The whole pack, in the order somebody signs it. */
    public static List<String> stockReport(
            Collection<BlankRow> blanks,
            Collection<Reconciliation> found,
            Collection<PurchaseOrderRow> orders,
            LocalDate since,
            LocalDate until) {
        List<String> lines = new ArrayList<>();
        lines.add("blank stock " + since + " to " + until);
        lines.add("");
        lines.addAll(shrinkage(found));
        lines.add("");
        lines.addAll(restrictedPosition(blanks));
        lines.add("");
        lines.addAll(reorderSheet(blanks));
        lines.add("");
        lines.addAll(outstandingOrders(orders));
        return List.copyOf(lines);
    }
}
