package io.github.rkaushikdev.staplewright.rekey;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;
import java.util.Set;

/** Why a cut left service. Only a compromised cut is retired for good. */
public enum RetirementReason implements Valued {
    COMPROMISED("compromised"),
    SUPERSEDED("superseded"),
    DAMAGED("damaged"),
    RESERVED("reserved");

    /** The reasons that never expire, whatever the release window says. */
    public static final Set<RetirementReason> PERMANENT = Set.of(COMPROMISED);

    private final String value;

    RetirementReason(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a cut retired for this reason stays out of service for good. */
    public boolean isPermanent() {
        return PERMANENT.contains(this);
    }

    /** The retirement reason written as {@code text}, when there is one. */
    public static Optional<RetirementReason> find(String text) {
        return Choices.find(RetirementReason.class, text);
    }

    /** The retirement reason written as {@code text}, refusing anything else. */
    public static RetirementReason fromValue(String text) {
        return Choices.fromValue(RetirementReason.class, text, "retirement reason");
    }

    @Override
    public String toString() {
        return value;
    }
}
