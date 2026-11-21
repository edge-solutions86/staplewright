package io.github.rkaushikdev.staplewright.cli;

/** The person at the terminal declined to go on, so nothing was done. */
public final class Stopped extends RuntimeException {

    public Stopped() {
        super("stopped");
    }
}
