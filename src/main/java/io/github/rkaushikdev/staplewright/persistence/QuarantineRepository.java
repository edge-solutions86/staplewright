package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import java.util.List;
import java.util.Optional;

/** The retired cuts, in the order they were retired. */
public final class QuarantineRepository extends Repository<RetiredBittingRow> {

    public QuarantineRepository(Session session) {
        super(session, RetiredBittingRow.class, "retired cut", Sort.ascending("id"));
    }

    /**
     * The retired cut written as the shop writes it, if it is retired.
     *
     * @throws io.github.rkaushikdev.staplewright.InputError for text that is not a bitting
     */
    public Optional<RetiredBittingRow> byBitting(String rendered) {
        return byBitting(Bitting.parse(rendered));
    }

    /** The retired cut, if it is retired. */
    public Optional<RetiredBittingRow> byBitting(Bitting bitting) {
        return one(Where.equal("bitting", bitting));
    }

    /** The cuts retired for good, because they were compromised. */
    public List<RetiredBittingRow> permanent() {
        return rows(Where.equal("reason", "compromised"));
    }
}
