package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One chamber of an interchangeable core. The last wafer is the control wafer.
 *
 * <p>The control cut is squeezed from both sides. Too shallow and its break lands on top of the
 * deepest operating break with no room for a wafer between them. Too deep and the break climbs so
 * high there is nothing left of the chamber to put a driver in.
 *
 * @param chamber which chamber this is, counted from one at the bow
 * @param bottomMils the pin against the key
 * @param wafersMils the master wafers, bottom pin side first, the control wafer last
 * @param driverMils the driver on top
 * @param driverProfile what the side of the driver looks like
 */
public record CoreStack(
        int chamber, int bottomMils, List<Integer> wafersMils, int driverMils, DriverProfile driverProfile)
        implements PinColumn {

    public CoreStack {
        wafersMils = List.copyOf(wafersMils);
    }

    /** A chamber with a plain driver. */
    public CoreStack(int chamber, int bottomMils, List<Integer> wafersMils, int driverMils) {
        this(chamber, bottomMils, wafersMils, driverMils, DriverProfile.STANDARD);
    }

    /** One chamber of a core with a plain driver, given the keys' cuts and the control cut. */
    public static CoreStack build(CoreSpec spec, int chamber, Collection<Integer> operatingDepths, int controlDepth) {
        return build(spec, chamber, operatingDepths, controlDepth, DriverProfile.STANDARD);
    }

    /**
     * One chamber of a core, given every operating cut it takes and the control cut.
     *
     * @throws PinningError for a chamber the family does not have, no operating cuts, a depth off
     *     the chart, a wafer too thin to stand up, a control cut too shallow to clear the operating
     *     breaks, or one so deep it leaves no room for a driver
     */
    public static CoreStack build(
            CoreSpec spec,
            int chamber,
            Collection<Integer> operatingDepths,
            int controlDepth,
            DriverProfile driverProfile) {
        if (chamber < 1 || chamber > spec.chambers()) {
            throw new PinningError("no such chamber in this family", Facts.of("chamber", chamber));
        }
        List<Integer> ordered = Breaks.ordered(operatingDepths, chamber);
        PinningSpec pinning = spec.pinning();
        int bottom = pinning.bottomPin(ordered.get(0));
        List<Integer> wafers = Breaks.wafers(pinning, chamber, ordered);
        int control = spec.controlBreak(controlDepth);
        int controlWafer = control - bottom - Breaks.sum(wafers);
        if (controlWafer < pinning.minWaferMils()) {
            throw new PinningError(
                    "the control cut is too shallow to clear the operating breaks",
                    Facts.of(
                            "chamber",
                            chamber,
                            "control_depth",
                            controlDepth,
                            "deepest_operating",
                            ordered.get(ordered.size() - 1),
                            "wafer",
                            controlWafer));
        }
        int driver = pinning.columnMils() - control;
        if (driver < pinning.minDriverMils()) {
            throw new PinningError(
                    "the control break leaves no room for a driver",
                    Facts.of("chamber", chamber, "driver", driver, "shortest", pinning.minDriverMils()));
        }
        List<Integer> withControl = new ArrayList<>(wafers);
        withControl.add(controlWafer);
        return new CoreStack(chamber, bottom, withControl, driver, driverProfile);
    }

    /** The wafer that lifts the column to the control line. */
    public int controlWaferMils() {
        return wafersMils.get(wafersMils.size() - 1);
    }

    /** The heights the operating keys break at, lowest first. */
    public List<Integer> operatingBreaks() {
        List<Integer> heights = breakHeights();
        return heights.subList(0, heights.size() - 1);
    }

    /** The height the control key breaks at, above every operating break. */
    public int controlBreak() {
        List<Integer> heights = breakHeights();
        return heights.get(heights.size() - 1);
    }
}
