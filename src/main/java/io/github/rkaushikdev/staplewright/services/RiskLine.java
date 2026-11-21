package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.spec.Exposure;
import java.time.LocalDate;
import java.util.List;

/**
 * One section the shop keys on, and what has happened to its patent.
 *
 * @param family the keyway family
 * @param section the section's code
 * @param systems the live systems keyed on it, by name
 * @param exposure where its patent stands
 */
public record RiskLine(String family, String section, List<String> systems, Exposure exposure) {

    public RiskLine {
        systems = List.copyOf(systems);
    }

    /** One line for the risk page. */
    public String describe(LocalDate today) {
        return exposure.describe(today) + " carrying " + String.join(", ", systems);
    }
}
