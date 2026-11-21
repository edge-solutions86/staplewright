package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.spec.CutViolation;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.Rules;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * The key bitting array: every key one plan holds, sorted into what the shop would cut and what it
 * would not.
 *
 * <p>A plan says how many keys exist. The array says which ones the shop would actually cut,
 * because macs and policy delete a slice of every plan and the slice is never small. Six pins
 * progressed two steps in three chambers looks like sixty four keys on paper and comes back as fifty
 * something once the milling limit has had its say.
 *
 * <p>The array also carries the block structure. Change keys are handed out in runs rather than one
 * at a time, so that a department that grows gets the next key in its own block instead of one
 * borrowed from the far side of the system. Blocks come off the array in printed order, which means
 * the keys in one block share their early cuts and differ only near the tip.
 *
 * <p>Both lists are worked out once and kept, because a six pin plan with four moving chambers is a
 * few hundred keys and the reports ask for them over and over. Walking an array gives the usable
 * keys in printed order.
 */
public final class KeyBittingArray implements Iterable<Bitting> {

    /**
     * One key the rules threw out, and the names of the rules that did it.
     *
     * @param bitting the key
     * @param reasons the rule names, in the order the rules are checked
     */
    public record Rejection(Bitting bitting, List<String> reasons) {

        public Rejection {
            reasons = List.copyOf(reasons);
        }
    }

    private final ProgressionPlan plan;
    private final CuttingRules rules;
    private final List<Bitting> usable;
    private final Set<Bitting> usableSet;
    private final List<Rejection> rejected;

    /** The array of a plan under the shop's standing rules. */
    public KeyBittingArray(ProgressionPlan plan) {
        this(plan, CuttingRules.DEFAULT);
    }

    /** The array of a plan under these rules. */
    public KeyBittingArray(ProgressionPlan plan, CuttingRules rules) {
        this.plan = plan;
        this.rules = rules;
        List<Bitting> kept = new ArrayList<>();
        List<Rejection> thrown = new ArrayList<>();
        plan.combinations().forEach(candidate -> {
            List<CutViolation> violations = Rules.checkCuts(plan.chart(), candidate, rules);
            if (violations.isEmpty()) {
                kept.add(candidate);
            } else {
                thrown.add(new Rejection(candidate, Rules.reasons(violations)));
            }
        });
        this.usable = List.copyOf(kept);
        this.usableSet = Set.copyOf(kept);
        this.rejected = List.copyOf(thrown);
    }

    public ProgressionPlan plan() {
        return plan;
    }

    public CuttingRules rules() {
        return rules;
    }

    /** What the plan promised before any rule was applied. */
    public int capacity() {
        return plan.capacity();
    }

    /** The keys the shop would cut, in printed order. */
    public List<Bitting> usable() {
        return usable;
    }

    /** The keys the rules threw out, in printed order, each with its reasons. */
    public List<Rejection> rejected() {
        return rejected;
    }

    public int usableCount() {
        return usable.size();
    }

    /** How many keys the rules cost. */
    public int loss() {
        return rejected.size();
    }

    /**
     * How many keys each rule cost, worst first, then by rule name.
     *
     * <p>A key thrown out twice by the same rule counts once against it.
     */
    public Map<String, Integer> rejectionReasons() {
        Map<String, Integer> counts = new TreeMap<>();
        for (Rejection rejection : rejected) {
            for (String reason : new HashSet<>(rejection.reasons())) {
                counts.merge(reason, 1, Integer::sum);
            }
        }
        Map<String, Integer> ordered = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .forEach(entry -> ordered.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(ordered);
    }

    /**
     * Cut the usable keys into runs of the given size.
     *
     * <p>The last block is short if the array does not divide evenly. It is still a block, because
     * throwing away nine keys to keep the arithmetic tidy is not something the shop will agree to.
     *
     * @throws ProgressionError for a size under one
     */
    public List<List<Bitting>> blocks(int size) {
        if (size < 1) {
            throw new ProgressionError("a block holds at least one key", Facts.of("size", size));
        }
        return IntStream.iterate(0, start -> start < usable.size(), start -> start + size)
                .mapToObj(start -> usable.subList(start, Math.min(start + size, usable.size())))
                .toList();
    }

    public int blockCount(int size) {
        return blocks(size).size();
    }

    /** Which block a key sits in, counted from one, or empty when it is not in the array. */
    public Optional<Integer> blockHolding(Bitting bitting, int size) {
        List<List<Bitting>> found = blocks(size);
        for (int index = 0; index < found.size(); index++) {
            if (found.get(index).contains(bitting)) {
                return Optional.of(index + 1);
            }
        }
        return Optional.empty();
    }

    /** The first run of keys off the array, refusing to overdraw. */
    public List<Bitting> take(int count) {
        return take(count, 0);
    }

    /**
     * The next run of keys off the array after skipping some, refusing to overdraw.
     *
     * @throws ProgressionError for a negative count or skip
     * @throws CapacityError when fewer keys are left than asked for
     */
    public List<Bitting> take(int count, int skip) {
        if (count < 0 || skip < 0) {
            throw new ProgressionError("cannot take a negative number of keys", Facts.of("count", count, "skip", skip));
        }
        List<Bitting> available = usable.subList(Math.min(skip, usable.size()), usable.size());
        if (available.size() < count) {
            throw new CapacityError(
                    "the array does not hold that many more keys",
                    Facts.of("wanted", count, "left", available.size(), "capacity", capacity()));
        }
        return available.subList(0, count);
    }

    /** Whether the shop would cut this key out of this array. */
    public boolean holds(Bitting bitting) {
        return usableSet.contains(bitting);
    }

    /** The usable keys in printed order. */
    @Override
    public Iterator<Bitting> iterator() {
        return usable.iterator();
    }

    /** How many usable keys there are. */
    public int size() {
        return usable.size();
    }

    /** One line saying how much of the plan survived the rules. */
    public String describe() {
        return usableCount() + " of " + capacity() + " cuttable, " + loss() + " thrown out by the rules";
    }

    /**
     * Hand one block to each symbol, in the order the symbols were given.
     *
     * <p>Refuses rather than wrapping around. A system that cannot seat every department it was
     * designed for is a design problem, not something to fix by quietly giving two of them the same
     * keys.
     *
     * @throws CapacityError when there are more symbols than blocks
     * @throws ProgressionError when a symbol is listed twice
     */
    public static Map<KeySymbol, List<Bitting>> assignBlocks(KeyBittingArray array, List<KeySymbol> symbols, int size) {
        List<List<Bitting>> blocks = array.blocks(size);
        if (symbols.size() > blocks.size()) {
            throw new CapacityError(
                    "not enough blocks for the masters that want one",
                    Facts.of("wanted", symbols.size(), "blocks", blocks.size(), "size", size));
        }
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new ProgressionError("a symbol was handed a block twice");
        }
        Map<KeySymbol, List<Bitting>> handed = new LinkedHashMap<>();
        for (int index = 0; index < symbols.size(); index++) {
            handed.put(symbols.get(index), blocks.get(index));
        }
        return Collections.unmodifiableMap(handed);
    }
}
