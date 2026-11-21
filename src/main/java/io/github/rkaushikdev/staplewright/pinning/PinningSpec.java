package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import java.util.ArrayList;
import java.util.List;

/**
 * How a chart's depths turn into lengths of brass.
 *
 * <p>The bottom pin fills from the plug up to the shallowest cut. Each further break adds a wafer as
 * thick as the distance between that cut and the one before it. A driver goes on top and brings the
 * column up to the height where the shell takes over.
 *
 * <p>Two things go wrong and both of them are physical. A wafer thinner than about a thirty
 * thousandth of an inch tips over in the chamber and jams the plug, so one step splits are refused
 * and systems get progressed two steps at a time. A column that already reaches the shear line
 * before the driver goes on has no room for a driver at all, which happens when a chamber is asked
 * to break at too many depths at once.
 *
 * @param chart the chart the depths come from
 * @param bottomAtShallowestMils the bottom pin that sits under the shallowest cut on the chart
 * @param columnMils the height from the floor of the plug chamber to the shear line, which every
 *     stack has to reach exactly
 * @param minWaferMils the thinnest wafer the shop will stand up in a chamber
 * @param minDriverMils the shortest driver that still holds the plug
 */
public record PinningSpec(
        DepthAndSpaceChart chart, int bottomAtShallowestMils, int columnMils, int minWaferMils, int minDriverMils) {

    /** The thinnest wafer the shop stands up unless told otherwise, in mils. */
    public static final int MIN_WAFER_MILS = 30;

    /** The shortest driver the shop fits unless told otherwise, in mils. */
    public static final int MIN_DRIVER_MILS = 90;

    public PinningSpec {
        if (bottomAtShallowestMils <= 0) {
            throw new PinningError("a bottom pin has a length");
        }
        if (columnMils <= bottomAtShallowestMils) {
            throw new PinningError(
                    "the column is shorter than the shallowest bottom pin",
                    Facts.of("column", columnMils, "bottom", bottomAtShallowestMils));
        }
        if (minWaferMils < 1) {
            throw new PinningError("a wafer has a thickness");
        }
    }

    /** A spec with the shop's thinnest wafer and shortest driver. */
    public PinningSpec(DepthAndSpaceChart chart, int bottomAtShallowestMils, int columnMils) {
        this(chart, bottomAtShallowestMils, columnMils, MIN_WAFER_MILS, MIN_DRIVER_MILS);
    }

    /** A spec with its own thinnest wafer and the shop's shortest driver. */
    public PinningSpec(DepthAndSpaceChart chart, int bottomAtShallowestMils, int columnMils, int minWaferMils) {
        this(chart, bottomAtShallowestMils, columnMils, minWaferMils, MIN_DRIVER_MILS);
    }

    public PinningSpec withBottomAtShallowestMils(int value) {
        return new PinningSpec(chart, value, columnMils, minWaferMils, minDriverMils);
    }

    public PinningSpec withColumnMils(int value) {
        return new PinningSpec(chart, bottomAtShallowestMils, value, minWaferMils, minDriverMils);
    }

    public PinningSpec withMinWaferMils(int value) {
        return new PinningSpec(chart, bottomAtShallowestMils, columnMils, value, minDriverMils);
    }

    public PinningSpec withMinDriverMils(int value) {
        return new PinningSpec(chart, bottomAtShallowestMils, columnMils, minWaferMils, value);
    }

    /**
     * The pin that sits against the key at one cut depth.
     *
     * @throws PinningError for a depth the chart does not publish
     */
    public int bottomPin(int depth) {
        if (!chart.allowsDepth(depth)) {
            throw new PinningError("that depth is not on the chart", Facts.of("depth", depth));
        }
        int steps = depth - chart.shallowest();
        return bottomAtShallowestMils + chart.incrementMils() * steps;
    }

    /**
     * The wafer between two breaks in one chamber.
     *
     * @throws PinningError unless the second depth is deeper than the first
     */
    public int wafer(int shallower, int deeper) {
        if (deeper <= shallower) {
            throw new PinningError(
                    "a wafer sits between two different depths", Facts.of("shallower", shallower, "deeper", deeper));
        }
        return chart.incrementMils() * (deeper - shallower);
    }

    /**
     * Every wafer a run of breaks needs, shallow to deep.
     *
     * <p>Refuses any wafer the shop will not stand up in a chamber, which is the check that stops a
     * one step split reaching the bench.
     *
     * @throws PinningError naming the thickness and the thinnest allowed
     */
    public List<Integer> wafersBetween(List<Integer> depths) {
        List<Integer> wafers = new ArrayList<>();
        for (int index = 1; index < depths.size(); index++) {
            int thickness = wafer(depths.get(index - 1), depths.get(index));
            if (thickness < minWaferMils) {
                throw new PinningError(
                        "that wafer is too thin to stand up in the chamber",
                        Facts.of("thickness", thickness, "thinnest", minWaferMils));
            }
            wafers.add(thickness);
        }
        return List.copyOf(wafers);
    }
}
