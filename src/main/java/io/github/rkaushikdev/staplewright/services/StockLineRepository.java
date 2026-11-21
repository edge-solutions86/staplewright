package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.StockLineRow;
import java.time.LocalDate;
import java.util.List;

/** The stock ledger: every movement of blank stock, in the order it was written. */
public final class StockLineRepository extends Repository<StockLineRow> {

    public StockLineRepository(Session session) {
        super(session, StockLineRow.class, "stock line", Sort.ascending("id"));
    }

    /** Every movement of one part number. */
    public List<StockLineRow> forPart(String partNumber) {
        return rows(Where.equal("partNumber", partNumber));
    }

    /** Every movement on a day or after it, by day. */
    public List<StockLineRow> since(LocalDate when) {
        return sortedBy(Sort.ascending("movedOn"), Where.atLeast("movedOn", when));
    }

    /** Every movement made against one purchase order, key tag or work order. */
    public List<StockLineRow> against(String reference) {
        return rows(Where.equal("reference", reference));
    }
}
