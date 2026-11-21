package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.Relations;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.spec.Rules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Incidental keys, which are the ones the shop never meant to make.
 *
 * <p>A cylinder does not know what a key symbol is. It knows that in chamber one it breaks at depth
 * 3 and at depth 7, and in chamber two at 4 and 6, and so on. Any key carrying one of the break
 * depths in every chamber turns the plug, whether or not anybody drew it on a schedule.
 *
 * <p>So a cylinder pinned for a master and three change keys accepts every combination of its break
 * depths, and the difference between those combinations and the keys on the schedule is the set of
 * incidental keys. They are real. They open the door. Nobody has one, which is the only thing that
 * makes them tolerable, and a system that generates hundreds of them is a system where somebody
 * eventually cuts one by accident and cannot explain why it works.
 *
 * <p>Cross keying is the usual cause. Put two unrelated change keys on one cylinder and the
 * incidental count goes up hard, which is why the shop asks what a request is for before it says
 * yes.
 */
public final class Incidentals {

    private Incidentals() {}

    /**
     * Every depth each chamber has to break at to take all of these keys, keyed by chamber, each
     * run shallow to deep.
     *
     * @throws InputError for no keys at all, or keys of different lengths
     */
    public static Map<Integer, List<Integer>> breakDepths(List<Bitting> keys) {
        if (keys.isEmpty()) {
            throw new InputError("a cylinder with no keys breaks nowhere");
        }
        return Relations.wafersFor(keys.get(0), keys.subList(1, keys.size()));
    }

    /**
     * Every key that turns a cylinder pinned to take the ones given.
     *
     * <p>Produced in chamber order with the last chamber turning over fastest, which puts the
     * intended keys near each other in the listing. The keys are made as the stream is read.
     */
    public static Stream<Bitting> operatingKeys(List<Bitting> keys) {
        Map<Integer, List<Integer>> breaks = breakDepths(keys);
        List<List<Integer>> runs =
                breaks.keySet().stream().sorted().map(breaks::get).toList();
        int total = runs.stream().mapToInt(List::size).reduce(1, Math::multiplyExact);
        return IntStream.range(0, total).mapToObj(index -> {
            List<Integer> cuts = new ArrayList<>(runs.size());
            int rest = index;
            for (int at = runs.size() - 1; at >= 0; at--) {
                List<Integer> run = runs.get(at);
                cuts.add(0, run.get(rest % run.size()));
                rest /= run.size();
            }
            return new Bitting(cuts);
        });
    }

    /** How many keys turn the plug, without building any of them. */
    public static int operatingCount(List<Bitting> keys) {
        return breakDepths(keys).values().stream().mapToInt(List::size).reduce(1, Math::multiplyExact);
    }

    /** Keys the cylinder accepts that nobody put on the schedule, in the operating key order. */
    public static List<Bitting> incidentalKeys(List<Bitting> keys) {
        Set<Bitting> intended = new HashSet<>(keys);
        return operatingKeys(keys).filter(key -> !intended.contains(key)).toList();
    }

    /** How many keys the cylinder accepts beyond the ones it was pinned for. */
    public static int incidentalCount(List<Bitting> keys) {
        return operatingCount(keys) - new HashSet<>(keys).size();
    }

    /** The incidental keys somebody could walk out with under the standing rules. */
    public static List<Bitting> cuttableIncidentals(DepthAndSpaceChart chart, List<Bitting> keys) {
        return cuttableIncidentals(chart, keys, CuttingRules.DEFAULT);
    }

    /**
     * The incidental keys somebody could actually walk out with.
     *
     * <p>An incidental key that breaks macs still opens the door, but no machine in the shop will
     * cut it and no factory blank arrives that way. The ones that pass the rules are the ones worth
     * losing sleep over.
     */
    public static List<Bitting> cuttableIncidentals(DepthAndSpaceChart chart, List<Bitting> keys, CuttingRules rules) {
        return incidentalKeys(keys).stream()
                .filter(key -> Rules.checkCuts(chart, key, rules).isEmpty())
                .toList();
    }

    /** Whether a cylinder takes its keys and nothing else. */
    public static boolean isClean(List<Bitting> keys) {
        return incidentalCount(keys) == 0;
    }

    /** The chamber carrying the most breaks, which is where to look first; the shallower wins a tie. */
    public static int worstChamber(List<Bitting> keys) {
        Map<Integer, List<Integer>> breaks = breakDepths(keys);
        return breaks.keySet().stream()
                .max(Comparator.<Integer>comparingInt(
                                chamber -> breaks.get(chamber).size())
                        .thenComparing(Comparator.reverseOrder()))
                .orElseThrow();
    }

    /** How many new incidental keys adding one more key to a cylinder makes. */
    public static int crossKeyingCost(List<Bitting> intended, Bitting extra) {
        int before = incidentalCount(intended);
        List<Bitting> widened = new ArrayList<>(intended);
        widened.add(extra);
        return incidentalCount(widened) - before;
    }
}
