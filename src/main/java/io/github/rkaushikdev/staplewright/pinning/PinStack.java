package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import java.util.Collection;
import java.util.List;

/**
 * One chamber of an ordinary cylinder, from the plug floor up.
 *
 * <p>Given the depths a chamber has to break at, the arithmetic is short: the bottom pin under the
 * shallowest cut, a wafer for every further break, and a driver bringing the column up to the shear
 * line. The spring sits above all of it and is not counted.
 *
 * @param chamber which chamber this is, counted from one at the bow
 * @param bottomMils the pin against the key
 * @param wafersMils the master wafers, bottom pin side first
 * @param driverMils the driver on top
 * @param driverProfile what the side of the driver looks like
 */
public record PinStack(
        int chamber, int bottomMils, List<Integer> wafersMils, int driverMils, DriverProfile driverProfile)
        implements PinColumn {

    public PinStack {
        wafersMils = List.copyOf(wafersMils);
    }

    /** A chamber with a plain driver. */
    public PinStack(int chamber, int bottomMils, List<Integer> wafersMils, int driverMils) {
        this(chamber, bottomMils, wafersMils, driverMils, DriverProfile.STANDARD);
    }

    /** The column for one chamber with a plain driver, given every depth it has to break at. */
    public static PinStack build(PinningSpec spec, int chamber, Collection<Integer> depths) {
        return build(spec, chamber, depths, DriverProfile.STANDARD);
    }

    /**
     * The column for one chamber, given every depth it has to break at, in any order and with
     * repeats allowed.
     *
     * @throws PinningError for a chamber the family does not have, no depths at all, a depth off the
     *     chart, a wafer too thin to stand up, or a column with no room left for a driver
     */
    public static PinStack build(
            PinningSpec spec, int chamber, Collection<Integer> depths, DriverProfile driverProfile) {
        int chambers = spec.chart().chambers();
        if (chamber < 1 || chamber > chambers) {
            throw new PinningError(
                    "no such chamber in this family", Facts.of("chamber", chamber, "chambers", chambers));
        }
        List<Integer> ordered = Breaks.ordered(depths, chamber);
        int bottom = spec.bottomPin(ordered.get(0));
        List<Integer> wafers = Breaks.wafers(spec, chamber, ordered);
        int driver = spec.columnMils() - bottom - Breaks.sum(wafers);
        if (driver < spec.minDriverMils()) {
            throw new PinningError(
                    "the stack reaches the shear line with no room for a driver",
                    Facts.of("chamber", chamber, "driver", driver, "shortest", spec.minDriverMils()));
        }
        return new PinStack(chamber, bottom, wafers, driver, driverProfile);
    }
}
