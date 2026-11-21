package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Optional;

/** The shape of a cylinder, which decides what housing takes it. */
public enum CylinderFormat implements Valued {
    MORTISE("mortise"),
    RIM("rim"),
    KIK("kik"),
    SFIC("sfic"),
    PADLOCK("padlock"),
    CAM("cam");

    private final String value;

    CylinderFormat(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The format written as {@code text}, when there is one. */
    public static Optional<CylinderFormat> find(String text) {
        return Choices.find(CylinderFormat.class, text);
    }

    /** The format written as {@code text}, refusing anything else. */
    public static CylinderFormat fromValue(String text) {
        return Choices.fromValue(CylinderFormat.class, text, "cylinder format");
    }

    @Override
    public String toString() {
        return value;
    }
}
