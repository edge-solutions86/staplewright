package io.github.rkaushikdev.staplewright.campus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Whether the hardware in a keyed alike set can actually take one key.
 *
 * <p>The checks are about the metal: one keyway section, one chamber count, and formats that take
 * the same cuts. A mortise cylinder and a seven pin core cannot be keyed alike however much anybody
 * wants them to be.
 */
public final class KeyedAlike {

    /** Formats that take the same cuts on the same section as each other. */
    public static final Set<CylinderFormat> INTERCHANGEABLE =
            Collections.unmodifiableSet(EnumSet.of(CylinderFormat.MORTISE, CylinderFormat.RIM, CylinderFormat.KIK));

    private KeyedAlike() {}

    /** The lockset cylinder at every door in the set, in door order. A door with none is skipped. */
    public static List<Cylinder> cylindersIn(HardwareRegister register, KeyedAlikeSet alike) {
        return alike.openings().stream()
                .sorted()
                .map(number -> register.at(number, Position.LOCKSET))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Every reason the hardware cannot take one key across this set, the empty doors first.
     *
     * <p>A set with no cylinders at all reports only its empty doors, since there is no metal to
     * compare.
     */
    public static List<String> problems(HardwareRegister register, KeyedAlikeSet alike) {
        List<String> found = new ArrayList<>();
        List<Cylinder> cylinders = cylindersIn(register, alike);
        alike.openings().stream()
                .sorted()
                .filter(number -> register.at(number, Position.LOCKSET).isEmpty())
                .forEach(number -> found.add(number + " has nothing in the lockset"));
        if (cylinders.isEmpty()) {
            return List.copyOf(found);
        }

        Set<String> sections = cylinders.stream().map(Cylinder::section).collect(Collectors.toCollection(TreeSet::new));
        if (sections.size() > 1) {
            found.add("the set spans keyway sections " + String.join(", ", sections));
        }
        Set<Integer> chambers =
                cylinders.stream().map(Cylinder::chambers).collect(Collectors.toCollection(TreeSet::new));
        if (chambers.size() > 1) {
            String listed = chambers.stream().map(String::valueOf).collect(Collectors.joining(", "));
            found.add("the set spans chamber counts " + listed);
        }
        Set<CylinderFormat> formats = cylinders.stream()
                .map(Cylinder::format)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(CylinderFormat.class)));
        if (formats.size() > 1 && !INTERCHANGEABLE.containsAll(formats)) {
            String listed = formats.stream().map(CylinderFormat::value).sorted().collect(Collectors.joining(", "));
            found.add("the set mixes formats that do not take one cut: " + listed);
        }
        return List.copyOf(found);
    }

    /** Whether nothing stops the set taking one key. */
    public static boolean canBeKeyedAlike(HardwareRegister register, KeyedAlikeSet alike) {
        return problems(register, alike).isEmpty();
    }
}
