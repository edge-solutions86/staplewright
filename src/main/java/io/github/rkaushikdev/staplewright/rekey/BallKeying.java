package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * The lost ball arrangement for one conventional cylinder under construction keying.
 *
 * <p>A building under construction is full of trades who all need to get in, and none of them
 * should still be getting in the week after handover. On conventional cylinders the answer is the
 * lost ball. A small steel ball sits above the driver in one or two chambers, which raises the
 * break the key has to reach, so the construction cut in those chambers is deeper than the
 * permanent one. The first turn of the permanent key drops the ball into a void milled in the
 * shell, the extra break disappears, and every construction key in the county becomes a
 * paperweight. Pick the ball chambers wrong and either the construction key does not work at all,
 * or it survives handover and nobody notices for a year.
 *
 * @param permanent the key the building is handed over on
 * @param construction the key the trades carry
 * @param ballChambers the chambers carrying a ball, counted from one
 * @param ballSteps how many depth steps a ball raises the break by
 */
public record BallKeying(Bitting permanent, Bitting construction, List<Integer> ballChambers, int ballSteps) {

    /** The ball most shops stock, worth two steps. */
    public static final int DEFAULT_BALL_STEPS = 2;

    /**
     * An arrangement, refusing one with no ball, a ball worth nothing, keys of different lengths or
     * a ball in a chamber the key does not have.
     *
     * @throws ConflictError for no ball chambers, mismatched keys or a chamber off the key
     * @throws InputError for a ball worth less than a step
     */
    public BallKeying {
        ballChambers = List.copyOf(ballChambers);
        if (ballChambers.isEmpty()) {
            throw new ConflictError("a construction cylinder carries at least one ball");
        }
        if (ballSteps < 1) {
            throw new InputError("a ball raises the break by at least one step");
        }
        if (permanent.chambers() != construction.chambers()) {
            throw new ConflictError(
                    "the two keys are not the same length",
                    Facts.of("permanent", permanent.chambers(), "construction", construction.chambers()));
        }
        for (int chamber : ballChambers) {
            if (chamber < 1 || chamber > permanent.chambers()) {
                throw new ConflictError("no such chamber on this key", Facts.of("chamber", chamber));
            }
        }
    }

    /** An arrangement with the stock ball. */
    public BallKeying(Bitting permanent, Bitting construction, List<Integer> ballChambers) {
        this(permanent, construction, ballChambers, DEFAULT_BALL_STEPS);
    }

    /** The chambers with no ball in them, in order. */
    public List<Integer> plainChambers() {
        Set<Integer> balls = new HashSet<>(ballChambers);
        return IntStream.rangeClosed(1, permanent.chambers())
                .filter(chamber -> !balls.contains(chamber))
                .boxed()
                .toList();
    }

    /**
     * Everything wrong with this arrangement, in the order it is read: the plain chambers where the
     * two keys disagree, then each ball chamber whose construction cut is not deeper by exactly the
     * ball.
     */
    public List<String> problems() {
        List<String> found = new ArrayList<>();
        for (int chamber : plainChambers()) {
            if (permanent.at(chamber) != construction.at(chamber)) {
                found.add("chamber " + chamber + " carries no ball and the two keys disagree there");
            }
        }
        for (int chamber : ballChambers) {
            int gap = construction.at(chamber) - permanent.at(chamber);
            if (gap <= 0) {
                found.add("chamber " + chamber + " needs the construction cut deeper than the permanent one");
            } else if (gap != ballSteps) {
                found.add("chamber " + chamber + " steps " + gap + " and the ball is worth " + ballSteps);
            }
        }
        return List.copyOf(found);
    }

    /** Whether nothing is wrong with the arrangement. */
    public boolean isSound() {
        return problems().isEmpty();
    }

    /** Whether turning the permanent key really kills the construction key. */
    public boolean voidedByFirstUse() {
        if (!isSound()) {
            return false;
        }
        return ballChambers.stream().anyMatch(chamber -> construction.at(chamber) != permanent.at(chamber));
    }

    /** Cut a construction key for a permanent one with the stock ball. */
    public static Bitting buildConstructionKey(
            DepthAndSpaceChart chart, Bitting permanent, List<Integer> ballChambers) {
        return buildConstructionKey(chart, permanent, ballChambers, DEFAULT_BALL_STEPS);
    }

    /**
     * Cut a construction key for a permanent one, or say why it cannot be.
     *
     * <p>The construction cut sits deeper by the ball, so a permanent cut already near the bottom of
     * the chart leaves nowhere to go. That is the check people forget, and it is the reason the ball
     * chambers get picked after the permanent keys are cut rather than before.
     *
     * @throws ConflictError for a chamber off the key, or a cut the chart has no room under, naming
     *     the depth wanted
     */
    public static Bitting buildConstructionKey(
            DepthAndSpaceChart chart, Bitting permanent, List<Integer> ballChambers, int ballSteps) {
        List<Integer> cuts = new ArrayList<>(permanent.depths());
        for (int chamber : ballChambers) {
            if (chamber < 1 || chamber > permanent.chambers()) {
                throw new ConflictError("no such chamber on this key", Facts.of("chamber", chamber));
            }
            int wanted = permanent.at(chamber) + ballSteps;
            if (!chart.allowsDepth(wanted)) {
                throw new ConflictError(
                        "the permanent cut is too deep to sit a ball above",
                        Facts.of("chamber", chamber, "permanent", permanent.at(chamber), "wanted", wanted));
            }
            cuts.set(chamber - 1, wanted);
        }
        return new Bitting(cuts);
    }

    /** Chambers with room above the permanent cut for the stock ball. */
    public static List<Integer> workableBallChambers(DepthAndSpaceChart chart, Bitting permanent) {
        return workableBallChambers(chart, permanent, DEFAULT_BALL_STEPS);
    }

    /** Chambers with room above the permanent cut for a ball, in order. */
    public static List<Integer> workableBallChambers(DepthAndSpaceChart chart, Bitting permanent, int ballSteps) {
        return IntStream.rangeClosed(1, permanent.chambers())
                .filter(chamber -> chart.allowsDepth(permanent.at(chamber) + ballSteps))
                .boxed()
                .toList();
    }
}
