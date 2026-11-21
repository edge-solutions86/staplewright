package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One order to a supplier, which blanks arrive against. Its state is one the schema knows, and its
 * lines go with it when it goes.
 */
@Entity
@Table(name = "blank_purchase_orders")
public class PurchaseOrderRow {

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String supplier;

    @Column(nullable = false)
    private LocalDate placedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "placed";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseLineRow> lines = new ArrayList<>();

    protected PurchaseOrderRow() {}

    /** An order just placed, with no lines yet. */
    public PurchaseOrderRow(String number, String supplier, LocalDate placedOn) {
        this.number = number;
        this.supplier = supplier;
        this.placedOn = placedOn;
    }

    public String getNumber() {
        return number;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public LocalDate getPlacedOn() {
        return placedOn;
    }

    /** The stored value of the order's state: placed, part received, received or cancelled. */
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

    /** The part numbers on the order, as loaded. */
    public List<PurchaseLineRow> getLines() {
        return Collections.unmodifiableList(lines);
    }

    void list(PurchaseLineRow line) {
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }

    /** Take a part off the order; the line is deleted with the next flush. */
    public void removeLine(PurchaseLineRow line) {
        lines.remove(line);
    }

    @Override
    public String toString() {
        return "<PurchaseOrderRow " + number + ">";
    }
}
