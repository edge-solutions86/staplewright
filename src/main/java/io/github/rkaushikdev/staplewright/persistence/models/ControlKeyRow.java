package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.BittingColumn;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One control key, which pulls cores rather than opening doors.
 *
 * <p>A control key is not an issued key and is kept away from the key ledger. It has no symbol, it
 * is not on any keying system, and a report that counted it among the masters would be counting a
 * key that opens no doors at all and takes every lock off the wall instead. So it has its own table
 * and its own register. It always has a section, and it is never withdrawn before it was issued.
 */
@Entity
@Table(name = "control_keys", indexes = @Index(name = "ix_control_keys_holder", columnList = "holder"))
public class ControlKeyRow {

    @Id
    @Column(length = Schema.SHORT)
    private String code;

    @Column(nullable = false, length = Schema.SHORT)
    private String section;

    @Column(nullable = false, length = Schema.SHORT)
    private String format;

    @Column(nullable = false, length = BittingColumn.WIDTH)
    private Bitting bitting;

    @Column(nullable = false)
    private LocalDate issuedOn;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String holder = "";

    private LocalDate withdrawnOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "control")
    private List<CoreRow> cores = new ArrayList<>();

    protected ControlKeyRow() {}

    /** A live control key, held by nobody named until set. */
    public ControlKeyRow(String code, String section, String format, Bitting bitting, LocalDate issuedOn) {
        this.code = code;
        this.section = section;
        this.format = format;
        this.bitting = bitting;
        this.issuedOn = issuedOn;
    }

    public String getCode() {
        return code;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    /** The stored value of the core format the key pulls. */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public Bitting getBitting() {
        return bitting;
    }

    public void setBitting(Bitting bitting) {
        this.bitting = bitting;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public String getHolder() {
        return holder;
    }

    public void setHolder(String holder) {
        this.holder = holder;
    }

    public Optional<LocalDate> getWithdrawnOn() {
        return Optional.ofNullable(withdrawnOn);
    }

    public void setWithdrawnOn(LocalDate withdrawnOn) {
        this.withdrawnOn = withdrawnOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The cores this key pulls, as loaded. */
    public List<CoreRow> getCores() {
        return Collections.unmodifiableList(cores);
    }

    /** Whether the key is still in service, which is until it is withdrawn. */
    public boolean isLive() {
        return withdrawnOn == null;
    }

    @Override
    public String toString() {
        return "<ControlKeyRow " + code + ">";
    }
}
