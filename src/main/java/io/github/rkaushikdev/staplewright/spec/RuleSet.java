package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.List;

/**
 * A chart and the policy that goes with it, carried together.
 *
 * @param chart the manufacturer's chart
 * @param rules the shop's policy on top of it
 */
public record RuleSet(DepthAndSpaceChart chart, CuttingRules rules) {

    /** A chart under the shop's standing rules. */
    public RuleSet(DepthAndSpaceChart chart) {
        this(chart, new CuttingRules());
    }

    /** Every reason the shop would not cut this key. */
    public List<CutViolation> check(Bitting bitting) {
        return Rules.checkCuts(chart, bitting, rules);
    }

    public boolean accepts(Bitting bitting) {
        return Rules.isAcceptable(chart, bitting, rules);
    }
}
