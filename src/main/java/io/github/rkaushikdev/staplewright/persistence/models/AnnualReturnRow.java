package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;

/**
 * One year of key control numbers, as they stood on the day filed.
 *
 * <p>Everything else in the schema answers questions about today. The ledger says what is out now,
 * the register says what is installed now, and a question about last March gets a shrug. That is
 * deliberate: a system that reconstructs the past from the present is a system that will
 * confidently tell you something wrong.
 *
 * <p>A filed return is the exception. It is four numbers, measured on a day, written down and never
 * touched again, and that is what makes a trend possible at all. So the row is immutable by
 * convention and by constraint: filing over an existing year is refused, because the interesting
 * case is always somebody running the numbers again after a clean up and quietly improving last
 * year. The checks in the schema keep every count at zero or more, and the masters and the
 * unaccounted for among the keys out.
 */
@Entity
@Table(
        name = "annual_returns",
        uniqueConstraints = @UniqueConstraint(name = "uq_annual_returns_year", columnNames = "year"))
public class AnnualReturnRow {

    @Id
    private Integer year;

    @Column(nullable = false)
    private LocalDate measuredOn;

    @Column(nullable = false)
    private int keysOut;

    @Column(nullable = false)
    private int mastersOut;

    @Column(nullable = false)
    private int unaccounted;

    @Column(nullable = false)
    private int keyholders;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String filedBy = "";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected AnnualReturnRow() {}

    /** A return for a year, as measured on a day, filed by nobody named until set. */
    public AnnualReturnRow(
            int year, LocalDate measuredOn, int keysOut, int mastersOut, int unaccounted, int keyholders) {
        this.year = year;
        this.measuredOn = measuredOn;
        this.keysOut = keysOut;
        this.mastersOut = mastersOut;
        this.unaccounted = unaccounted;
        this.keyholders = keyholders;
    }

    public int getYear() {
        return year;
    }

    public LocalDate getMeasuredOn() {
        return measuredOn;
    }

    public int getKeysOut() {
        return keysOut;
    }

    public int getMastersOut() {
        return mastersOut;
    }

    /** The keys out that nobody could account for on the day. */
    public int getUnaccounted() {
        return unaccounted;
    }

    public int getKeyholders() {
        return keyholders;
    }

    public String getFiledBy() {
        return filedBy;
    }

    public void setFiledBy(String filedBy) {
        this.filedBy = filedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The keys out per keyholder, or zero when nobody held a key. */
    public double keysPerHolder() {
        if (keyholders == 0) {
            return 0.0;
        }
        return keysOut / (double) keyholders;
    }

    @Override
    public String toString() {
        return "<AnnualReturnRow " + year + ">";
    }
}
