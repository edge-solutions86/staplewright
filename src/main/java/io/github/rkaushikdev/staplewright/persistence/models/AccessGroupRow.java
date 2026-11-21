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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A set of people who need the same doors, named once. Its requirements, the cells of the access
 * matrix, go with it when it goes.
 */
@Entity
@Table(
        name = "access_groups",
        uniqueConstraints = @UniqueConstraint(name = "uq_access_groups_name", columnNames = "name"))
public class AccessGroupRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String name;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String department = "";

    @Column(nullable = false, length = Schema.LONG)
    private String description = "";

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AccessRequirementRow> requirements = new ArrayList<>();

    protected AccessGroupRow() {}

    /** A group in no department, needing no doors yet. */
    public AccessGroupRow(String name) {
        this.name = name;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** The doors the group needs, as loaded. */
    public List<AccessRequirementRow> getRequirements() {
        return Collections.unmodifiableList(requirements);
    }

    void list(AccessRequirementRow requirement) {
        if (!requirements.contains(requirement)) {
            requirements.add(requirement);
        }
    }

    /** Take a door off the group; the requirement is deleted with the next flush. */
    public void removeRequirement(AccessRequirementRow requirement) {
        requirements.remove(requirement);
    }

    @Override
    public String toString() {
        return "<AccessGroupRow " + name + ">";
    }
}
