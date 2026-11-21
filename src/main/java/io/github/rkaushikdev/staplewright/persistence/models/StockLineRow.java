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
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

/**
 * One movement of blank stock, in or out of the drawer.
 *
 * <p>Blank stock is kept as a ledger rather than as a number in a column. A single number cannot
 * answer the question anybody actually asks, which is where thirty of them went, so the movements
 * get their own table and the blank's stock count becomes a running total that the ledger writes.
 *
 * <p>Every line points at the part number and says what it did: received against a purchase order,
 * cut against a key tag, scrapped with a reason, put back, or counted. A count is the line that
 * carries a difference the shop did not choose, which is exactly why it has to be a line and not an
 * edit, and it is the only line that carries an adjustment.
 *
 * <p>The reference is deliberately loose. It holds a purchase order on a receipt, a key tag on a
 * cut, and a work order on a return; a foreign key for each would be three columns that are empty
 * on every row but one.
 */
@Entity
@Table(
        name = "blank_stock_lines",
        indexes = @Index(name = "ix_blank_stock_lines_part_day", columnList = "part_number, moved_on"))
public class StockLineRow {

    private static final String COUNTED = "counted";
    private static final Set<String> TAKING = Set.of("cut", "scrapped");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "part_number", nullable = false, length = Schema.SHORT)
    private String partNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "part_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_blank_stock_lines_part_number_key_blanks"))
    private BlankRow blank;

    @Column(nullable = false, length = Schema.SHORT)
    private String movement;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private LocalDate movedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String reference = "";

    private Integer adjustment;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected StockLineRow() {}

    /** A movement of a part, with no reference, adjustment or note until set. */
    public StockLineRow(String partNumber, String movement, int quantity, LocalDate movedOn) {
        this.partNumber = partNumber;
        this.movement = movement;
        this.quantity = quantity;
        this.movedOn = movedOn;
    }

    public Integer getId() {
        return id;
    }

    public String getPartNumber() {
        return partNumber;
    }

    /** The blank, as loaded; it follows {@link #getPartNumber()} once the row is read again. */
    public BlankRow getBlank() {
        return blank;
    }

    /** The stored value of the movement: received, cut, scrapped, returned or counted. */
    public String getMovement() {
        return movement;
    }

    /** How many moved, or for a count, how many were found in the drawer. */
    public int getQuantity() {
        return quantity;
    }

    public LocalDate getMovedOn() {
        return movedOn;
    }

    /** The purchase order, key tag or work order the movement was made against. */
    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    /** How far a count moved the running total; nothing on every other movement. */
    public Optional<Integer> getAdjustment() {
        return Optional.ofNullable(adjustment);
    }

    public void setAdjustment(Integer adjustment) {
        this.adjustment = adjustment;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /**
     * What this line does to the running total.
     *
     * <p>A count is the odd one out. Its quantity is what was in the drawer, not what moved, so the
     * movement is the adjustment beside it. Without that column the difference a stocktake found was
     * unrecoverable: the drawer went to the counted number and the ledger had no record of how far
     * it had moved to get there.
     */
    public int signed() {
        if (COUNTED.equals(movement)) {
            return adjustment == null ? 0 : adjustment;
        }
        return TAKING.contains(movement) ? -quantity : quantity;
    }

    @Override
    public String toString() {
        return "<StockLineRow " + partNumber + " " + movement + " " + quantity + ">";
    }
}
