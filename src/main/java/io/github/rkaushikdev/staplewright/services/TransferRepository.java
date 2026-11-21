package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.control.TransferState;
import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import java.util.List;

/** Handovers of keys between people, by number. */
public final class TransferRepository extends Repository<TransferRow> {

    public TransferRepository(Session session) {
        super(session, TransferRow.class, "transfer", Sort.ascending("number"));
    }

    /** Every handover nobody has settled yet. */
    public List<TransferRow> openClaims() {
        return inState(TransferState.CLAIMED.value());
    }

    /** The handovers in one state, given by its stored value. */
    public List<TransferRow> inState(String state) {
        return rows(Where.equal("state", state));
    }

    /** Every handover of one key. */
    public List<TransferRow> forTag(String tag) {
        return rows(Where.equal("tag", tag));
    }

    /** The open claims waiting on one person's signature. */
    public List<TransferRow> waitingOn(String identifier) {
        return rows(Where.equal("passedTo", identifier), Where.equal("state", TransferState.CLAIMED.value()));
    }

    /** Every handover one person says they made. */
    public List<TransferRow> claimedBy(String identifier) {
        return rows(Where.equal("passedBy", identifier));
    }
}
