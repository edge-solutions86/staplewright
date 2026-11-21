package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One warding section, hanging under the section above it in the same table.
 *
 * <p>That is how a multiplex family nests: the root of a family is the row with no parent. A code
 * appears once in a family, and a check keeps a section from being its own parent, which is the
 * only cycle a single row can make on its own.
 */
@Entity
@Table(
        name = "keyway_sections",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_keyway_sections_family_name_code",
                        columnNames = {"family_name", "code"}))
public class KeywaySectionRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String familyName;

    @Column(nullable = false, length = Schema.SHORT)
    private String code;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String manufacturer;

    @Column(nullable = false)
    private boolean restricted;

    private LocalDate patentExpires;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", foreignKey = @ForeignKey(name = "fk_keyway_sections_parent_id_keyway_sections"))
    private KeywaySectionRow parent;

    @OneToMany(mappedBy = "parent")
    private List<KeywaySectionRow> children = new ArrayList<>();

    @OneToMany(mappedBy = "section")
    private List<BlankRow> blanks = new ArrayList<>();

    protected KeywaySectionRow() {}

    /** An open section at the root of its family, until a parent is set. */
    public KeywaySectionRow(String familyName, String code, String manufacturer) {
        this.familyName = familyName;
        this.code = code;
        this.manufacturer = manufacturer;
    }

    public Integer getId() {
        return id;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public boolean isRestricted() {
        return restricted;
    }

    public void setRestricted(boolean restricted) {
        this.restricted = restricted;
    }

    public Optional<LocalDate> getPatentExpires() {
        return Optional.ofNullable(patentExpires);
    }

    public void setPatentExpires(LocalDate patentExpires) {
        this.patentExpires = patentExpires;
    }

    /** The section this one hangs under, or nothing for the root. */
    public Optional<KeywaySectionRow> getParent() {
        return Optional.ofNullable(parent);
    }

    /** The key of the parent row, or nothing for the root. */
    public Optional<Integer> getParentId() {
        return getParent().map(KeywaySectionRow::getId);
    }

    /** Hang this section under another, and list it among that section's children. */
    public void setParent(KeywaySectionRow parent) {
        if (this.parent != null) {
            this.parent.children.remove(this);
        }
        this.parent = parent;
        if (parent != null && !parent.children.contains(this)) {
            parent.children.add(this);
        }
    }

    /** The sections hung directly under this one. */
    public List<KeywaySectionRow> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** The blanks cut on this section. */
    public List<BlankRow> getBlanks() {
        return Collections.unmodifiableList(blanks);
    }

    void listBlank(BlankRow blank) {
        if (!blanks.contains(blank)) {
            blanks.add(blank);
        }
    }

    void forgetBlank(BlankRow blank) {
        blanks.remove(blank);
    }

    public boolean isRoot() {
        return parent == null;
    }

    @Override
    public String toString() {
        return "<KeywaySectionRow " + familyName + " " + code + ">";
    }
}
