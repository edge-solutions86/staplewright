package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.pinning.CorePinning;
import io.github.rkaushikdev.staplewright.pinning.CoreStack;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import io.github.rkaushikdev.staplewright.pinning.PinColumn;
import io.github.rkaushikdev.staplewright.pinning.PinSegment;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The pinning chart, which is the piece of paper that goes to the bench.
 *
 * <p>A chart is a document, not a property of a column, so it lives here and the pinning code stays
 * arithmetic.
 *
 * <p>The format is fixed by what the bench does with it. One row a chamber, numbered down the left,
 * brass in order from the bottom pin out, and the control wafer in brackets because that is the one
 * a technician has to not lose. Everything lines up in columns so a row with a missing piece is
 * obvious at arm's length.
 */
public final class PinningPages {

    private static final int WIDTH = 5;

    private PinningPages() {}

    /** One chamber, bottom pin first, driver last, the control wafer of a core in brackets. */
    public static String stackRow(PinColumn stack) {
        List<String> pieces = new ArrayList<>();
        pieces.add(String.valueOf(stack.bottomMils()));
        List<Integer> wafers = stack.wafersMils();
        if (stack instanceof CoreStack core) {
            for (int wafer : wafers.subList(0, Math.max(wafers.size() - 1, 0))) {
                pieces.add(String.valueOf(wafer));
            }
            pieces.add("[" + core.controlWaferMils() + "]");
        } else {
            for (int wafer : wafers) {
                pieces.add(String.valueOf(wafer));
            }
        }
        pieces.add(String.valueOf(stack.driverMils()));
        return Layout.right(pieces, WIDTH);
    }

    /** The numbered rows, without any heading. */
    public static List<String> chartBody(List<? extends PinColumn> stacks) {
        return stacks.stream()
                .map(stack -> Text.right(stack.chamber(), 2) + stackRow(stack))
                .toList();
    }

    /** A line a key the cylinder is pinned for. */
    public static List<String> keyLines(List<Bitting> keys) {
        return keys.stream().map(key -> "   key " + key.render()).toList();
    }

    /** The whole sheet for one cylinder, with no title. */
    public static List<String> cylinderChart(CylinderPinning pinning) {
        return cylinderChart(pinning, "");
    }

    /** The whole sheet for one cylinder: its keys, what it costs in brass, then a row a chamber. */
    public static List<String> cylinderChart(CylinderPinning pinning, String title) {
        List<String> lines = new ArrayList<>();
        if (!title.isEmpty()) {
            lines.add(title);
        }
        lines.addAll(keyLines(pinning.keys()));
        lines.add("   " + pinning.waferCount() + " wafers, " + pinning.pieceCount() + " pieces");
        lines.addAll(chartBody(pinning.stacks()));
        return List.copyOf(lines);
    }

    /** The sheet for a core, with no title. */
    public static List<String> coreChart(CorePinning pinning) {
        return coreChart(pinning, "");
    }

    /** The same sheet for a core, with the control key called out. */
    public static List<String> coreChart(CorePinning pinning, String title) {
        List<String> lines = new ArrayList<>();
        if (!title.isEmpty()) {
            lines.add(title);
        }
        lines.addAll(keyLines(pinning.keys()));
        lines.add("   control " + pinning.controlKey().render());
        lines.addAll(chartBody(pinning.stacks()));
        return List.copyOf(lines);
    }

    /** How much of each bin a job draws, bin by bin, for the person filling the tray. */
    public static List<String> partsSummary(Iterable<PinSegment> parts) {
        Map<String, Integer> counts = new TreeMap<>();
        for (PinSegment part : parts) {
            counts.merge(part.binCode(), 1, Integer::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> Text.left(entry.getKey(), 8) + Text.right(entry.getValue(), 4))
                .toList();
    }
}
