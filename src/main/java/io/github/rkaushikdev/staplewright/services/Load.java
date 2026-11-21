package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.reports.Imports;
import io.github.rkaushikdev.staplewright.reports.RowProblem;
import java.util.ArrayList;
import java.util.List;

/**
 * What loading one file did, or would have done.
 *
 * <p>A load with problems wrote nothing, whatever {@code written} would otherwise have been; a dry
 * run wrote nothing either, and says how many rows would have gone in.
 *
 * @param kind what was loaded: buildings, keyholders or openings
 * @param written how many rows went in, or would have
 * @param problems every row that stopped the file, in file order
 * @param dryRun whether the load only read the file
 */
public record Load(String kind, int written, List<RowProblem> problems, boolean dryRun) {

    public Load {
        problems = List.copyOf(problems);
    }

    /** A load that wrote. */
    public Load(String kind, int written, List<RowProblem> problems) {
        this(kind, written, problems, false);
    }

    /** Whether every row read. */
    public boolean isClean() {
        return problems.isEmpty();
    }

    /** The load as lines: what it came to, then how many problems and each one, when there are any. */
    public List<String> describe() {
        String head = kind + ": " + written + " rows";
        if (dryRun) {
            head = kind + ": " + written + " rows would load";
        }
        if (isClean()) {
            return List.of(head);
        }
        List<String> lines = new ArrayList<>();
        lines.add(head);
        lines.add(problems.size() + " problems");
        lines.addAll(Imports.describe(problems));
        return List.copyOf(lines);
    }
}
