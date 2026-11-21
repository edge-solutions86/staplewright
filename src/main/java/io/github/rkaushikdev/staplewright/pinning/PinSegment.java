package io.github.rkaushikdev.staplewright.pinning;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * One piece of brass, as it sits in a bin.
 *
 * <p>A chamber is a column of these, and every one of them is a part with a length and a bin in the
 * cabinet. Lengths are in mils. Two segments of the same kind, length and profile are the same part.
 *
 * @param kind where in the column it belongs
 * @param lengthMils how long it is, in mils
 * @param profile what the side looks like, which only a driver may vary
 */
public record PinSegment(SegmentKind kind, int lengthMils, DriverProfile profile) {

    public PinSegment {
        if (lengthMils <= 0) {
            throw new PinningError("a segment has a length", Facts.of("kind", kind.value()));
        }
        if (profile != DriverProfile.STANDARD && kind != SegmentKind.DRIVER) {
            throw new PinningError(
                    "only drivers come in profiles", Facts.of("kind", kind.value(), "profile", profile.value()));
        }
    }

    /** A segment with a plain side. */
    public PinSegment(SegmentKind kind, int lengthMils) {
        this(kind, lengthMils, DriverProfile.STANDARD);
    }

    /**
     * The label on the drawer: the kind's initial and the length, {@code B150}. The profile only
     * shows on a security driver, {@code D190S}.
     */
    public String binCode() {
        String code = initial(kind.value()) + lengthMils;
        if (kind == SegmentKind.DRIVER && profile != DriverProfile.STANDARD) {
            return code + initial(profile.value());
        }
        return code;
    }

    private static String initial(String word) {
        return Text.upper(word.substring(0, 1));
    }

    /** The bin code. */
    @Override
    public String toString() {
        return binCode();
    }
}
