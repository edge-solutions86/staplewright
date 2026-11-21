package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.SymbolColumn;
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
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.hibernate.Hibernate;

/**
 * One seal on one key box, from the day it went on to the day it came off. A seal number goes on a
 * box once on any one day, and is never broken before it went on.
 */
@Entity
@Table(
        name = "key_box_seals",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_key_box_seals_box_number_seal_sealed_on",
                        columnNames = {"box_number", "seal", "sealed_on"}),
        indexes = @Index(name = "ix_key_box_seals_box_day", columnList = "box_number, sealed_on"))
public class SealRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "box_number", nullable = false, length = Schema.SHORT)
    private String boxNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "box_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_box_seals_box_number_key_boxes"))
    private KeyBoxRow box;

    @Column(nullable = false, length = Schema.SHORT)
    private String seal;

    @Column(nullable = false, length = SymbolColumn.WIDTH)
    private KeySymbol symbol;

    @Column(nullable = false)
    private LocalDate sealedOn;

    private LocalDate brokenOn;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String brokenBy = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected SealRow() {}

    /** A seal put on a box, by the box's number, still unbroken. */
    public SealRow(String boxNumber, String seal, KeySymbol symbol, LocalDate sealedOn) {
        this.boxNumber = boxNumber;
        this.seal = seal;
        this.symbol = symbol;
        this.sealedOn = sealedOn;
    }

    /** A seal put on a box, listed on the box when the box's seals are loaded. */
    public static SealRow of(KeyBoxRow box, String seal, KeySymbol symbol, LocalDate sealedOn) {
        SealRow row = new SealRow(box.getNumber(), seal, symbol, sealedOn);
        row.box = box;
        if (Hibernate.isInitialized(box)) {
            box.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public String getBoxNumber() {
        return boxNumber;
    }

    /** The box, as loaded; it follows {@link #getBoxNumber()} once the row is read again. */
    public KeyBoxRow getBox() {
        return box;
    }

    public String getSeal() {
        return seal;
    }

    /** The symbol of the key that was behind this seal. */
    public KeySymbol getSymbol() {
        return symbol;
    }

    public LocalDate getSealedOn() {
        return sealedOn;
    }

    public Optional<LocalDate> getBrokenOn() {
        return Optional.ofNullable(brokenOn);
    }

    public void setBrokenOn(LocalDate brokenOn) {
        this.brokenOn = brokenOn;
    }

    public String getBrokenBy() {
        return brokenBy;
    }

    public void setBrokenBy(String brokenBy) {
        this.brokenBy = brokenBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** Whether this is the seal on the box now, which is the one nobody has broken. */
    public boolean isCurrent() {
        return brokenOn == null;
    }

    /** How long this seal was on, or has been on so far, in days. */
    public int daysHeld(LocalDate today) {
        return Math.toIntExact(ChronoUnit.DAYS.between(sealedOn, brokenOn == null ? today : brokenOn));
    }

    @Override
    public String toString() {
        return "<SealRow " + boxNumber + " " + seal + ">";
    }
}
