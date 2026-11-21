package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.util.Text;

/**
 * What actually gets cut into one key.
 *
 * @param tag the tag number of the key it goes on
 * @param symbol the symbol the key is cut to, which stays on paper unless the marking is the symbol
 * @param text what goes on the bow, empty for a key that carries nothing
 * @param marking what kind of mark it is
 */
public record Stamp(String tag, String symbol, String text, Marking marking) {

    public Stamp {
        if (Text.isBlank(tag)) {
            throw new InputError("a stamp goes on a tagged key");
        }
    }

    /** Whether the bow carries a blind code. */
    public boolean isBlind() {
        return marking == Marking.BLIND;
    }

    /** One line: {@code 41207 AA1 (the key symbol)}, with {@code blank} for a bare bow. */
    public String describe() {
        return tag + " " + (text.isEmpty() ? "blank" : text) + " (" + marking.value() + ")";
    }
}
