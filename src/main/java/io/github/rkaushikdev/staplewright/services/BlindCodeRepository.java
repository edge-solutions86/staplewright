package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.BlindCodeRow;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Optional;

/** The blind code table, in symbol order. */
public final class BlindCodeRepository extends Repository<BlindCodeRow> {

    public BlindCodeRepository(Session session) {
        super(session, BlindCodeRow.class, "blind code", Sort.ascending("symbol"));
    }

    /** The row a stamped number belongs to, white space around it ignored. */
    public Optional<BlindCodeRow> byCode(String code) {
        return one(Where.equal("code", Text.strip(code)));
    }
}
