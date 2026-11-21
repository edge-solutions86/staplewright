package io.github.rkaushikdev.staplewright.spec;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Arrays;
import java.util.Optional;

/** What an interchangeable core is sitting in. */
public enum HousingKind implements Valued {
    MORTISE("mortise housing"),
    RIM("rim housing"),
    KNOB("knob or lever"),
    PADLOCK("padlock"),
    CABINET("cabinet lock");

    private final String value;

    HousingKind(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** The housing written as {@code text}, when there is one. */
    public static Optional<HousingKind> find(String text) {
        return Arrays.stream(values()).filter(kind -> kind.value.equals(text)).findFirst();
    }

    /** The housing written as {@code text}, refusing anything else. */
    public static HousingKind fromValue(String text) {
        return find(text).orElseThrow(() -> new InputError("no such housing kind", Facts.of("value", text)));
    }

    @Override
    public String toString() {
        return value;
    }
}
