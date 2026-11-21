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
import jakarta.persistence.UniqueConstraint;
import java.util.Optional;

/**
 * One interchangeable core, and the control key that pulls it.
 *
 * <p>The only thing that joins the core register to the rest of the schema is the opening a core
 * happens to be sitting in. That join is optional, because a core on a shelf is still a core
 * somebody could put in a housing tomorrow. One core per housing of an opening.
 */
@Entity
@Table(
        name = "interchangeable_cores",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_interchangeable_cores_installed_at_housing",
                        columnNames = {"installed_at", "housing"}),
        indexes = @Index(name = "ix_interchangeable_cores_control", columnList = "control_code"))
public class CoreRow {

    @Id
    @Column(length = Schema.SHORT)
    private String serial;

    @Column(nullable = false, length = Schema.SHORT)
    private String format;

    @Column(name = "control_code", nullable = false, length = Schema.SHORT)
    private String controlCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "control_code",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_interchangeable_cores_control_code_control_keys"))
    private ControlKeyRow control;

    @Column(nullable = false, length = Schema.SHORT)
    private String housing;

    @Column(name = "installed_at", length = Schema.SHORT)
    private String installedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "installed_at",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_interchangeable_cores_installed_at_openings"))
    private OpeningRow opening;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected CoreRow() {}

    /** A core on the shelf, pulled by a control key given by its code. */
    public CoreRow(String serial, String format, String controlCode, String housing) {
        this.serial = serial;
        this.format = format;
        this.controlCode = controlCode;
        this.housing = housing;
    }

    public String getSerial() {
        return serial;
    }

    /** The stored value of the core's format. */
    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getControlCode() {
        return controlCode;
    }

    public void setControlCode(String controlCode) {
        this.controlCode = controlCode;
    }

    /** The control key, as loaded; it follows {@link #getControlCode()} once the row is read again. */
    public ControlKeyRow getControl() {
        return control;
    }

    /** The housing the core sits in, or will sit in. */
    public String getHousing() {
        return housing;
    }

    public void setHousing(String housing) {
        this.housing = housing;
    }

    /** The number of the opening the core is in, or nothing while it is on the shelf. */
    public Optional<String> getInstalledAt() {
        return Optional.ofNullable(installedAt);
    }

    /** Put the core in an opening by its number, or on the shelf with nothing. */
    public void setInstalledAt(String installedAt) {
        this.installedAt = installedAt;
    }

    /** The opening, as loaded; it follows {@link #getInstalledAt()} once the row is read again. */
    public Optional<OpeningRow> getOpening() {
        return Optional.ofNullable(opening);
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isInstalled() {
        return installedAt != null;
    }

    @Override
    public String toString() {
        return "<CoreRow " + serial + ">";
    }
}
