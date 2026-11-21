package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.ControlKeyRow;
import java.util.List;

/** The control keys, by code. */
public final class ControlKeyRepository extends Repository<ControlKeyRow> {

    public ControlKeyRepository(Session session) {
        super(session, ControlKeyRow.class, "control key", Sort.ascending("code"));
    }

    /** Every control key not withdrawn. */
    public List<ControlKeyRow> live() {
        return rows(Where.isNull("withdrawnOn"));
    }

    public List<ControlKeyRow> heldBy(String holder) {
        return rows(Where.equal("holder", holder));
    }
}
