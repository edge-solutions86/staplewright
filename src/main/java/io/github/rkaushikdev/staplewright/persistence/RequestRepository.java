package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import java.util.List;

/** Key requests by number. */
public final class RequestRepository extends Repository<KeyRequestRow> {

    public RequestRepository(Session session) {
        super(session, KeyRequestRow.class, "request", Sort.ascending("number"));
    }

    /** The requests in one state, given by its stored value. */
    public List<KeyRequestRow> inState(String state) {
        return rows(Where.equal("state", state));
    }

    /** Everything one person has asked for, oldest first. */
    public List<KeyRequestRow> forKeyholder(String identifier) {
        return sortedBy(Sort.ascending("raisedOn"), Where.equal("keyholder", identifier));
    }
}
