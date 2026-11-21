package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

/**
 * A whole interchangeable core, with the operating keys it takes and the control key it answers to.
 *
 * @param stacks every chamber, in chamber order
 * @param keys the operating keys the core was pinned for
 * @param controlKey the key that pulls the core out of its housing
 */
public record CorePinning(List<CoreStack> stacks, List<Bitting> keys, Bitting controlKey) {

    public CorePinning {
        stacks = List.copyOf(stacks);
        keys = List.copyOf(keys);
    }

    /** Pin a core with plain drivers for its operating keys and one control key. */
    public static CorePinning pin(CoreSpec spec, List<Bitting> keys, Bitting controlKey) {
        return pin(spec, keys, controlKey, DriverProfile.STANDARD);
    }

    /**
     * Pin a core for its operating keys and one control key.
     *
     * @throws PinningError for no operating keys, a key of the wrong length, or any chamber that
     *     cannot be stacked
     */
    public static CorePinning pin(CoreSpec spec, List<Bitting> keys, Bitting controlKey, DriverProfile driverProfile) {
        if (keys.isEmpty()) {
            throw new PinningError("a core is pinned for at least one key");
        }
        List<Bitting> every = new ArrayList<>(keys);
        every.add(controlKey);
        for (Bitting key : every) {
            if (key.chambers() != spec.chambers()) {
                throw new PinningError(
                        "a key handed to this core is the wrong length",
                        Facts.of("chambers", spec.chambers(), "got", key.chambers()));
            }
        }
        List<CoreStack> stacks = IntStream.rangeClosed(1, spec.chambers())
                .mapToObj(chamber -> CoreStack.build(
                        spec,
                        chamber,
                        keys.stream().map(key -> key.at(chamber)).toList(),
                        controlKey.at(chamber),
                        driverProfile))
                .toList();
        return new CorePinning(stacks, keys, controlKey);
    }

    public int chambers() {
        return stacks.size();
    }

    /** Every piece of brass in the core, control wafers included. */
    public int pieceCount() {
        return stacks.stream().mapToInt(CoreStack::pieceCount).sum();
    }

    /**
     * One chamber's stack.
     *
     * @throws PinningError for a chamber the core does not have
     */
    public CoreStack stack(int chamber) {
        return stacks.stream()
                .filter(stack -> stack.chamber() == chamber)
                .findFirst()
                .orElseThrow(() -> new PinningError("this core has no such chamber", Facts.of("chamber", chamber)));
    }

    /** Every part to draw for the core, chamber by chamber. */
    public List<PinSegment> parts() {
        return stacks.stream().flatMap(stack -> stack.segments().stream()).toList();
    }
}
