package io.github.rkaushikdev.staplewright.pinning;

import java.util.ArrayList;
import java.util.List;

/**
 * One chamber's column of brass, from the plug floor up.
 *
 * <p>A column is a column whether the cylinder has one shear line or two, so the stacking
 * arithmetic lives here once: a {@link PinStack} is the column of an ordinary cylinder, and a
 * {@link CoreStack} is the column of an interchangeable core, whose last wafer is the control
 * wafer. The wafers run in order from the bottom pin toward the driver, and the heights they break
 * at are just the running total.
 */
public sealed interface PinColumn permits PinStack, CoreStack {

    /** Which chamber this is, counted from one at the bow. */
    int chamber();

    /** The pin against the key, in mils. */
    int bottomMils();

    /** The master wafers, bottom pin side first, in mils. */
    List<Integer> wafersMils();

    /** The driver on top, in mils. */
    int driverMils();

    /** What the side of the driver looks like. */
    DriverProfile driverProfile();

    /** The whole column, which reaches the shear line exactly. */
    default int heightMils() {
        return bottomMils() + wafersMils().stream().mapToInt(Integer::intValue).sum() + driverMils();
    }

    /** How many pieces of brass the chamber takes: a bottom pin, a driver and every wafer. */
    default int pieceCount() {
        return 2 + wafersMils().size();
    }

    /** Where the plug turns, measured from the floor of the chamber, lowest first. */
    default List<Integer> breakHeights() {
        List<Integer> heights = new ArrayList<>();
        int height = bottomMils();
        heights.add(height);
        for (int wafer : wafersMils()) {
            height += wafer;
            heights.add(height);
        }
        return List.copyOf(heights);
    }

    /** The parts to draw out of the kit for this chamber: bottom pin, wafers, then the driver. */
    default List<PinSegment> segments() {
        List<PinSegment> parts = new ArrayList<>();
        parts.add(new PinSegment(SegmentKind.BOTTOM, bottomMils()));
        for (int wafer : wafersMils()) {
            parts.add(new PinSegment(SegmentKind.WAFER, wafer));
        }
        parts.add(new PinSegment(SegmentKind.DRIVER, driverMils(), driverProfile()));
        return List.copyOf(parts);
    }
}
