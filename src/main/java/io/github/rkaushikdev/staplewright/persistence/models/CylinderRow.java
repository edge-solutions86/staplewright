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
 * One serial numbered cylinder or core, wherever it is.
 *
 * <p>The installation lives here: a cylinder points at an opening, so it has at most one opening
 * because it has one column. Two cylinders cannot sit in the same position on one door, which is a
 * unique constraint across the opening and the position. Both are empty while the cylinder is on
 * the shelf, and an empty value is not equal to another, so any number of cylinders can sit in the
 * drawer at once. A check keeps the two set together or empty together.
 */
@Entity
@Table(
        name = "cylinders",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_cylinders_installed_at_position",
                        columnNames = {"installed_at", "position"}),
        indexes = {
            // The coverage map is built from these two joins on every report.
            @Index(name = "ix_cylinders_installed_at", columnList = "installed_at"),
            @Index(name = "ix_cylinders_group_id", columnList = "group_id")
        })
public class CylinderRow {

    @Id
    @Column(length = Schema.SHORT)
    private String serial;

    @Column(nullable = false, length = Schema.SHORT)
    private String format;

    @Column(nullable = false, length = Schema.SHORT)
    private String section;

    @Column(nullable = false)
    private int chambers;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String manufacturer = "";

    @Column(nullable = false)
    private boolean spooled;

    @Column(name = "installed_at", length = Schema.SHORT)
    private String installedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "installed_at",
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_cylinders_installed_at_openings"))
    private OpeningRow opening;

    @Column(length = Schema.SHORT)
    private String position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", foreignKey = @ForeignKey(name = "fk_cylinders_group_id_keying_groups"))
    private GroupRow group;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected CylinderRow() {}

    /** A cylinder on the shelf, unspooled, answering to no keying group until set. */
    public CylinderRow(String serial, String format, String section, int chambers) {
        this.serial = serial;
        this.format = format;
        this.section = section;
        this.chambers = chambers;
    }

    public String getSerial() {
        return serial;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public int getChambers() {
        return chambers;
    }

    public void setChambers(int chambers) {
        this.chambers = chambers;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public boolean isSpooled() {
        return spooled;
    }

    public void setSpooled(boolean spooled) {
        this.spooled = spooled;
    }

    /** The number of the opening the cylinder is in, or nothing while it is on the shelf. */
    public Optional<String> getInstalledAt() {
        return Optional.ofNullable(installedAt);
    }

    /** Put the cylinder in an opening by its number, or on the shelf with nothing. */
    public void setInstalledAt(String installedAt) {
        this.installedAt = installedAt;
    }

    /** The opening, as loaded; it follows {@link #getInstalledAt()} once the row is read again. */
    public Optional<OpeningRow> getOpening() {
        return Optional.ofNullable(opening);
    }

    /** Where on the door the cylinder sits, or nothing while it is on the shelf. */
    public Optional<String> getPosition() {
        return Optional.ofNullable(position);
    }

    public void setPosition(String position) {
        this.position = position;
    }

    /** The keying group the cylinder answers to, or nothing when nobody wrote it down. */
    public Optional<GroupRow> getGroup() {
        return Optional.ofNullable(group);
    }

    /** The key of the group row, or nothing. */
    public Optional<Integer> getGroupId() {
        return getGroup().map(GroupRow::getId);
    }

    /** Set the keying group, or clear it with nothing. */
    public void setGroup(GroupRow group) {
        this.group = group;
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
        return "<CylinderRow " + serial + ">";
    }
}
