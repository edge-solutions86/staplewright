package io.github.rkaushikdev.staplewright.reports;

import java.util.List;

/**
 * What applying a reading did: how many openings went onto the schedule, or, when any of them could
 * not, none of them and the rows that stopped the load.
 *
 * @param written how many openings were added, nought when anything was refused
 * @param problems why nothing was added, empty when everything was
 */
public record Applied(int written, List<RowProblem> problems) {

    public Applied {
        problems = List.copyOf(problems);
    }
}
