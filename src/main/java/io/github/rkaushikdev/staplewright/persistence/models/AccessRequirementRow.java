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
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.Hibernate;

/** One group needing one door, which is a cell of the access matrix. A group asks for a door once. */
@Entity
@Table(
        name = "access_requirements",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_access_requirements_group_id_opening_number",
                        columnNames = {"group_id", "opening_number"}))
public class AccessRequirementRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_access_requirements_group_id_access_groups"))
    private AccessGroupRow group;

    @Column(name = "opening_number", nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "opening_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_access_requirements_opening_number_openings"))
    private OpeningRow opening;

    protected AccessRequirementRow() {}

    /** A door a group needs, listed on the group when the group's requirements are loaded. */
    public static AccessRequirementRow of(AccessGroupRow group, String openingNumber) {
        AccessRequirementRow row = new AccessRequirementRow();
        row.group = group;
        row.openingNumber = openingNumber;
        if (Hibernate.isInitialized(group)) {
            group.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public AccessGroupRow getGroup() {
        return group;
    }

    /** The key of the group row. */
    public Integer getGroupId() {
        return group.getId();
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

    @Override
    public String toString() {
        return "<AccessRequirementRow " + openingNumber + ">";
    }
}
