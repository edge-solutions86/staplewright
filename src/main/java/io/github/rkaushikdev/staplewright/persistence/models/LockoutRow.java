package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;

/**
 * One lockout call, from the telephone ringing to the door opening.
 *
 * <p>A lockout is about a door and a person, so it hangs off the opening and never off the cylinder
 * that happened to be in the door at the time: tying it to the cylinder would mean the record moved
 * when the cylinder did, which is exactly wrong. Nobody attends before the call, and an attended
 * call names its technician.
 */
@Entity
@Table(name = "lockouts")
public class LockoutRow {

    private static final Set<String> OPEN_STATES = Set.of("logged", "attended");

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "opening_number", nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "opening_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_lockouts_opening_number_openings"))
    private OpeningRow opening;

    @Column(nullable = false, length = Schema.SHORT)
    private String keyholder;

    @Column(nullable = false)
    private LocalDateTime calledAt;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = "logged";

    private LocalDateTime attendedAt;

    private LocalDateTime closedAt;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String technician = "";

    @Column(nullable = false, length = Schema.LONG)
    private String identifiedBy = "not identified";

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected LockoutRow() {}

    /** A call just logged, attended by nobody yet and with the caller not identified. */
    public LockoutRow(String number, String openingNumber, String keyholder, LocalDateTime calledAt) {
        this.number = number;
        this.openingNumber = openingNumber;
        this.keyholder = keyholder;
        this.calledAt = calledAt;
    }

    public String getNumber() {
        return number;
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

    /** The identifier of the person locked out. */
    public String getKeyholder() {
        return keyholder;
    }

    public void setKeyholder(String keyholder) {
        this.keyholder = keyholder;
    }

    public LocalDateTime getCalledAt() {
        return calledAt;
    }

    public void setCalledAt(LocalDateTime calledAt) {
        this.calledAt = calledAt;
    }

    /** The stored value of the call's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Optional<LocalDateTime> getAttendedAt() {
        return Optional.ofNullable(attendedAt);
    }

    public void setAttendedAt(LocalDateTime attendedAt) {
        this.attendedAt = attendedAt;
    }

    public Optional<LocalDateTime> getClosedAt() {
        return Optional.ofNullable(closedAt);
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getTechnician() {
        return technician;
    }

    public void setTechnician(String technician) {
        this.technician = technician;
    }

    /** How the caller proved who they were. */
    public String getIdentifiedBy() {
        return identifiedBy;
    }

    public void setIdentifiedBy(String identifiedBy) {
        this.identifiedBy = identifiedBy;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** Whether the call is still logged or attended, rather than finished one way or another. */
    public boolean isOpen() {
        return OPEN_STATES.contains(state);
    }

    @Override
    public String toString() {
        return "<LockoutRow " + number + ">";
    }
}
