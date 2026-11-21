package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The handover plan for one door in a building running on interchangeable cores.
 *
 * <p>On interchangeable cores construction keying is dull and reliable: construction cores go in,
 * permanent cores go in a box, and at handover somebody walks the building with a control key and
 * swaps one for the other at every door.
 *
 * @param opening the door
 * @param constructionCore the core the trades use until handover
 * @param permanentCore the core that goes in at handover
 */
public record CoreSwap(String opening, String constructionCore, String permanentCore) {

    /**
     * A swap, refusing one core on both sides of it.
     *
     * @throws ConflictError when the two cores are the same
     */
    public CoreSwap {
        if (constructionCore.equals(permanentCore)) {
            throw new ConflictError("the same core cannot be both halves of a swap", Facts.of("opening", opening));
        }
    }

    /**
     * Line up a construction core and a permanent core against every door, position by position.
     *
     * @throws InputError when the three lists are not the same length
     * @throws ConflictError when a permanent core is promised to two doors
     */
    public static List<CoreSwap> swapPlan(
            List<String> openings, List<String> constructionCores, List<String> permanentCores) {
        if (openings.size() != constructionCores.size() || constructionCores.size() != permanentCores.size()) {
            throw new InputError(
                    "every door needs both cores",
                    Facts.of(
                            "openings",
                            openings.size(),
                            "construction",
                            constructionCores.size(),
                            "permanent",
                            permanentCores.size()));
        }
        if (new HashSet<>(permanentCores).size() != permanentCores.size()) {
            throw new ConflictError("a permanent core is on the plan twice");
        }
        List<CoreSwap> plan = new ArrayList<>();
        for (int index = 0; index < openings.size(); index++) {
            plan.add(new CoreSwap(openings.get(index), constructionCores.get(index), permanentCores.get(index)));
        }
        return List.copyOf(plan);
    }

    /** The doors still on a construction core after a handover walk, in number order. */
    public static List<String> unswapped(List<CoreSwap> plan, List<String> doneOpenings) {
        Set<String> done = new HashSet<>(doneOpenings);
        return plan.stream()
                .map(CoreSwap::opening)
                .filter(opening -> !done.contains(opening))
                .sorted()
                .toList();
    }
}
