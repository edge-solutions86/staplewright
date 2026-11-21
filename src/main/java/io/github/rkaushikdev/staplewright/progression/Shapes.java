package io.github.rkaushikdev.staplewright.progression;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Numbers;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Measuring the shape of a master key system, which is not the same as its size.
 *
 * <p>A system with four hundred change keys under one grand master is not a large system, it is a
 * broken one, and the number that says so is not the count. It is the fan out: how many keys hang
 * directly off each master, and how uneven that is across the tree.
 *
 * <p>Three numbers matter and the rest is decoration. Fan out, because a master with sixty children
 * is a master somebody will use as a shortcut instead of finding the right key. Depth, because every
 * level between the top key and the door is a wafer in every chamber and a wafer is where a system
 * starts picking itself. And reach, which is how many doors one key turns, because that is the
 * number that goes on an incident report.
 *
 * <p>Nothing here refuses anything a schedule could hold. These are measurements, and the shop
 * decides what to do about them, because a plant room with one master over forty doors is fine and
 * a residence hall with the same shape is not.
 */
public final class Shapes {

    /** More keys than this directly under one master is a master to watch. */
    public static final int WIDE_FAN_OUT = 25;

    /** A tree deeper than this costs a wafer in every chamber for every rank. */
    public static final int DEEP_TREE = 4;

    private Shapes() {}

    private static Optional<String> parentOf(KeySymbol symbol) {
        return symbol.parent().map(KeySymbol::render);
    }

    /**
     * Measure a set of symbols, which is all a schedule really is.
     *
     * @throws InputError for nothing to measure
     */
    public static Shape shapeOf(List<KeySymbol> symbols) {
        if (symbols.isEmpty()) {
            throw new InputError("there is nothing to measure");
        }
        Map<Integer, List<KeySymbol>> byLevel = new TreeMap<>();
        Map<String, Integer> fanOut = new LinkedHashMap<>();
        for (KeySymbol symbol : symbols) {
            byLevel.computeIfAbsent(symbol.level(), level -> new ArrayList<>()).add(symbol);
            parentOf(symbol).ifPresent(parent -> fanOut.merge(parent, 1, Integer::sum));
        }
        List<Level> levels = byLevel.entrySet().stream()
                .map(entry -> new Level(entry.getKey(), entry.getValue().size(), (int) entry.getValue().stream()
                        .filter(symbol -> !symbol.isChangeKey())
                        .count()))
                .toList();
        int deepest =
                byLevel.keySet().stream().mapToInt(Integer::intValue).max().orElseThrow();
        return new Shape(symbols.size(), deepest, levels, fanOut);
    }

    /** Masters carrying more than the shop is comfortable with, widest first. */
    public static List<Fan> wideMasters(Shape found) {
        return wideMasters(found, WIDE_FAN_OUT);
    }

    /**
     * Masters carrying more than {@code over} keys, widest first, then by symbol.
     *
     * @throws InputError for a limit under one
     */
    public static List<Fan> wideMasters(Shape found, int over) {
        if (over < 1) {
            throw new InputError("a fan out limit is at least one", Facts.of("over", over));
        }
        return found.fanOut().entrySet().stream()
                .filter(entry -> entry.getValue() > over)
                .map(entry -> new Fan(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(Fan::count, Comparator.reverseOrder())
                        .thenComparing(Fan::master))
                .toList();
    }

    /**
     * Masters with exactly one key under them, which are a level for nothing, in symbol order.
     *
     * <p>A master that opens one change key is a second key cut to the same doors with an extra
     * wafer in every chamber to make it possible. It is always a plan that was going to grow and did
     * not.
     */
    public static List<String> onlyChildren(Shape found) {
        return found.fanOut().entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Masters with nothing under them at all, in symbol order. The top key is never barren. */
    public static List<String> barrenMasters(List<KeySymbol> symbols) {
        Shape found = shapeOf(symbols);
        return symbols.stream()
                .filter(symbol -> !symbol.isChangeKey() && !symbol.isTop())
                .map(KeySymbol::render)
                .filter(master -> !found.fanOut().containsKey(master))
                .distinct()
                .sorted()
                .toList();
    }

    /** Masters that keys hang under and that are not on the schedule, which are gaps in the tree. */
    public static List<String> orphans(List<KeySymbol> symbols) {
        Set<String> present = symbols.stream().map(KeySymbol::render).collect(Collectors.toSet());
        return symbols.stream()
                .map(Shapes::parentOf)
                .flatMap(Optional::stream)
                .filter(parent -> !present.contains(parent))
                .distinct()
                .sorted()
                .toList();
    }

    /** The tree rank by rank, which is the picture people want. */
    public static List<String> levelTable(Shape found) {
        List<String> lines = new ArrayList<>();
        lines.add(found.symbols() + " symbols across " + (found.depth() + 1) + " levels");
        found.levels().forEach(level -> lines.add("  " + level.describe()));
        return List.copyOf(lines);
    }

    /** Everything worth saying about the shape of a system, on one page, against the usual limit. */
    public static List<String> shapeReport(List<KeySymbol> symbols) {
        return shapeReport(symbols, WIDE_FAN_OUT);
    }

    /** Everything worth saying about the shape of a system, on one page. */
    public static List<String> shapeReport(List<KeySymbol> symbols, int over) {
        Shape found = shapeOf(symbols);
        List<String> lines = new ArrayList<>();
        lines.add(found.describe());
        lines.add("");
        lines.addAll(levelTable(found));
        lines.add("");
        List<Fan> wide = wideMasters(found, over);
        if (wide.isEmpty()) {
            lines.add("no master carries more than " + over + " keys");
        } else {
            lines.add(wide.size() + " masters carry more than " + over + " keys");
            wide.forEach(fan -> lines.add("  " + Text.left(fan.master(), 10) + fan.count()));
        }
        List<String> thin = onlyChildren(found);
        if (!thin.isEmpty()) {
            lines.add(thin.size() + " masters have one key under them");
            thin.forEach(name -> lines.add("  " + name));
        }
        if (found.depth() > DEEP_TREE) {
            lines.add("the tree is " + found.depth() + " deep, which is a wafer in every chamber");
        }
        if (found.isLopsided()) {
            Fan widest = found.widest();
            lines.add("one branch carries the system: " + widest.master() + " has " + widest.count());
        }
        List<String> missing = orphans(symbols);
        if (!missing.isEmpty()) {
            lines.add(missing.size() + " masters are referred to and not on the schedule");
            missing.forEach(name -> lines.add("  " + name));
        }
        return List.copyOf(lines);
    }

    /** Two systems side by side, for the argument about which to extend. */
    public static List<String> compare(Shape first, Shape second) {
        return List.of(
                "symbols   " + Text.right(first.symbols(), 6) + Text.right(second.symbols(), 8),
                "depth     " + Text.right(first.depth(), 6) + Text.right(second.depth(), 8),
                "widest    " + Text.right(first.widest().count(), 6)
                        + Text.right(second.widest().count(), 8),
                "average   " + Text.right(Numbers.fixed(first.averageFanOut(), 1), 6)
                        + Text.right(Numbers.fixed(second.averageFanOut(), 1), 8));
    }
}
