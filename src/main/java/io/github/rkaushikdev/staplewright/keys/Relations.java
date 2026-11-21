package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * What two keys in one system are to each other, chamber by chamber.
 *
 * <p>Everything about mastering falls out of one comparison. Where a master and a key under it are
 * cut the same, the cylinder needs one pin. Where they differ, the cylinder needs a split, which is
 * a master wafer sitting between the bottom pin and the top pin so the plug turns at two different
 * heights.
 *
 * <p>So the count of chambers where two keys differ is the count of extra wafers the cylinder
 * carries, and that number is the cost of the whole design. A master that differs from its change
 * keys in every chamber is expensive, loose and full of keys nobody meant to cut. One that differs
 * in a single chamber is cheap and gives the system almost nowhere to grow.
 *
 * <p>The step between the two cuts matters as much as the count. A one step split leaves a wafer
 * under twenty thousandths of an inch, which the shop will not put in a cylinder that gets used, so
 * systems are progressed two steps at a time and the wafer comes out at twice the increment.
 */
public final class Relations {

    private Relations() {}

    /** Line two keys up and report every chamber they disagree in. */
    public static MasteringReport compare(Bitting above, Bitting below) {
        if (above.chambers() != below.chambers()) {
            throw new InputError(
                    "two keys of different lengths are not in one system",
                    Facts.of("above", above.chambers(), "below", below.chambers()));
        }
        List<ChamberSplit> splits = IntStream.rangeClosed(1, above.chambers())
                .filter(chamber -> above.at(chamber) != below.at(chamber))
                .mapToObj(chamber -> new ChamberSplit(chamber, above.at(chamber), below.at(chamber)))
                .toList();
        return new MasteringReport(splits, above.chambers());
    }

    /** How many extra wafers a cylinder needs to take both keys. */
    public static int waferCount(Bitting above, Bitting below) {
        return compare(above, below).splitCount();
    }

    /**
     * Every depth each chamber has to break at, master included.
     *
     * <p>A cylinder that takes a master and four change keys breaks in a chamber at every depth any
     * of those keys cuts there. The answer is keyed by chamber, bow first, and each run of depths is
     * sorted shallow to deep, which is the order the stack gets built in.
     *
     * @throws InputError when a change key is not as long as its master
     */
    public static Map<Integer, List<Integer>> wafersFor(Bitting master, List<Bitting> changes) {
        Map<Integer, TreeSet<Integer>> depths = new LinkedHashMap<>();
        for (int chamber = 1; chamber <= master.chambers(); chamber++) {
            depths.put(chamber, new TreeSet<>(List.of(master.at(chamber))));
        }
        for (Bitting change : changes) {
            if (change.chambers() != master.chambers()) {
                throw new InputError(
                        "a change key is not the same length as its master",
                        Facts.of("master", master.chambers(), "change", change.chambers()));
            }
            for (int chamber = 1; chamber <= master.chambers(); chamber++) {
                depths.get(chamber).add(change.at(chamber));
            }
        }
        Map<Integer, List<Integer>> out = new LinkedHashMap<>();
        depths.forEach((chamber, found) -> out.put(chamber, List.copyOf(found)));
        return Collections.unmodifiableMap(out);
    }

    /**
     * Whether every split between the two keys moves in whole steps.
     *
     * @throws InputError for a step under one
     */
    public static boolean stepsAreMultiplesOf(Bitting above, Bitting below, int step) {
        if (step < 1) {
            throw new InputError("a progression step is at least one", Facts.of("step", step));
        }
        return compare(above, below).splits().stream().allMatch(split -> split.step() % step == 0);
    }

    /** The tightest split between the two keys, which is the thin wafer; empty when they agree. */
    public static Optional<Integer> shallowestSplit(Bitting above, Bitting below) {
        return compare(above, below).splits().stream().map(ChamberSplit::step).min(Integer::compare);
    }

    /** Whether no two of these keys are cut the same. */
    public static boolean keysAreDistinct(List<Bitting> keys) {
        return new HashSet<>(keys).size() == keys.size();
    }

    /** Any bitting that turns up more than once, in the order first seen. */
    public static List<Bitting> duplicateBittings(List<Bitting> keys) {
        Map<Bitting, Integer> seen = new LinkedHashMap<>();
        for (Bitting key : keys) {
            seen.merge(key, 1, Integer::sum);
        }
        return seen.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
    }
}
