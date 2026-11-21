package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.SymbolColumn;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One sealed key box, as it stands today.
 *
 * <p>The box row says where the box stands now, which is what a walk round needs; the seal history
 * beside it says which key was behind which seal number between which two days, which is what
 * somebody needs eighteen months later when a key turns up in a car park and the only marking on
 * it is a symbol. The current seal is on the box row as well as at the end of the history. That is
 * a duplicate on purpose: the walk round reads one row per box, and a walk round that has to open a
 * history per box is a walk round nobody finishes.
 *
 * <p>One box per opening. Its state is one the schema knows, a sealed box has a seal, an opened box
 * says when, and a box is never checked before it was sealed. Its seal history goes with it.
 */
@Entity
@Table(
        name = "key_boxes",
        uniqueConstraints = @UniqueConstraint(name = "uq_key_boxes_opening_number", columnNames = "opening_number"))
public class KeyBoxRow {

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "building_code", nullable = false, length = Schema.SHORT)
    private String buildingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "building_code",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_boxes_building_code_buildings"))
    private BuildingRow building;

    @Column(name = "opening_number", nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "opening_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_boxes_opening_number_openings"))
    private OpeningRow opening;

    @Column(nullable = false, length = SymbolColumn.WIDTH)
    private KeySymbol symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String seal;

    @Column(nullable = false)
    private LocalDate sealedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "sealed";

    private LocalDate checkedOn;

    private LocalDate openedOn;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String openedBy = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "box", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SealRow> seals = new ArrayList<>();

    protected KeyBoxRow() {}

    /** A box sealed on a day with a key behind the seal, never checked or opened yet. */
    public KeyBoxRow(
            String number,
            String buildingCode,
            String openingNumber,
            KeySymbol symbol,
            String seal,
            LocalDate sealedOn) {
        this.number = number;
        this.buildingCode = buildingCode;
        this.openingNumber = openingNumber;
        this.symbol = symbol;
        this.seal = seal;
        this.sealedOn = sealedOn;
    }

    public String getNumber() {
        return number;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    public void setBuildingCode(String buildingCode) {
        this.buildingCode = buildingCode;
    }

    /** The building, as loaded; it follows {@link #getBuildingCode()} once the row is read again. */
    public BuildingRow getBuilding() {
        return building;
    }

    public String getOpeningNumber() {
        return openingNumber;
    }

    public void setOpeningNumber(String openingNumber) {
        this.openingNumber = openingNumber;
    }

    /** The opening, as loaded; it follows {@link #getOpeningNumber()} once the row is read again. */
    public OpeningRow getOpening() {
        return opening;
    }

    /** The symbol of the key behind the seal. */
    public KeySymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(KeySymbol symbol) {
        this.symbol = symbol;
    }

    /** The number on the current seal. */
    public String getSeal() {
        return seal;
    }

    public void setSeal(String seal) {
        this.seal = seal;
    }

    public LocalDate getSealedOn() {
        return sealedOn;
    }

    public void setSealedOn(LocalDate sealedOn) {
        this.sealedOn = sealedOn;
    }

    /** The stored value of the box's state: sealed, opened, empty or withdrawn. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Optional<LocalDate> getCheckedOn() {
        return Optional.ofNullable(checkedOn);
    }

    public void setCheckedOn(LocalDate checkedOn) {
        this.checkedOn = checkedOn;
    }

    public Optional<LocalDate> getOpenedOn() {
        return Optional.ofNullable(openedOn);
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public String getOpenedBy() {
        return openedBy;
    }

    public void setOpenedBy(String openedBy) {
        this.openedBy = openedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** Every seal the box has carried, as loaded. */
    public List<SealRow> getSeals() {
        return Collections.unmodifiableList(seals);
    }

    void list(SealRow sealed) {
        if (!seals.contains(sealed)) {
            seals.add(sealed);
        }
    }

    /** Whether the box is still on the wall in some state, rather than withdrawn. */
    public boolean isLive() {
        return !"withdrawn".equals(state);
    }

    @Override
    public String toString() {
        return "<KeyBoxRow " + number + " " + state + ">";
    }
}
