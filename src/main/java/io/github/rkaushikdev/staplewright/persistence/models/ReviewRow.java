package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One department, one year, one list of keys to account for.
 *
 * <p>A department is reviewed once per opening date, a review is due on or after the day it opens,
 * and a closed review says when it closed. Everything else about a review is policy and lives in
 * the code. Its lines go with it when it goes.
 */
@Entity
@Table(
        name = "access_reviews",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_access_reviews_department_opened_on",
                        columnNames = {"department", "opened_on"}))
public class ReviewRow {

    private static final String OPEN = "open";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String department;

    @Column(nullable = false)
    private LocalDate openedOn;

    @Column(nullable = false)
    private LocalDate dueOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = OPEN;

    private LocalDate closedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "review", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ReviewLineRow> lines = new ArrayList<>();

    protected ReviewRow() {}

    /** An open review with no lines yet. */
    public ReviewRow(String department, LocalDate openedOn, LocalDate dueOn) {
        this.department = department;
        this.openedOn = openedOn;
        this.dueOn = dueOn;
    }

    public Integer getId() {
        return id;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public LocalDate getOpenedOn() {
        return openedOn;
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public LocalDate getDueOn() {
        return dueOn;
    }

    public void setDueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }

    /** The stored value of the review's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
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

    /** The keys on the review, as loaded. */
    public List<ReviewLineRow> getLines() {
        return Collections.unmodifiableList(lines);
    }

    void list(ReviewLineRow line) {
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }

    /** Take a key off the review; the line is deleted with the next flush. */
    public void removeLine(ReviewLineRow line) {
        lines.remove(line);
    }

    public boolean isOpen() {
        return OPEN.equals(state);
    }

    @Override
    public String toString() {
        return "<ReviewRow " + department + " " + openedOn + ">";
    }
}
