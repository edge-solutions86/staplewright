package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import java.time.LocalDate;
import java.util.List;

/** The key ledger: stamped keys by tag, and the questions the counter asks of it. */
public final class KeyRepository extends Repository<IssuedKeyRow> {

    private static final String OUT = "out";

    public KeyRepository(Session session) {
        super(session, IssuedKeyRow.class, "key", Sort.ascending("tag"));
    }

    /** Every key out with somebody. */
    public List<IssuedKeyRow> out() {
        return rows(Where.equal("state", OUT));
    }

    /** The keys one person has out. */
    public List<IssuedKeyRow> heldBy(String identifier) {
        return rows(Where.equal("keyholder", identifier), Where.equal("state", OUT));
    }

    /** The keys out that were due back before {@code today}, the longest overdue first. */
    public List<IssuedKeyRow> overdue(LocalDate today) {
        return sortedBy(Sort.ascending("dueOn"), Where.equal("state", OUT), Where.less("dueOn", today));
    }

    /** The keys out that are cut to one symbol. */
    public List<IssuedKeyRow> cutTo(String symbol) {
        return rows(Where.equal("symbol", symbol), Where.equal("state", OUT));
    }

    /** The deposits held against every key out, in cents; nothing out is nothing held. */
    public long depositsHeld() {
        CriteriaBuilder builder = session().entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        Root<IssuedKeyRow> key = query.from(IssuedKeyRow.class);
        query.select(builder.coalesce(builder.sumAsLong(key.get("depositCents")), 0L))
                .where(builder.equal(key.get("state"), OUT));
        return session().entityManager().createQuery(query).getSingleResult();
    }
}
