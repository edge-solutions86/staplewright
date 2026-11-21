package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Reading a choice back from the text it is written as, the way a stored column or a typed
 * argument names a door function, a cylinder format or a call state.
 */
final class Choices {

    private Choices() {}

    /** The choice of {@code kind} written as {@code text}, when there is one. */
    static <E extends Enum<E> & Valued> Optional<E> find(Class<E> kind, String text) {
        for (E choice : kind.getEnumConstants()) {
            if (choice.value().equals(text)) {
                return Optional.of(choice);
            }
        }
        return Optional.empty();
    }

    /**
     * The choice of {@code kind} written as {@code text}.
     *
     * @throws InputError naming the {@code noun} when the text names no choice
     */
    static <E extends Enum<E> & Valued> E fromValue(Class<E> kind, String text, String noun) {
        return find(kind, text).orElseThrow(() -> new InputError("no such " + noun, Facts.of("value", text)));
    }
}
