package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The two ways a shop hands out blocks of an array, and what each costs.
 *
 * <p>The block method is the obvious one. Cut the array into runs and give one run to each master.
 * It is easy to read, easy to extend, and it wastes whatever is left at the end of a run when a
 * department turns out to need six keys rather than ten.
 *
 * <p>The rotating constant method holds one chamber still per master instead. Every key under
 * master AA carries the same cut in the constant chamber, which means a key can be read back to its
 * master by looking at one position, and it means the masters do not compete for the same block of
 * the array. It costs a chamber: that one cannot be progressed for change keys any more, so the
 * system gets narrower as it gets easier to read.
 *
 * <p>Neither is right. The block method suits a campus that grows by buildings and the rotating
 * constant suits one that grows by departments, and the shop has used both in the same decade.
 */
public final class Blocks {

    private Blocks() {}

    /**
     * Cut the array into runs and hand one to each master, in order.
     *
     * @throws CapacityError when there are more masters than blocks
     * @throws ProgressionError when a master is listed twice, or the size is under one
     */
    public static List<Allocation> byBlocks(KeyBittingArray array, List<KeySymbol> symbols, int size) {
        List<List<Bitting>> blocks = array.blocks(size);
        if (symbols.size() > blocks.size()) {
            throw new CapacityError(
                    "not enough blocks for the masters that want one",
                    Facts.of("wanted", symbols.size(), "blocks", blocks.size()));
        }
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new ProgressionError("a symbol was handed a block twice");
        }
        List<Allocation> found = new ArrayList<>();
        for (int index = 0; index < symbols.size(); index++) {
            found.add(new Allocation(symbols.get(index), blocks.get(index)));
        }
        return List.copyOf(found);
    }

    /**
     * Every depth the array actually uses in one chamber, shallow first.
     *
     * @throws ProgressionError for a chamber outside the family
     */
    public static List<Integer> constantDepths(KeyBittingArray array, int chamber) {
        if (chamber < 1 || chamber > array.plan().chart().chambers()) {
            throw new ProgressionError("no such chamber in this family", Facts.of("chamber", chamber));
        }
        return List.copyOf(
                array.usable().stream().map(key -> key.at(chamber)).collect(Collectors.toCollection(TreeSet::new)));
    }

    /**
     * Hold one chamber still per master, one depth each.
     *
     * <p>A master gets every key in the array that carries its depth in the constant chamber, so the
     * allocations partition the array exactly and a key can be read back to its master from one cut.
     * Masters take the depths shallow first, in the order they were given.
     *
     * @throws ProgressionError when a master is listed twice, or the chamber is outside the family
     * @throws CapacityError when there are more masters than depths in the chamber
     */
    public static List<Allocation> byRotatingConstant(KeyBittingArray array, List<KeySymbol> symbols, int chamber) {
        if (new HashSet<>(symbols).size() != symbols.size()) {
            throw new ProgressionError("a symbol was handed a depth twice");
        }
        List<Integer> depths = constantDepths(array, chamber);
        if (symbols.size() > depths.size()) {
            throw new CapacityError(
                    "the constant chamber does not hold that many masters",
                    Facts.of("wanted", symbols.size(), "depths", depths.size(), "chamber", chamber));
        }
        List<Allocation> found = new ArrayList<>();
        for (int index = 0; index < symbols.size(); index++) {
            int depth = depths.get(index);
            List<Bitting> keys = array.usable().stream()
                    .filter(key -> key.at(chamber) == depth)
                    .toList();
            found.add(new Allocation(symbols.get(index), keys, chamber, depth));
        }
        return List.copyOf(found);
    }

    /** Which master a key belongs to, read off the allocations; empty when nobody was given it. */
    public static Optional<KeySymbol> masterOf(List<Allocation> allocations, Bitting key) {
        return allocations.stream()
                .filter(allocation -> allocation.keys().contains(key))
                .map(Allocation::symbol)
                .findFirst();
    }

    /** Keys in the array nobody was given, which is the waste, in array order. */
    public static List<Bitting> unallocated(KeyBittingArray array, List<Allocation> allocations) {
        Set<Bitting> handed = new HashSet<>();
        allocations.forEach(allocation -> handed.addAll(allocation.keys()));
        return array.usable().stream().filter(key -> !handed.contains(key)).toList();
    }

    /** How many keys in the array nobody was given. */
    public static int waste(KeyBittingArray array, List<Allocation> allocations) {
        return unallocated(array, allocations).size();
    }

    /** Whether the allocations cover the array exactly once each. */
    public static boolean isPartition(KeyBittingArray array, List<Allocation> allocations) {
        List<Bitting> handed = allocations.stream()
                .flatMap(allocation -> allocation.keys().stream())
                .toList();
        return handed.size() == new HashSet<>(handed).size() && handed.size() == array.usableCount();
    }
}
