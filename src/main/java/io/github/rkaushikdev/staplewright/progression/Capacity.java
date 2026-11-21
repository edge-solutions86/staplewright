package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * How much room a system has left, and what widening it would cost.
 *
 * <p>Every keying system runs out. The question is whether it runs out in four years or in forty,
 * and the shop only finds out by asking, because nobody notices an array filling up until the
 * afternoon there is no key left for a new office.
 *
 * <p>The forecast is deliberately crude. A department that took eleven keys last year will take
 * about eleven again, and any model more sophisticated than that is pretending to know something.
 * What the arithmetic is for is the difference between four years and forty, not the difference
 * between four and five.
 *
 * <p>Widening is where the interesting trade sits. Progressing another chamber multiplies the
 * capacity and costs a wafer in every cylinder. Dropping to a one step progression doubles it and
 * produces wafers too thin to stand up. Loosening macs is not a decision the shop gets to make at
 * all, because it is the manufacturer's number. So the options come back with what each one costs,
 * and somebody who knows the buildings picks one.
 */
public final class Capacity {

    /** The share of the cuttable keys the shop keeps back unless told otherwise. */
    public static final double DEFAULT_RESERVE = 0.2;

    private Capacity() {}

    /** What is left, against the usual reserve. */
    public static Headroom headroom(KeyBittingArray array, int used) {
        return headroom(array, used, DEFAULT_RESERVE);
    }

    /**
     * What is left, against the reserve the shop keeps for surprises.
     *
     * @param reserveFraction the share of the cuttable keys to keep back, from nought up to but not
     *     including one
     * @throws InputError for a negative count of cut keys, or a reserve outside that range
     */
    public static Headroom headroom(KeyBittingArray array, int used, double reserveFraction) {
        if (used < 0) {
            throw new InputError("a count of cut keys is not negative", Facts.of("used", used));
        }
        if (!(0 <= reserveFraction && reserveFraction < 1)) {
            throw new InputError("a reserve is a fraction under one", Facts.of("reserve", reserveFraction));
        }
        int wanted = (int) (array.usableCount() * reserveFraction);
        return new Headroom(array.capacity(), array.usableCount(), used, wanted);
    }

    /**
     * How many whole years the free keys beyond the reserve last at the rate they are being cut.
     *
     * <p>Empty when nothing is being cut, because a system nobody is growing does not run out and
     * saying otherwise invites a rebuild nobody needs. Never below nought.
     *
     * @throws InputError for a negative rate
     */
    public static Optional<Integer> yearsLeft(Headroom room, int keysPerYear) {
        if (keysPerYear < 0) {
            throw new InputError("a growth rate is not negative", Facts.of("rate", keysPerYear));
        }
        if (keysPerYear == 0) {
            return Optional.empty();
        }
        return Optional.of(Math.max(Math.floorDiv(room.free() - room.reserveWanted(), keysPerYear), 0));
    }

    /**
     * How many keys will be cut by then, at this year's rate.
     *
     * @throws InputError for a forecast that runs backward
     */
    public static int forecast(int used, int keysPerYear, int years) {
        if (years < 0) {
            throw new InputError("a forecast runs forward", Facts.of("years", years));
        }
        return used + keysPerYear * years;
    }

    /** Every way to widen this plan under the standing rules, widest first. */
    public static List<WideningOption> wideningOptions(ProgressionPlan plan) {
        return wideningOptions(plan, new CuttingRules());
    }

    /**
     * Every way to widen this plan, with what each one costs, widest first and then by name.
     *
     * <p>A chamber that is already progressed is not offered again, and an option that produces no
     * more keys than the plan already holds is left out: it is a cost with nothing on the other side
     * of it. A chamber or a step the plan cannot take is skipped.
     */
    public static List<WideningOption> wideningOptions(ProgressionPlan plan, CuttingRules rules) {
        int current = new KeyBittingArray(plan, rules).usableCount();
        int stepMils = plan.chart().incrementMils();
        List<WideningOption> found = new ArrayList<>();

        for (int chamber : plan.constants()) {
            Optional<ProgressionPlan> wider = attempt(plan, chamber);
            if (wider.isEmpty()) {
                continue;
            }
            int count = new KeyBittingArray(wider.get(), rules).usableCount();
            if (count <= current) {
                continue;
            }
            found.add(new WideningOption(
                    "progress chamber " + chamber,
                    count,
                    1,
                    plan.step() * stepMils,
                    "one more wafer in every cylinder"));
        }

        if (plan.step() > 1) {
            int count;
            try {
                count = new KeyBittingArray(plan.withStep(plan.step() - 1), rules).usableCount();
            } catch (ProgressionError cannot) {
                count = current;
            }
            if (count > current) {
                found.add(new WideningOption(
                        "progress " + (plan.step() - 1) + " steps instead of " + plan.step(),
                        count,
                        0,
                        (plan.step() - 1) * stepMils,
                        "thinner wafers, which the bench will argue about"));
            }
        }

        found.sort(Comparator.comparing(WideningOption::capacity, Comparator.reverseOrder())
                .thenComparing(WideningOption::name));
        return List.copyOf(found);
    }

    /** The widest option whose wafers the shop will actually build, the fewer wafers on a tie. */
    public static Optional<WideningOption> bestOption(List<WideningOption> options, int thinnestAllowed) {
        return options.stream()
                .filter(option -> option.thinnestSplit() >= thinnestAllowed)
                .max(Comparator.comparingInt(WideningOption::capacity)
                        .thenComparing(WideningOption::wafersAdded, Comparator.reverseOrder()));
    }

    private static Optional<ProgressionPlan> attempt(ProgressionPlan plan, int chamber) {
        List<Integer> moving = Stream.concat(plan.progressed().stream(), Stream.of(chamber))
                .sorted()
                .toList();
        try {
            return Optional.of(plan.withProgressed(moving));
        } catch (ProgressionError cannot) {
            return Optional.empty();
        }
    }
}
