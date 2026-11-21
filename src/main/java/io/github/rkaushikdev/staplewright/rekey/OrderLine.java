package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.ArrayList;
import java.util.List;

/**
 * One cylinder on one work order, and what happened to it at the door.
 *
 * @param serial the cylinder
 * @param opening the door it sits in
 * @param state how far it has got
 * @param note what the technician wrote, which a skip always has
 */
public record OrderLine(String serial, String opening, LineState state, String note) {

    /** A line nobody has touched yet. */
    public OrderLine(String serial, String opening) {
        this(serial, opening, LineState.PENDING, "");
    }

    /** A line in a given state with nothing written on it. */
    public OrderLine(String serial, String opening, LineState state) {
        this(serial, opening, state, "");
    }

    /** Whether the line has been dealt with, by being installed or skipped. */
    public boolean isSettled() {
        return state.isSettled();
    }

    /**
     * Turn a scope decision into pending lines, one a cylinder, pairing each cylinder with the door
     * it sits in.
     *
     * @throws InputError when the doors do not pair up with the cylinders
     */
    public static List<OrderLine> linesFrom(ScopeDecision decision, List<String> openings) {
        List<String> cylinders = decision.cylinders();
        if (openings.size() != cylinders.size()) {
            throw new InputError(
                    "every cylinder on the order needs the door it sits in",
                    Facts.of("cylinders", cylinders.size(), "openings", openings.size()));
        }
        List<OrderLine> lines = new ArrayList<>();
        for (int index = 0; index < cylinders.size(); index++) {
            lines.add(new OrderLine(cylinders.get(index), openings.get(index)));
        }
        return List.copyOf(lines);
    }
}
