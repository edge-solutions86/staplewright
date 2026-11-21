package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import java.time.LocalDate;
import java.util.List;

/** Work orders by number. */
public final class WorkOrderRepository extends Repository<WorkOrderRow> {

    private static final List<String> OPEN = List.of("drafted", "scheduled", "started");

    public WorkOrderRepository(Session session) {
        super(session, WorkOrderRow.class, "work order", Sort.ascending("number"));
    }

    /** The orders not yet finished: drafted, scheduled or started. */
    public List<WorkOrderRow> openOrders() {
        return rows(Where.in("state", OPEN));
    }

    public List<WorkOrderRow> forTechnician(String technician) {
        return rows(Where.equal("technician", technician));
    }

    /** The orders scheduled for one day. */
    public List<WorkOrderRow> dueOn(LocalDate when) {
        return rows(Where.equal("scheduledFor", when));
    }
}
