package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.progression.Incidentals;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The pinning chart for one cylinder and the keys it was built for.
 *
 * <p>The bench works off a pinning chart: one row a chamber, bottom pin first, then wafers, then
 * the driver. This holds that chart together with the keys the cylinder is meant to take, because
 * six months later the only interesting question about a cylinder is what is supposed to open it.
 *
 * <p>Security drivers are decided per cylinder rather than per chamber. The shop either spools a
 * cylinder or it does not, and mixing profiles inside one cylinder is the kind of thing that reads
 * as clever and comes back as a service call.
 *
 * @param stacks every chamber, in chamber order
 * @param keys the keys the cylinder was pinned for
 * @param driverProfile the driver every chamber carries
 */
public record CylinderPinning(List<PinStack> stacks, List<Bitting> keys, DriverProfile driverProfile) {

    public CylinderPinning {
        stacks = List.copyOf(stacks);
        keys = List.copyOf(keys);
    }

    /** A cylinder with plain drivers. */
    public CylinderPinning(List<PinStack> stacks, List<Bitting> keys) {
        this(stacks, keys, DriverProfile.STANDARD);
    }

    /** Work out every chamber, with plain drivers, for a cylinder that has to take these keys. */
    public static CylinderPinning pin(PinningSpec spec, List<Bitting> keys) {
        return pin(spec, keys, DriverProfile.STANDARD);
    }

    /**
     * Work out every chamber for a cylinder that has to take these keys.
     *
     * @throws PinningError for no keys, a key of the wrong length, or any chamber that cannot be
     *     stacked
     */
    public static CylinderPinning pin(PinningSpec spec, List<Bitting> keys, DriverProfile driverProfile) {
        if (keys.isEmpty()) {
            throw new PinningError("a cylinder is pinned for at least one key");
        }
        int chambers = spec.chart().chambers();
        for (Bitting key : keys) {
            if (key.chambers() != chambers) {
                throw new PinningError(
                        "a key handed to this cylinder is the wrong length",
                        Facts.of("chambers", chambers, "got", key.chambers()));
            }
        }
        List<PinStack> stacks = IntStream.rangeClosed(1, chambers)
                .mapToObj(chamber -> PinStack.build(
                        spec, chamber, keys.stream().map(key -> key.at(chamber)).toList(), driverProfile))
                .toList();
        return new CylinderPinning(stacks, keys, driverProfile);
    }

    /** Pin a cylinder with plain drivers and take the brass out of the cabinet to do it. */
    public static CylinderPinning buildFromKit(PinningSpec spec, PinKit kit, List<Bitting> keys) {
        return buildFromKit(spec, kit, keys, DriverProfile.STANDARD);
    }

    /**
     * Pin a cylinder and take the brass out of the cabinet to do it.
     *
     * @throws CapacityError when the kit cannot fill the whole cylinder, which then takes nothing
     */
    public static CylinderPinning buildFromKit(
            PinningSpec spec, PinKit kit, List<Bitting> keys, DriverProfile driverProfile) {
        CylinderPinning pinning = pin(spec, keys, driverProfile);
        kit.draw(pinning.parts());
        return pinning;
    }

    /** A cylinder coming apart puts its parts back. */
    public static void stripToKit(PinKit kit, CylinderPinning pinning) {
        kit.putBack(pinning.parts());
    }

    public int chambers() {
        return stacks.size();
    }

    /**
     * One chamber's stack.
     *
     * @throws PinningError for a chamber the cylinder does not have
     */
    public PinStack stack(int chamber) {
        return stacks.stream()
                .filter(stack -> stack.chamber() == chamber)
                .findFirst()
                .orElseThrow(() -> new PinningError("this cylinder has no such chamber", Facts.of("chamber", chamber)));
    }

    /** Every master wafer in the cylinder, which is what it cost. */
    public int waferCount() {
        return stacks.stream().mapToInt(stack -> stack.wafersMils().size()).sum();
    }

    /** Every piece of brass in the cylinder. */
    public int pieceCount() {
        return stacks.stream().mapToInt(PinStack::pieceCount).sum();
    }

    /** The chambers that carry at least one wafer, in chamber order. */
    public List<Integer> masteredChambers() {
        return stacks.stream()
                .filter(stack -> !stack.wafersMils().isEmpty())
                .map(PinStack::chamber)
                .toList();
    }

    /** Every part to draw for the cylinder, chamber by chamber. */
    public List<PinSegment> parts() {
        return stacks.stream().flatMap(stack -> stack.segments().stream()).toList();
    }

    /** Whether a key turns this plug, drawn on the schedule or not. */
    public boolean operates(Bitting bitting) {
        if (bitting.chambers() != chambers()) {
            return false;
        }
        for (PinStack stack : stacks) {
            if (!breakDepths(stack.chamber()).contains(bitting.at(stack.chamber()))) {
                return false;
            }
        }
        return true;
    }

    /**
     * The cut depths one chamber accepts, read back off the keys, shallow to deep.
     *
     * @throws PinningError for a chamber the cylinder does not have
     */
    public List<Integer> breakDepths(int chamber) {
        stack(chamber);
        return Breaks.ordered(keys.stream().map(key -> key.at(chamber)).toList());
    }

    /** How many keys turn the plug, the intended ones included. */
    public int operatingKeyCount() {
        return Incidentals.operatingCount(keys);
    }

    /** How many keys turn the plug that nobody drew on the schedule. */
    public int incidentalKeyCount() {
        return Incidentals.incidentalCount(keys);
    }

    /** Whether the kit holds every part the cylinder needs. */
    public boolean canBeBuiltFrom(PinKit kit) {
        return kit.shortfall(parts()).isEmpty();
    }
}
