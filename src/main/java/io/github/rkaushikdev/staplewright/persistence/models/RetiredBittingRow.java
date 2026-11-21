package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.BittingColumn;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * A cut that is out of service, standing on its own on purpose.
 *
 * <p>This is the table that outlives everything else. A system gets replaced, a building comes
 * down, the cylinders go in a skip, and the cuts that were compromised while all that was happening
 * are still not available, because the keys are still out there in somebody's drawer. So it has no
 * foreign key to the system: it is a list of cut sequences, each retired once, with a reason and a
 * date, and the only thing that ever removes a row is a person deciding a superseded cut has sat
 * out long enough.
 */
@Entity
@Table(
        name = "retired_bittings",
        uniqueConstraints = @UniqueConstraint(name = "uq_retired_bittings_bitting", columnNames = "bitting"))
public class RetiredBittingRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = BittingColumn.WIDTH)
    private Bitting bitting;

    @Column(nullable = false, length = Schema.SHORT)
    private String reason;

    @Column(nullable = false)
    private LocalDate retiredOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected RetiredBittingRow() {}

    /** A cut retired for a reason on a day, with no symbol recorded until set. */
    public RetiredBittingRow(Bitting bitting, String reason, LocalDate retiredOn) {
        this.bitting = bitting;
        this.reason = reason;
        this.retiredOn = retiredOn;
    }

    public Integer getId() {
        return id;
    }

    public Bitting getBitting() {
        return bitting;
    }

    /** The stored value of why the cut was retired. */
    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public LocalDate getRetiredOn() {
        return retiredOn;
    }

    public void setRetiredOn(LocalDate retiredOn) {
        this.retiredOn = retiredOn;
    }

    /** The symbol the cut was serving when it was retired, empty when nobody recorded one. */
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** Whether the cut is retired for good: a compromised cut never comes back. */
    public boolean isPermanent() {
        return "compromised".equals(reason);
    }

    @Override
    public String toString() {
        return "<RetiredBittingRow " + bitting + ">";
    }
}
