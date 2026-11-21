package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.PurchaseOrderRow;
import java.util.List;

/** The purchase orders for blank stock, by order number. */
public final class OrderRepository extends Repository<PurchaseOrderRow> {

    public OrderRepository(Session session) {
        super(session, PurchaseOrderRow.class, "purchase order", Sort.ascending("number"));
    }

    /** The orders still coming: placed, or partly received. */
    public List<PurchaseOrderRow> outstanding() {
        return rows(Where.in("state", List.of("placed", "part received")));
    }

    /** Every order put to one supplier. */
    public List<PurchaseOrderRow> fromSupplier(String supplier) {
        return rows(Where.equal("supplier", supplier));
    }
}
