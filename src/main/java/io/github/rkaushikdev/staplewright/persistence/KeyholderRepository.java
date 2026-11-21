package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import java.time.LocalDate;
import java.util.List;

/** The people the shop hands metal to, by identifier. */
public final class KeyholderRepository extends Repository<KeyholderRow> {

    public KeyholderRepository(Session session) {
        super(session, KeyholderRow.class, "keyholder", Sort.ascending("identifier"));
    }

    public List<KeyholderRow> inDepartment(String department) {
        return rows(Where.equal("department", department));
    }

    /**
     * The people still standing as active whose time on campus ran out before {@code today}, the
     * longest lapsed first.
     */
    public List<KeyholderRow> lapsed(LocalDate today) {
        return sortedBy(Sort.ascending("endsOn"), Where.equal("standing", "active"), Where.less("endsOn", today));
    }
}
