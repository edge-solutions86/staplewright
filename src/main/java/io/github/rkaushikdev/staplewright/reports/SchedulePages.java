package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.keys.ScheduleEntry;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The printed keying schedule, which is the document the shop hands over.
 *
 * <p>Two forms of the same thing. The schedule proper is one line a symbol, in hierarchy order,
 * indented so the tree is visible at arm's length. The bitting list is the same data sorted by cut,
 * which is what somebody uses when they have a key in their hand and no idea what it is.
 *
 * <p>Both are fixed width because they get printed, filed, and read on a bench with a light on one
 * side. Nothing here wraps.
 */
public final class SchedulePages {

    static final int SYMBOL_WIDTH = 10;
    static final int SECTION_WIDTH = 6;
    static final int CUT_WIDTH = 14;

    private SchedulePages() {}

    /** One line, indented by the symbol's level: the symbol, where it is cut, and what it is cut to. */
    public static String scheduleLine(ScheduleEntry entry) {
        return scheduleLine(entry, true);
    }

    /** One line: the symbol, where it is cut, and what it is cut to, or {@code open} when it is not cut. */
    public static String scheduleLine(ScheduleEntry entry, boolean indent) {
        String lead = indent ? "  ".repeat(entry.symbol().level()) : "";
        String name = lead + entry.symbol().render();
        String cut = entry.bitting().map(Bitting::render).orElse("open");
        String left = Text.left(name, SYMBOL_WIDTH + 6) + Text.left(entry.section(), SECTION_WIDTH);
        return left + Text.left(cut, CUT_WIDTH) + entry.note();
    }

    /** The whole schedule, in hierarchy order, with no title. */
    public static List<String> scheduleReport(KeyingSchedule schedule) {
        return scheduleReport(schedule, "");
    }

    /** The whole schedule, in hierarchy order, under a heading and over a count of what is open. */
    public static List<String> scheduleReport(KeyingSchedule schedule, String title) {
        List<String> lines = new ArrayList<>();
        if (!title.isEmpty()) {
            lines.add(title);
        }
        lines.add(Text.left("symbol", SYMBOL_WIDTH + 6) + Text.left("kwy", SECTION_WIDTH) + Text.left("cut", CUT_WIDTH)
                + "note");
        for (ScheduleEntry entry : schedule.entries()) {
            lines.add(Layout.stripEnd(scheduleLine(entry)));
        }
        lines.add(schedule.size() + " symbols, " + schedule.openSymbols().size() + " of them open");
        return List.copyOf(lines);
    }

    /** The same keys sorted by cut, for reading a key nobody recognises. What is not cut is left out. */
    public static List<String> bittingList(KeyingSchedule schedule) {
        List<ScheduleEntry> cut = schedule.entries().stream()
                .filter(entry -> entry.bitting().isPresent())
                .sorted(Comparator.comparing(entry -> entry.bitting().orElseThrow()))
                .toList();
        List<String> lines = new ArrayList<>();
        lines.add(Text.left("cut", CUT_WIDTH) + Text.left("symbol", SYMBOL_WIDTH) + "kwy");
        for (ScheduleEntry entry : cut) {
            String rendered = entry.bitting().map(Bitting::render).orElse("");
            lines.add(Text.left(rendered, CUT_WIDTH)
                    + Text.left(entry.symbol().render(), SYMBOL_WIDTH)
                    + entry.section());
        }
        return List.copyOf(lines);
    }

    /** What is drawn and not cut, which is what the shop owes somebody. */
    public static List<String> openSymbolsReport(KeyingSchedule schedule) {
        List<KeySymbol> openOnes = schedule.openSymbols();
        if (openOnes.isEmpty()) {
            return List.of("every symbol on this schedule is cut");
        }
        List<String> lines = new ArrayList<>();
        lines.add(openOnes.size() + " symbols drawn and not cut");
        for (KeySymbol symbol : openOnes) {
            lines.add("  " + symbol.render());
        }
        return List.copyOf(lines);
    }

    /** The hierarchy alone, with the count of what hangs under each key. */
    public static List<String> treeReport(KeyingSchedule schedule) {
        List<String> lines = new ArrayList<>();
        for (KeySymbol symbol : schedule.symbols()) {
            int below = schedule.descendants(symbol).size();
            String lead = "  ".repeat(symbol.level());
            String tail = below > 0 ? below + " under it" : "leaf";
            lines.add(lead + symbol.render() + "  " + tail);
        }
        return List.copyOf(lines);
    }

    /** Every symbol against the doors it opens, for the facilities office. */
    public static List<String> crossReference(KeyingSchedule schedule, Map<KeySymbol, List<String>> openingsBySymbol) {
        List<String> lines = new ArrayList<>();
        lines.add(Text.left("symbol", SYMBOL_WIDTH) + "opens");
        for (KeySymbol symbol : schedule.symbols()) {
            List<String> doors = openingsBySymbol.getOrDefault(symbol, List.of());
            String rendered = doors.isEmpty() ? "nothing on record" : String.join(", ", doors);
            lines.add(Text.left(symbol.render(), SYMBOL_WIDTH) + rendered);
        }
        return List.copyOf(lines);
    }
}
