package io.github.rkaushikdev.staplewright.rekey;

import java.util.Optional;

/**
 * A day's plan cut in two at a building boundary.
 *
 * @param first the doors that fit in the day, or the whole plan when it all fits
 * @param second whatever is left for another day, empty when nothing is
 */
public record DaySplit(DayPlan first, Optional<DayPlan> second) {}
