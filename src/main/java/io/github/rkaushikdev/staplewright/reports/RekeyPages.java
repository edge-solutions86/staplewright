package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Estimate;
import io.github.rkaushikdev.staplewright.rekey.Incident;
import io.github.rkaushikdev.staplewright.rekey.OrderLine;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.rekey.WorkOrder;
import io.github.rkaushikdev.staplewright.util.Numbers;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The work packet: what a technician carries out to do a rekey.
 *
 * <p>One sheet a cylinder, in the order the doors sit in the building, because a crew that walks a
 * floor twice has wasted twenty minutes. Then the summary page with the cost, which is what goes back
 * to the department, and the incident sheet that says why any of this is happening.
 *
 * <p>The packet deliberately repeats itself. The cylinder serial is on the header of every sheet,
 * because sheets get separated on a bench and a page with no serial on it is a page nobody can use.
 */
public final class RekeyPages {

    static final int SERIAL_WIDTH = 10;
    static final int OPENING_WIDTH = 12;

    private RekeyPages() {}

    /** Why the work is happening, in the words the meeting used. */
    public static List<String> incidentSheet(Assessment assessment) {
        Incident incident = assessment.incident();
        List<String> lines = new ArrayList<>();
        lines.add("incident " + incident.number() + "  " + incident.cause().value() + "  " + incident.reportedOn());
        String holder = incident.keyholder().isEmpty() ? "nobody" : incident.keyholder();
        lines.add("key " + incident.symbol() + " on tag " + incident.tag() + ", held by " + holder);
        lines.add("grade " + assessment.severity().label());
        lines.add(assessment.openingCount() + " openings in "
                + assessment.buildings().size() + " buildings");
        if (!assessment.exteriorOpenings().isEmpty()) {
            lines.add("exterior doors exposed: " + String.join(", ", assessment.exteriorOpenings()));
        }
        if (!assessment.keysToRecut().isEmpty()) {
            lines.add("keys to recut: " + String.join(", ", assessment.keysToRecut()));
        }
        if (!incident.note().isEmpty()) {
            lines.add("note: " + incident.note());
        }
        return List.copyOf(lines);
    }

    /** What was decided and why, on one page. */
    public static List<String> scopeSheet(ScopeDecision decision) {
        List<String> lines = new ArrayList<>();
        lines.add("scope " + decision.scope().label() + ": " + decision.reason());
        lines.add(decision.cylinderCount() + " cylinders, " + decision.symbols().size() + " symbols recut");
        for (KeySymbol symbol : decision.symbols()) {
            lines.add("  recut " + symbol.render());
        }
        if (decision.overCap()) {
            lines.add("over the cap the shop set, wants a signature");
        }
        return List.copyOf(lines);
    }

    /** One cylinder, one page, with room to write on it, ending in a blank line. */
    public static List<String> lineSheet(OrderLine line, WorkOrder order) {
        return List.of(
                order.number() + "  cylinder " + line.serial() + "  door " + line.opening(),
                "state " + line.state().value(),
                "pinned by ..............  date ..............",
                "installed by ..........  date ..............",
                line.note().isEmpty() ? "note:" : "note: " + line.note(),
                "");
    }

    /** Every sheet for one work order, in door order and then by serial. */
    public static List<String> orderPacket(WorkOrder order) {
        List<String> lines = new ArrayList<>();
        lines.add("work order " + order.number() + "  " + order.state().value());
        lines.add("technician " + (order.technician().isEmpty() ? "unassigned" : order.technician()));
        lines.add(order.lines().size() + " cylinders across " + order.openings().size() + " doors");
        lines.add("");
        order.lines().stream()
                .sorted(Comparator.comparing(OrderLine::opening).thenComparing(OrderLine::serial))
                .forEach(line -> lines.addAll(lineSheet(line, order)));
        return List.copyOf(lines);
    }

    /** The page the department reads, in minutes and in money. */
    public static List<String> costSheet(Estimate estimate) {
        return List.of(
                estimate.cylinders() + " cylinders, " + estimate.cores() + " of them cores",
                estimate.keys() + " keys to cut",
                "bench " + estimate.benchMinutes() + " min, door " + estimate.doorMinutes() + " min, travel "
                        + estimate.travelMinutes() + " min",
                Numbers.repr(estimate.hours()) + " hours",
                "labour " + estimate.labourCents() + " cents, parts " + estimate.partsCents() + " cents",
                "total " + estimate.totalCents() + " cents");
    }

    /** The whole thing, stapled in the order it gets read. */
    public static List<String> packet(
            Assessment assessment, ScopeDecision decision, WorkOrder order, Estimate estimate) {
        List<String> lines = new ArrayList<>();
        lines.addAll(incidentSheet(assessment));
        lines.add("");
        lines.addAll(scopeSheet(decision));
        lines.add("");
        lines.addAll(costSheet(estimate));
        lines.add("");
        lines.addAll(orderPacket(order));
        return List.copyOf(lines);
    }
}
