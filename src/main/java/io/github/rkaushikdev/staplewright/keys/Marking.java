package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.Valued;

/** What a shop puts on the brass. */
public enum Marking implements Valued {
    SYMBOL("the key symbol"),
    BLIND("a blind code"),
    TAG("the tag number"),
    NOTHING("nothing at all");

    private final String value;

    Marking(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }
}
