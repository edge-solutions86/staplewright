package io.github.rkaushikdev.staplewright;

import java.util.Locale;

/**
 * One way this library says no, and what a caller should do about it.
 *
 * @param code the stable code a caller branches on
 * @param kind the refusal class that carries it
 * @param status the status the service answers with
 * @param meaning what the refusal means, in a phrase
 * @param retry whether the same request could succeed once the caller fixed something
 */
public record Refusal(String code, Class<? extends HaspError> kind, int status, String meaning, boolean retry) {

    /** One line for the interface note: the code, the status, the meaning and the advice. */
    public String describe() {
        String again = retry ? "worth another go" : "not worth another go";
        return String.format(Locale.ROOT, "%-14s%d  %s (%s)", code, status, meaning, again);
    }
}
