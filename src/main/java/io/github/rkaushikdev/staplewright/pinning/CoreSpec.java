package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A pinning spec plus the second shear line an interchangeable core has.
 *
 * <p>A small format core drops into a housing and is held there by a lug, and the lug is retracted
 * by a second key that lifts every stack past the operating shear line to a control shear line
 * sitting a little higher. Pull the core, drop in a differently pinned one, and the room is rekeyed
 * in about four seconds without a screwdriver.
 *
 * <p>A break aligns with a shear line when the stack under it plus the lift the key gives equals
 * the height of that line, and a shallower cut lifts more. So the control break in a chamber sits
 * exactly the control gap above where an operating break at the same cut would sit.
 *
 * @param pinning the arithmetic of the operating column
 * @param controlGapMils how far the control line sits above the operating line
 */
public record CoreSpec(PinningSpec pinning, int controlGapMils) {

    public CoreSpec {
        if (controlGapMils <= 0) {
            throw new PinningError("the control line sits above the operating line");
        }
        if (controlGapMils < pinning.minWaferMils()) {
            throw new PinningError(
                    "the control gap is thinner than the thinnest wafer",
                    Facts.of("gap", controlGapMils, "thinnest", pinning.minWaferMils()));
        }
    }

    /** How many chambers a core of this family has. */
    public int chambers() {
        return pinning.chart().chambers();
    }

    /** Where a break has to sit for an operating key cut this deep. */
    public int operatingBreak(int depth) {
        return pinning.bottomPin(depth);
    }

    /** Where a break has to sit for the control key cut this deep. */
    public int controlBreak(int depth) {
        return pinning.bottomPin(depth) + controlGapMils;
    }

    /**
     * Every control cut that works in a chamber holding these operating cuts, shallow to deep.
     *
     * <p>This is the check that catches a control key borrowed off the wrong system. The control cut
     * is squeezed from below by the wafer that has to fit above the deepest operating break, and
     * from above by the driver that still has to reach the top of the chamber.
     *
     * @throws PinningError for no operating cuts at all, or operating cuts too close to stack
     */
    public List<Integer> controlCutRange(Collection<Integer> operatingDepths) {
        List<Integer> ordered = Breaks.ordered(operatingDepths);
        if (ordered.isEmpty()) {
            throw new PinningError("a chamber breaks somewhere");
        }
        int topOperating = pinning.bottomPin(ordered.get(0)) + Breaks.sum(pinning.wafersBetween(ordered));
        List<Integer> workable = new ArrayList<>();
        for (int depth : pinning.chart().depths()) {
            int control = controlBreak(depth);
            if (control - topOperating < pinning.minWaferMils()) {
                continue;
            }
            if (pinning.columnMils() - control < pinning.minDriverMils()) {
                continue;
            }
            workable.add(depth);
        }
        return List.copyOf(workable);
    }
}
