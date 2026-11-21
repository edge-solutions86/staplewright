package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.Hibernate;

/**
 * One cylinder on one work order, and the opening it is going into. A cylinder is on an order once,
 * and may be on any number of orders over its life.
 */
@Entity
@Table(
        name = "work_order_lines",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_work_order_lines_order_number_serial",
                        columnNames = {"order_number", "serial"}),
        indexes = @Index(name = "ix_work_order_lines_opening", columnList = "opening_number"))
public class WorkOrderLineRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "order_number", nullable = false, length = Schema.SHORT)
    private String orderNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "order_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_work_order_lines_order_number_work_orders"))
    private WorkOrderRow workOrder;

    @Column(nullable = false, length = Schema.SHORT)
    private String serial;

    @Column(nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "pending";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected WorkOrderLineRow() {}

    /** A pending line on an order, by the order's number. */
    public WorkOrderLineRow(String orderNumber, String serial, String openingNumber) {
        this.orderNumber = orderNumber;
        this.serial = serial;
        this.openingNumber = openingNumber;
    }

    /** A pending line on an order, listed on the order when the order's lines are loaded. */
    public static WorkOrderLineRow of(WorkOrderRow order, String serial, String openingNumber) {
        WorkOrderLineRow row = new WorkOrderLineRow(order.getNumber(), serial, openingNumber);
        row.workOrder = order;
        if (Hibernate.isInitialized(order)) {
            order.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    /** The order, as loaded; it follows {@link #getOrderNumber()} once the row is read again. */
    public WorkOrderRow getWorkOrder() {
        return workOrder;
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    /** The number of the opening the cylinder is going into. */
    public String getOpeningNumber() {
        return openingNumber;
    }

    public void setOpeningNumber(String openingNumber) {
        this.openingNumber = openingNumber;
    }

    /** The stored value of the line's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "<WorkOrderLineRow " + orderNumber + " " + serial + ">";
    }
}
