package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * One trip out with a bag of pins.
 *
 * <p>An order may answer an incident or stand on its own, and it outlives the incident: deleting the
 * incident leaves the order with none. A scheduled order has a day. Its lines, one per cylinder, go
 * with it when it goes.
 */
@Entity
@Table(name = "work_orders")
public class WorkOrderRow {

    private static final Set<String> SETTLED = Set.of("installed", "skipped");

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "incident_number", length = Schema.SHORT)
    private String incidentNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "incident_number",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_work_orders_incident_number_incidents"))
    private IncidentRow incident;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "drafted";

    @Column(nullable = false, length = Schema.MEDIUM)
    private String technician = "";

    private LocalDate scheduledFor;

    private LocalDate closedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "workOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkOrderLineRow> lines = new ArrayList<>();

    protected WorkOrderRow() {}

    /** A drafted order for no incident, with nobody assigned and no day set. */
    public WorkOrderRow(String number) {
        this.number = number;
    }

    public String getNumber() {
        return number;
    }

    /** The number of the incident the order answers, or nothing for an order of its own. */
    public Optional<String> getIncidentNumber() {
        return Optional.ofNullable(incidentNumber);
    }

    public void setIncidentNumber(String incidentNumber) {
        this.incidentNumber = incidentNumber;
    }

    /** The incident, as loaded; it follows {@link #getIncidentNumber()} once the row is read again. */
    public Optional<IncidentRow> getIncident() {
        return Optional.ofNullable(incident);
    }

    /** The stored value of the order's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getTechnician() {
        return technician;
    }

    public void setTechnician(String technician) {
        this.technician = technician;
    }

    public Optional<LocalDate> getScheduledFor() {
        return Optional.ofNullable(scheduledFor);
    }

    public void setScheduledFor(LocalDate scheduledFor) {
        this.scheduledFor = scheduledFor;
    }

    public Optional<LocalDate> getClosedOn() {
        return Optional.ofNullable(closedOn);
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The cylinders on the order, as loaded. */
    public List<WorkOrderLineRow> getLines() {
        return Collections.unmodifiableList(lines);
    }

    void list(WorkOrderLineRow line) {
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }

    /** Take a cylinder off the order; the line is deleted with the next flush. */
    public void removeLine(WorkOrderLineRow line) {
        lines.remove(line);
    }

    /** The lines still to do: every line that has been neither installed nor skipped. */
    public List<WorkOrderLineRow> pending() {
        return lines.stream().filter(line -> !SETTLED.contains(line.getState())).toList();
    }

    @Override
    public String toString() {
        return "<WorkOrderRow " + number + ">";
    }
}
