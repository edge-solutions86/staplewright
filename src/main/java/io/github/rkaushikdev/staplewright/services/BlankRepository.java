package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import java.util.List;

/** The part numbers in the blank drawer, by part number. */
public final class BlankRepository extends Repository<BlankRow> {

    public BlankRepository(Session session) {
        super(session, BlankRow.class, "blank", Sort.ascending("partNumber"));
    }

    /** The parts cut on one keyway section. */
    public List<BlankRow> onSection(int sectionId) {
        return rows(Where.equal("section.id", sectionId));
    }

    /** The parts the factory sells to the shop only. */
    public List<BlankRow> restricted() {
        return rows(Where.isTrue("restricted"));
    }

    /** The parts whose drawer holds fewer than their reorder level. */
    public List<BlankRow> belowReorder() {
        return rows((builder, row) ->
                builder.lessThan(Where.<Integer>path(row, "stock"), Where.<Integer>path(row, "reorderAt")));
    }
}
