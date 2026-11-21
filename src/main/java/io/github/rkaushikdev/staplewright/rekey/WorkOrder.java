package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One trip out with a bag of pins: a rekey decision turned into a day of work.
 *
 * <p>An order is a header and a line per cylinder. The header carries who is doing it and when.
 * The lines carry what actually happened at each door, because a rekey is never finished in one
 * visit: two rooms are occupied, one cylinder turns out to be a different format than the survey
 * said, and one door has a lock nobody has a key for at all.
 *
 * @param number the order number
 * @param incident the incident the work answers
 * @param lines a line a cylinder, each cylinder once
 * @param state where the order is
 * @param technician who is doing it, once it is scheduled
 * @param scheduledFor the day it is booked for
 * @param closedOn the day it was finished or cancelled
 * @param note why it was cancelled
 */
public record WorkOrder(
        String number,
        String incident,
        List<OrderLine> lines,
        OrderState state,
        String technician,
        Optional<LocalDate> scheduledFor,
        Optional<LocalDate> closedOn,
        String note) {

    private static final Set<OrderState> OPEN = Set.of(OrderState.DRAFTED, OrderState.SCHEDULED, OrderState.STARTED);

    /**
     * An order, refusing one with no number, no cylinders, or a cylinder on it twice.
     *
     * @throws InputError for a blank number
     * @throws ConflictError for no lines or a repeated cylinder
     */
    public WorkOrder {
        if (Text.isBlank(number)) {
            throw new InputError("a work order needs a number");
        }
        lines = List.copyOf(lines);
        if (lines.isEmpty()) {
            throw new ConflictError("a work order with no cylinders is not work", Facts.of("number", number));
        }
        Set<String> serials = new HashSet<>();
        for (OrderLine line : lines) {
            if (!serials.add(line.serial())) {
                throw new ConflictError("a cylinder is on the order twice", Facts.of("number", number));
            }
        }
    }

    /** A drafted order nobody has been given yet. */
    public WorkOrder(String number, String incident, List<OrderLine> lines) {
        this(number, incident, lines, OrderState.DRAFTED, "", Optional.empty(), Optional.empty(), "");
    }

    /**
     * The line for one cylinder.
     *
     * @throws ConflictError when the cylinder is not on this order
     */
    public OrderLine line(String serial) {
        return lines.stream()
                .filter(line -> line.serial().equals(serial))
                .findFirst()
                .orElseThrow(() -> new ConflictError(
                        "that cylinder is not on this order", Facts.of("number", number, "serial", serial)));
    }

    /** The lines not yet dealt with, in order. */
    public List<OrderLine> pending() {
        return lines.stream().filter(line -> !line.isSettled()).toList();
    }

    /** Whether the order is still drafted, scheduled or started. */
    public boolean isOpen() {
        return OPEN.contains(state);
    }

    /** The doors the order visits, each once, in number order. */
    public List<String> openings() {
        return lines.stream().map(OrderLine::opening).distinct().sorted().toList();
    }

    /** One line for the board: {@code W-1041 drafted: 2 cylinders, 2 still pending}. */
    public String summary() {
        return number + " " + state.value() + ": " + lines.size() + " cylinders, "
                + pending().size() + " still pending";
    }

    public WorkOrder withNumber(String value) {
        return new WorkOrder(value, incident, lines, state, technician, scheduledFor, closedOn, note);
    }

    public WorkOrder withIncident(String value) {
        return new WorkOrder(number, value, lines, state, technician, scheduledFor, closedOn, note);
    }

    public WorkOrder withLines(List<OrderLine> value) {
        return new WorkOrder(number, incident, value, state, technician, scheduledFor, closedOn, note);
    }

    public WorkOrder withState(OrderState value) {
        return new WorkOrder(number, incident, lines, value, technician, scheduledFor, closedOn, note);
    }

    public WorkOrder withTechnician(String value) {
        return new WorkOrder(number, incident, lines, state, value, scheduledFor, closedOn, note);
    }

    public WorkOrder withScheduledFor(LocalDate value) {
        return new WorkOrder(number, incident, lines, state, technician, Optional.of(value), closedOn, note);
    }

    public WorkOrder withClosedOn(LocalDate value) {
        return new WorkOrder(number, incident, lines, state, technician, scheduledFor, Optional.of(value), note);
    }

    public WorkOrder withNote(String value) {
        return new WorkOrder(number, incident, lines, state, technician, scheduledFor, closedOn, value);
    }
}
