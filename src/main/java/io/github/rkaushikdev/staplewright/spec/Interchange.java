package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Where an interchangeable core could cross from one control key to another.
 *
 * <p>The rule that earns its place here is the sleeve rule. Two cores keyed to different control
 * keys cannot go in the same housing family, because a core somebody pulls with a control key can be
 * put back in any housing that takes it, and a campus with two control sections is a campus where
 * half the cores can be moved into the other half.
 */
public final class Interchange {

    private Interchange() {}

    /**
     * Pairs of cores that could be moved into each other's housings, each pair once, in serial
     * order.
     *
     * <p>Only pairs on different control keys are worth reporting. Two cores on one control key are
     * already in the same trust boundary, so moving one is not a crossing, it is a rekey somebody did
     * with a screwdriver.
     */
    public static List<Crossing> swappable(List<Core> cores) {
        List<Crossing> found = new ArrayList<>();
        for (int index = 0; index < cores.size(); index++) {
            Core first = cores.get(index);
            for (Core second : cores.subList(index + 1, cores.size())) {
                if (first.controlCode().equals(second.controlCode())) {
                    continue;
                }
                if (first.fits(second)) {
                    found.add(crossing(first.serial(), second.serial()));
                }
            }
        }
        return found.stream()
                .distinct()
                .sorted(Comparator.comparing(Crossing::first).thenComparing(Crossing::second))
                .toList();
    }

    private static Crossing crossing(String one, String other) {
        return one.compareTo(other) <= 0 ? new Crossing(one, other) : new Crossing(other, one);
    }

    /** Sections carrying a live control key, which should be exactly one. */
    public static List<String> controlSections(ControlRegister register) {
        return register.sections();
    }

    /** Where a core could cross from one control key to another, as lines for a page. */
    public static List<String> crossingReport(ControlRegister register, List<Core> cores) {
        List<String> sections = controlSections(register);
        List<String> lines = new ArrayList<>();
        lines.add(register.liveKeys().size() + " live control keys on " + sections.size() + " sections");
        if (sections.size() > 1) {
            lines.add("more than one control section is in use: " + String.join(", ", sections));
        }
        List<Crossing> pairs = swappable(cores);
        if (pairs.isEmpty()) {
            lines.add("no core can be moved across a control key");
            return List.copyOf(lines);
        }
        lines.add(pairs.size() + " core pairs could swap housings across control keys");
        for (Crossing pair : pairs) {
            lines.add("  " + pair.first() + " and " + pair.second());
        }
        return List.copyOf(lines);
    }

    /** Who holds a control key. The shortest page in the building. */
    public static List<String> holderSheet(ControlRegister register) {
        Map<String, Integer> counts = register.holders();
        if (counts.isEmpty()) {
            return List.of("no control key is out");
        }
        List<String> lines = new ArrayList<>();
        int out = counts.values().stream().mapToInt(Integer::intValue).sum();
        lines.add(out + " control keys are out");
        counts.forEach((name, count) -> lines.add("  " + Text.left(name, 20) + count));
        return List.copyOf(lines);
    }
}
