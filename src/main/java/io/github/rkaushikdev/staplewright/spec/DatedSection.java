package io.github.rkaushikdev.staplewright.spec;

import java.time.LocalDate;

/**
 * A section and the day its patent runs out, one line of a wall planner.
 *
 * @param code the section's code
 * @param expiresOn the day its patent runs out
 */
public record DatedSection(String code, LocalDate expiresOn) {}
