package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * One symbol and the number that stands for it on a key bow.
 *
 * <p>The blind code table is the one table worth locking the room over. Everything else in the
 * schema is inconvenient to lose; this one is dangerous to leak. It turns every number stamped on
 * every master key on the campus back into a symbol, and the symbols are what say which doors a key
 * opens.
 *
 * <p>So it holds the symbol, the code and who assigned it when, and nothing else. No note field,
 * because a note is where somebody eventually writes what the key opens. Each side is unique and
 * never empty, and the audit trail lives somewhere else.
 */
@Entity
@Table(name = "blind_codes", uniqueConstraints = @UniqueConstraint(name = "uq_blind_codes_code", columnNames = "code"))
public class BlindCodeRow {

    @Id
    @Column(length = Schema.SHORT)
    private String symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String code;

    @Column(nullable = false)
    private LocalDate assignedOn;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String assignedBy = "";

    protected BlindCodeRow() {}

    /** A code for a symbol, assigned on a day by nobody named until set. */
    public BlindCodeRow(String symbol, String code, LocalDate assignedOn) {
        this.symbol = symbol;
        this.code = code;
        this.assignedOn = assignedOn;
    }

    /** The symbol as printed. */
    public String getSymbol() {
        return symbol;
    }

    /** The number stamped on the bow in place of the symbol. */
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public LocalDate getAssignedOn() {
        return assignedOn;
    }

    public void setAssignedOn(LocalDate assignedOn) {
        this.assignedOn = assignedOn;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(String assignedBy) {
        this.assignedBy = assignedBy;
    }

    @Override
    public String toString() {
        return "<BlindCodeRow " + code + ">";
    }
}
