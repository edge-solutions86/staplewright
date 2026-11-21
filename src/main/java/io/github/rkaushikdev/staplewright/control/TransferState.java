package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.Valued;
import java.util.Optional;

/**
 * Where a handover has got to.
 *
 * <p>Claimed is the state the whole idea of a recorded handover exists for: the key is now with
 * somebody who has not signed for it, and the record says it moved. Every other state is settled,
 * and only a confirmed handover moved the key.
 */
public enum TransferState implements Valued {
    CLAIMED("claimed"),
    CONFIRMED("confirmed"),
    REFUSED("refused"),
    LAPSED("lapsed"),
    CANCELLED("cancelled");

    private final String value;

    TransferState(String value) {
        this.value = value;
    }

    @Override
    public String value() {
        return value;
    }

    /** Whether a handover here is still waiting to be settled. */
    public boolean isOpen() {
        return this == CLAIMED;
    }

    /** The state written as {@code text}, when there is one. */
    public static Optional<TransferState> find(String text) {
        return Choices.find(TransferState.class, text);
    }

    /** The state written as {@code text}, refusing anything else. */
    public static TransferState fromValue(String text) {
        return Choices.fromValue(TransferState.class, text, "handover state");
    }

    @Override
    public String toString() {
        return value;
    }
}
