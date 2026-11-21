package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One person asking for one key.
 *
 * <p>The person has to be on the register. A request that has been approved or denied carries the
 * day it was decided, which a check in the schema insists on.
 */
@Entity
@Table(
        name = "key_requests",
        indexes = {
            @Index(name = "ix_key_requests_state", columnList = "state"),
            @Index(name = "ix_key_requests_keyholder", columnList = "keyholder")
        })
public class KeyRequestRow {

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "keyholder", nullable = false, length = Schema.SHORT)
    private String keyholder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "keyholder",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_requests_keyholder_keyholders"))
    private KeyholderRow holder;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String building;

    @Column(nullable = false, length = Schema.LONG)
    private String reason = "";

    @Column(nullable = false)
    private LocalDate raisedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "raised";

    @Column(name = "is_master", nullable = false)
    private boolean master;

    private LocalDate decidedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String decidedBy = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected KeyRequestRow() {}

    /** A request just raised, for an ordinary key, with no reason given until set. */
    public KeyRequestRow(String number, String keyholder, String symbol, String building, LocalDate raisedOn) {
        this.number = number;
        this.keyholder = keyholder;
        this.symbol = symbol;
        this.building = building;
        this.raisedOn = raisedOn;
    }

    public String getNumber() {
        return number;
    }

    /** The identifier of the person asking. */
    public String getKeyholder() {
        return keyholder;
    }

    public void setKeyholder(String keyholder) {
        this.keyholder = keyholder;
    }

    /** The person asking, as loaded; it follows {@link #getKeyholder()} once the row is read again. */
    public KeyholderRow getHolder() {
        return holder;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getRaisedOn() {
        return raisedOn;
    }

    public void setRaisedOn(LocalDate raisedOn) {
        this.raisedOn = raisedOn;
    }

    /** The stored value of the request's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    /** Whether the key asked for is a master, which only some signatures may approve. */
    public boolean isMaster() {
        return master;
    }

    public void setMaster(boolean master) {
        this.master = master;
    }

    public Optional<LocalDate> getDecidedOn() {
        return Optional.ofNullable(decidedOn);
    }

    public void setDecidedOn(LocalDate decidedOn) {
        this.decidedOn = decidedOn;
    }

    /** The identifier of whoever decided, empty until somebody has. */
    public String getDecidedBy() {
        return decidedBy;
    }

    public void setDecidedBy(String decidedBy) {
        this.decidedBy = decidedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public String toString() {
        return "<KeyRequestRow " + number + ">";
    }
}
