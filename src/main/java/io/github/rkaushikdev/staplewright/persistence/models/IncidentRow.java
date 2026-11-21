package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
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
 * One missing key, written down the day it is reported. A theft is always written up: a check in
 * the schema refuses a stolen key with no note. The work orders it causes hang off it.
 */
@Entity
@Table(name = "incidents")
public class IncidentRow {

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(nullable = false, length = Schema.SHORT)
    private String tag;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String cause;

    @Column(nullable = false)
    private LocalDate reportedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String keyholder = "";

    @Column(nullable = false, length = Schema.SHORT)
    private String severity = "routine";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "incident")
    private List<WorkOrderRow> orders = new ArrayList<>();

    protected IncidentRow() {}

    /** A routine incident with nobody named as the holder until set. */
    public IncidentRow(String number, String tag, String symbol, String cause, LocalDate reportedOn) {
        this.number = number;
        this.tag = tag;
        this.symbol = symbol;
        this.cause = cause;
        this.reportedOn = reportedOn;
    }

    public String getNumber() {
        return number;
    }

    /** The tag of the key that went missing. */
    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /** The stored value of how the key went missing. */
    public String getCause() {
        return cause;
    }

    public void setCause(String cause) {
        this.cause = cause;
    }

    public LocalDate getReportedOn() {
        return reportedOn;
    }

    public void setReportedOn(LocalDate reportedOn) {
        this.reportedOn = reportedOn;
    }

    public String getKeyholder() {
        return keyholder;
    }

    public void setKeyholder(String keyholder) {
        this.keyholder = keyholder;
    }

    /** The stored value of how serious the loss is. */
    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The work orders raised against the incident, as loaded. */
    public List<WorkOrderRow> getOrders() {
        return Collections.unmodifiableList(orders);
    }

    @Override
    public String toString() {
        return "<IncidentRow " + number + ">";
    }
}
