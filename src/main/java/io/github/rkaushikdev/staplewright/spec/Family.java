package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.pinning.CoreSpec;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import java.util.Optional;

/**
 * A chart and everything that goes with it: the pinning spec for its cylinders, the core spec when
 * the family takes interchangeable cores, and the cutting policy.
 *
 * <p>The two specs sit beside the chart because they are always used together, and keeping them
 * apart means somebody eventually pairs an A2 chart with a conventional column height and wonders
 * why the drivers do not fit.
 *
 * @param chart the manufacturer's chart
 * @param pinning how the chart's depths turn into brass
 * @param core the second shear line, for a family that takes cores
 * @param rules the shop's cutting policy for the family
 */
public record Family(DepthAndSpaceChart chart, PinningSpec pinning, Optional<CoreSpec> core, CuttingRules rules) {

    /** A conventional family under the shop's standing rules. */
    public Family(DepthAndSpaceChart chart, PinningSpec pinning) {
        this(chart, pinning, Optional.empty(), CuttingRules.DEFAULT);
    }

    /** A family that takes cores, under the shop's standing rules. */
    public Family(DepthAndSpaceChart chart, PinningSpec pinning, CoreSpec core) {
        this(chart, pinning, Optional.of(core), CuttingRules.DEFAULT);
    }

    /** The family the way a part number reads it: {@code Sargent/LA six pin}. */
    public String key() {
        return chart.manufacturer() + "/" + chart.family();
    }

    public boolean takesCores() {
        return core.isPresent();
    }
}
