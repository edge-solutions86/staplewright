package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;

/**
 * One part number on one purchase order, and how much of it has landed. Something is always
 * ordered, and no more arrives than was ordered.
 */
@Entity
@Table(name = "blank_purchase_lines")
public class PurchaseLineRow {

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
            foreignKey = @ForeignKey(name = "fk_blank_purchase_lines_order_number_blank_purchase_orders"))
    private PurchaseOrderRow purchaseOrder;

    @Column(name = "part_number", nullable = false, length = Schema.SHORT)
    private String partNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "part_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_blank_purchase_lines_part_number_key_blanks"))
    private BlankRow blank;

    @Column(nullable = false)
    private int ordered;

    @Column(nullable = false)
    private int received;

    protected PurchaseLineRow() {}

    /** A line on an order, by the order's number, with nothing received yet. */
    public PurchaseLineRow(String orderNumber, String partNumber, int ordered) {
        this.orderNumber = orderNumber;
        this.partNumber = partNumber;
        this.ordered = ordered;
    }

    /** A line on an order, listed on the order when the order's lines are loaded. */
    public static PurchaseLineRow of(PurchaseOrderRow order, String partNumber, int ordered) {
        PurchaseLineRow row = new PurchaseLineRow(order.getNumber(), partNumber, ordered);
        row.purchaseOrder = order;
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
    public PurchaseOrderRow getPurchaseOrder() {
        return purchaseOrder;
    }

    public String getPartNumber() {
        return partNumber;
    }

    /** The blank, as loaded; it follows {@link #getPartNumber()} once the row is read again. */
    public BlankRow getBlank() {
        return blank;
    }

    public int getOrdered() {
        return ordered;
    }

    public void setOrdered(int ordered) {
        this.ordered = ordered;
    }

    public int getReceived() {
        return received;
    }

    public void setReceived(int received) {
        this.received = received;
    }

    /** How many are still to arrive. */
    public int outstanding() {
        return ordered - received;
    }

    @Override
    public String toString() {
        return "<PurchaseLineRow " + orderNumber + " " + partNumber + ">";
    }
}
