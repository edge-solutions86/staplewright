package io.github.rkaushikdev.staplewright.rekey;

import java.util.Optional;

/**
 * What the shop has decided to do about missing keys.
 *
 * @param recutAllOnRepin whether repinning a cylinder means recutting every key that opens it
 * @param alwaysRekeyExterior whether an exterior door is always rekeyed, however few keys it takes
 * @param alwaysRekeyMasters whether a lost master always means a rekey
 * @param ignoreBelowOpenings a loss opening fewer doors than this is not rekeyed
 * @param cylinderCap the most cylinders a rekey may touch before somebody signs for it
 */
public record ScopePolicy(
        boolean recutAllOnRepin,
        boolean alwaysRekeyExterior,
        boolean alwaysRekeyMasters,
        int ignoreBelowOpenings,
        Optional<Integer> cylinderCap) {

    /** The shop's standing policy. */
    public static final ScopePolicy DEFAULT = new ScopePolicy();

    public ScopePolicy {
        if (ignoreBelowOpenings < 0) {
            throw new IllegalArgumentException("a threshold is a count of openings");
        }
        if (cylinderCap.isPresent() && cylinderCap.get() < 1) {
            throw new IllegalArgumentException("a cap of nothing rekeys nothing");
        }
    }

    /** Exteriors and masters always rekeyed, no threshold and no cap. */
    public ScopePolicy() {
        this(false, true, true, 0, Optional.empty());
    }

    public ScopePolicy withRecutAllOnRepin(boolean value) {
        return new ScopePolicy(value, alwaysRekeyExterior, alwaysRekeyMasters, ignoreBelowOpenings, cylinderCap);
    }

    public ScopePolicy withAlwaysRekeyExterior(boolean value) {
        return new ScopePolicy(recutAllOnRepin, value, alwaysRekeyMasters, ignoreBelowOpenings, cylinderCap);
    }

    public ScopePolicy withAlwaysRekeyMasters(boolean value) {
        return new ScopePolicy(recutAllOnRepin, alwaysRekeyExterior, value, ignoreBelowOpenings, cylinderCap);
    }

    public ScopePolicy withIgnoreBelowOpenings(int value) {
        return new ScopePolicy(recutAllOnRepin, alwaysRekeyExterior, alwaysRekeyMasters, value, cylinderCap);
    }

    public ScopePolicy withCylinderCap(int value) {
        return new ScopePolicy(
                recutAllOnRepin, alwaysRekeyExterior, alwaysRekeyMasters, ignoreBelowOpenings, Optional.of(value));
    }
}
