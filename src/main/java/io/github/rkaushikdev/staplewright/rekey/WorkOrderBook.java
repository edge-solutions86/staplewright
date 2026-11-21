package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every rekey work order, open and closed, in the order they were raised.
 *
 * <p>An order closes only when every line has been dealt with, and a line can be dealt with by
 * being done or by being skipped with a reason. What is not allowed is closing an order with a line
 * still pending, because that is how a door stays on the old key for three years.
 */
public final class WorkOrderBook implements Iterable<WorkOrder> {

    private final Registry<WorkOrder> orders = new Registry<>("work order", "number");

    /**
     * File a new order.
     *
     * @throws ConflictError when it is not drafted or the number is taken
     */
    public WorkOrder raiseOrder(WorkOrder order) {
        if (order.state() != OrderState.DRAFTED) {
            throw new ConflictError("an order starts drafted", Facts.of("number", order.number()));
        }
        return orders.add(order.number(), order);
    }

    /**
     * The order with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is none
     */
    public WorkOrder order(String number) {
        return orders.get(number);
    }

    /**
     * Book a technician and a day. A scheduled order can be booked again until it starts.
     *
     * @throws ConflictError when the order has started or finished
     * @throws InputError when nobody is named
     */
    public WorkOrder schedule(String number, String technician, LocalDate when) {
        WorkOrder order = mustBe(number, List.of(OrderState.DRAFTED, OrderState.SCHEDULED));
        if (Text.isBlank(technician)) {
            throw new InputError("somebody has to do the work", Facts.of("number", number));
        }
        WorkOrder updated =
                order.withState(OrderState.SCHEDULED).withTechnician(technician).withScheduledFor(when);
        return orders.put(number, updated);
    }

    /**
     * Start a scheduled order.
     *
     * @throws ConflictError when it is not scheduled
     */
    public WorkOrder start(String number) {
        WorkOrder order = mustBe(number, List.of(OrderState.SCHEDULED));
        return orders.put(number, order.withState(OrderState.STARTED));
    }

    /** Move one cylinder along with nothing written on it. */
    public WorkOrder markLine(String number, String serial, LineState state) {
        return markLine(number, serial, state, "");
    }

    /**
     * Move one cylinder along. A skip has to say why; a blank note keeps whatever the line said.
     *
     * @throws ConflictError when the order is not under way, the cylinder is not on it, or the line
     *     is already in that state
     * @throws InputError for a skip with no reason
     */
    public WorkOrder markLine(String number, String serial, LineState state, String note) {
        WorkOrder order = mustBe(number, List.of(OrderState.SCHEDULED, OrderState.STARTED));
        OrderLine line = order.line(serial);
        if (state == LineState.SKIPPED && Text.isBlank(note)) {
            throw new InputError("a skipped cylinder says why", Facts.of("number", number, "serial", serial));
        }
        if (line.state() == state) {
            throw new ConflictError("that line is already there", Facts.of("number", number, "serial", serial));
        }
        List<OrderLine> updated = order.lines().stream()
                .map(item -> item.serial().equals(serial)
                        ? new OrderLine(item.serial(), item.opening(), state, note.isEmpty() ? item.note() : note)
                        : item)
                .toList();
        return orders.put(number, order.withLines(updated));
    }

    /**
     * Finish an order, refusing one with a cylinder still pending.
     *
     * @throws ConflictError when it has not started, or naming the pending serials
     */
    public WorkOrder close(String number, LocalDate when) {
        WorkOrder order = mustBe(number, List.of(OrderState.STARTED));
        List<OrderLine> pending = order.pending();
        if (!pending.isEmpty()) {
            throw new ConflictError(
                    "that order still has cylinders on it",
                    Facts.of(
                            "number",
                            number,
                            "pending",
                            pending.stream().map(OrderLine::serial).toList()));
        }
        return orders.put(number, order.withState(OrderState.DONE).withClosedOn(when));
    }

    /**
     * Call off an open order, keeping its lines.
     *
     * @throws ConflictError when it is already done or cancelled
     * @throws InputError when no reason is given
     */
    public WorkOrder cancel(String number, LocalDate when, String note) {
        WorkOrder order = orders.get(number);
        if (!order.isOpen()) {
            throw new ConflictError(
                    "that order is closed",
                    Facts.of("number", number, "state", order.state().value()));
        }
        if (Text.isBlank(note)) {
            throw new InputError("a cancelled order says why", Facts.of("number", number));
        }
        WorkOrder updated =
                order.withState(OrderState.CANCELLED).withClosedOn(when).withNote(note);
        return orders.put(number, updated);
    }

    private WorkOrder mustBe(String number, List<OrderState> states) {
        WorkOrder order = orders.get(number);
        Lifecycle.requireState(
                order.state(), states, "the order is not in a state for that", Facts.of("number", number));
        return order;
    }

    /** Every order still drafted, scheduled or started, in the order raised. */
    public List<WorkOrder> openOrders() {
        return orders.where(WorkOrder::isOpen);
    }

    /** Every order booked to one technician. */
    public List<WorkOrder> forTechnician(String technician) {
        return orders.where(order -> order.technician().equals(technician));
    }

    /** Every order raised for one incident. */
    public List<WorkOrder> forIncident(String incident) {
        return orders.where(order -> order.incident().equals(incident));
    }

    /** The open orders booked for one day. */
    public List<WorkOrder> dueOn(LocalDate when) {
        return orders.where(order -> order.isOpen() && order.scheduledFor().equals(Optional.of(when)));
    }

    /** Cylinders still pending per technician on open orders, busiest first and then by name. */
    public Map<String, Integer> workload() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (WorkOrder order : openOrders()) {
            if (order.technician().isEmpty()) {
                continue;
            }
            counts.merge(order.technician(), order.pending().size(), Integer::sum);
        }
        Map<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    /** Whether an order with this number has been raised. */
    public boolean contains(Object number) {
        return orders.contains(number);
    }

    /** Every order in the order raised. */
    @Override
    public Iterator<WorkOrder> iterator() {
        return orders.iterator();
    }

    /** How many orders the book holds. */
    public int size() {
        return orders.size();
    }
}
