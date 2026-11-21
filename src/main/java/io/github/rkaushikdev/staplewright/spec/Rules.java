package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * The rules that sit on top of a chart and throw good looking keys away.
 *
 * <p>A cut can be on the chart and still be a bad idea. Two neighbouring chambers that jump too far
 * leave a key the mill cannot cut cleanly, and the tip of the milling wheel runs into the metal it
 * just left. That limit is the macs, and every chart publishes one. The rest is shop policy, held
 * in {@link CuttingRules}.
 *
 * <p>Nothing here refuses. A key gets checked once and the caller gets every reason it failed,
 * because handing a locksmith one complaint at a time out of six is how an afternoon disappears.
 */
public final class Rules {

    private Rules() {}

    /** Every neighbouring pair that steps further than the chart allows. */
    public static List<CutViolation> checkMacs(DepthAndSpaceChart chart, Bitting bitting) {
        List<CutViolation> found = new ArrayList<>();
        List<Integer> steps = bitting.adjacentDifferences();
        for (int index = 0; index < steps.size(); index++) {
            int step = steps.get(index);
            if (step > chart.macs()) {
                int low = index + 1;
                found.add(new CutViolation(
                        "macs",
                        "chambers " + low + " and " + (low + 1) + " step " + step + ", the chart allows "
                                + chart.macs(),
                        Optional.of(low),
                        Facts.of("step", step, "macs", chart.macs())));
            }
        }
        return List.copyOf(found);
    }

    /** Runs of one depth that go on longer than policy allows. */
    public static List<CutViolation> checkRuns(CuttingRules rules, Bitting bitting) {
        List<CutViolation> found = new ArrayList<>();
        int runStart = 1;
        int runDepth = bitting.at(1);
        List<Integer> depths = bitting.depths();
        for (int chamber = 2; chamber <= bitting.chambers() + 1; chamber++) {
            Integer current = chamber <= bitting.chambers() ? depths.get(chamber - 1) : null;
            if (current != null && current == runDepth) {
                continue;
            }
            int length = chamber - runStart;
            if (length > rules.maxRun()) {
                found.add(new CutViolation(
                        "run",
                        length + " chambers in a row cut " + runDepth,
                        Optional.of(runStart),
                        Facts.of("length", length, "depth", runDepth, "limit", rules.maxRun())));
            }
            runStart = chamber;
            if (current != null) {
                runDepth = current;
            }
        }
        return List.copyOf(found);
    }

    /** Keys that use too few depths, or none but the ends of the chart. */
    public static List<CutViolation> checkVariety(DepthAndSpaceChart chart, CuttingRules rules, Bitting bitting) {
        List<CutViolation> found = new ArrayList<>();
        Set<Integer> distinct = new TreeSet<>(bitting.depths());
        if (distinct.size() < rules.minDistinct()) {
            found.add(new CutViolation(
                    "variety",
                    "the key uses " + distinct.size() + " depths, policy wants " + rules.minDistinct(),
                    Optional.empty(),
                    Facts.of("distinct", distinct.size(), "wanted", rules.minDistinct())));
        }
        if (rules.forbidAllShallowest() && distinct.equals(Set.of(chart.shallowest()))) {
            found.add(new CutViolation("all_shallowest", "every chamber is cut at the shallowest depth on the chart"));
        }
        if (rules.forbidAllDeepest() && distinct.equals(Set.of(chart.deepest()))) {
            found.add(new CutViolation("all_deepest", "every chamber is cut at the deepest depth on the chart"));
        }
        return List.copyOf(found);
    }

    /** The last chamber, which policy may want kept shallow. */
    public static List<CutViolation> checkTip(CuttingRules rules, Bitting bitting) {
        if (rules.shallowTipLimit().isEmpty()) {
            return List.of();
        }
        int limit = rules.shallowTipLimit().get();
        int tip = bitting.at(bitting.chambers());
        if (tip <= limit) {
            return List.of();
        }
        return List.of(new CutViolation(
                "tip",
                "the tip cut is " + tip + " and policy stops at " + limit,
                Optional.of(bitting.chambers()),
                Facts.of("depth", tip, "limit", limit)));
    }

    /** Every reason this bitting is not one the shop would cut, under the standing rules. */
    public static List<CutViolation> checkCuts(DepthAndSpaceChart chart, Bitting bitting) {
        return checkCuts(chart, bitting, CuttingRules.DEFAULT);
    }

    /**
     * Every reason this bitting is not one the shop would cut.
     *
     * <p>The chart is asked first. A key of the wrong length or carrying a depth the chart never
     * published gets that answer and nothing else, because the later rules would only be guessing at
     * what the cuts meant.
     */
    public static List<CutViolation> checkCuts(DepthAndSpaceChart chart, Bitting bitting, CuttingRules rules) {
        if (bitting.chambers() != chart.chambers()) {
            return List.of(new CutViolation(
                    "length",
                    "the key has " + bitting.chambers() + " cuts and the family has " + chart.chambers(),
                    Optional.empty(),
                    Facts.of("chambers", chart.chambers(), "got", bitting.chambers())));
        }
        List<CutViolation> offChart = new ArrayList<>();
        int chamber = 1;
        for (int depth : bitting.depths()) {
            if (!chart.allowsDepth(depth)) {
                offChart.add(new CutViolation(
                        "depth",
                        "depth " + depth + " is not on the " + chart.family() + " chart",
                        Optional.of(chamber),
                        Map.of("depth", depth)));
            }
            chamber++;
        }
        if (!offChart.isEmpty()) {
            return List.copyOf(offChart);
        }
        List<CutViolation> found = new ArrayList<>();
        found.addAll(checkMacs(chart, bitting));
        found.addAll(checkRuns(rules, bitting));
        found.addAll(checkVariety(chart, rules, bitting));
        found.addAll(checkTip(rules, bitting));
        return List.copyOf(found);
    }

    public static boolean isAcceptable(DepthAndSpaceChart chart, Bitting bitting) {
        return checkCuts(chart, bitting).isEmpty();
    }

    public static boolean isAcceptable(DepthAndSpaceChart chart, Bitting bitting, CuttingRules rules) {
        return checkCuts(chart, bitting, rules).isEmpty();
    }

    /** Just the rule names, which is what a summary line wants. */
    public static List<String> reasons(Collection<CutViolation> violations) {
        return violations.stream().map(CutViolation::rule).toList();
    }
}
