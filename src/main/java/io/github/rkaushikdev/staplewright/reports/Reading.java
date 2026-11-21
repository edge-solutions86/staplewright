package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.campus.Opening;
import java.util.List;

/**
 * What one pass over an imported file produced: the openings it could read and the rows it could
 * not. Reading touches nothing, so a reading with problems in it is simply thrown away and the file
 * fixed.
 *
 * @param openings every row that read cleanly, in file order
 * @param problems every row that did not, in file order
 */
public record Reading(List<Opening> openings, List<RowProblem> problems) {

    public Reading {
        openings = List.copyOf(openings);
        problems = List.copyOf(problems);
    }

    /** Whether every row read, which is the only reading that may be applied. */
    public boolean isClean() {
        return problems.isEmpty();
    }

    /** How many rows read cleanly. */
    public int count() {
        return openings.size();
    }

    /** One line for the person who sent the file: {@code 2 rows read, 0 to fix}. */
    public String summary() {
        return count() + " rows read, " + problems.size() + " to fix";
    }
}
