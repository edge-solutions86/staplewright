package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * One part number in the blank drawer, cut on one keyway section.
 *
 * <p>The stock count is never negative and is written by the stock ledger only; the reorder point
 * is where the shop wants to hear about it.
 */
@Entity
@Table(name = "key_blanks")
public class BlankRow {

    @Id
    @Column(length = Schema.SHORT)
    private String partNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "section_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_key_blanks_section_id_keyway_sections"))
    private KeywaySectionRow section;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String manufacturer;

    @Column(nullable = false)
    private int chambers;

    @Column(nullable = false)
    private int stock;

    @Column(nullable = false)
    private boolean restricted;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String supplier = "";

    @Column(nullable = false)
    private int reorderAt;

    protected BlankRow() {}

    /** A blank with none in the drawer, no supplier and no reorder point until set. */
    public BlankRow(String partNumber, KeywaySectionRow section, String manufacturer, int chambers) {
        this.partNumber = partNumber;
        this.section = section;
        this.manufacturer = manufacturer;
        this.chambers = chambers;
    }

    public String getPartNumber() {
        return partNumber;
    }

    public KeywaySectionRow getSection() {
        return section;
    }

    /** The key of the section row. */
    public Integer getSectionId() {
        return section.getId();
    }

    /** Move the blank to another section, and list it there. */
    public void setSection(KeywaySectionRow section) {
        if (this.section != null) {
            this.section.forgetBlank(this);
        }
        this.section = section;
        section.listBlank(this);
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public int getChambers() {
        return chambers;
    }

    public void setChambers(int chambers) {
        this.chambers = chambers;
    }

    public int getStock() {
        return stock;
    }

    public void setStock(int stock) {
        this.stock = stock;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public String getSupplier() {
        return supplier;
    }

    public void setSupplier(String supplier) {
        this.supplier = supplier;
    }

    public int getReorderAt() {
        return reorderAt;
    }

    public void setReorderAt(int reorderAt) {
        this.reorderAt = reorderAt;
    }

    @Override
    public String toString() {
        return "<BlankRow " + partNumber + ">";
    }
}
