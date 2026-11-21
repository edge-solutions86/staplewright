package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.hibernate.Hibernate;

/** A set of symbols one cylinder answers to, named once inside its system. */
@Entity
@Table(
        name = "keying_groups",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_keying_groups_system_id_name",
                        columnNames = {"system_id", "name"}),
        indexes = @Index(name = "ix_keying_groups_name", columnList = "name"))
public class GroupRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "system_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_keying_groups_system_id_keying_systems"))
    private SystemRow system;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String name;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupMemberRow> members = new ArrayList<>();

    protected GroupRow() {}

    /** An empty group in a system, listed on the system. */
    public static GroupRow of(SystemRow system, String name) {
        GroupRow row = new GroupRow();
        row.system = system;
        row.name = name;
        if (Hibernate.isInitialized(system)) {
            system.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public SystemRow getSystem() {
        return system;
    }

    /** The key of the system row. */
    public Integer getSystemId() {
        return system.getId();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The group's members, as loaded. */
    public List<GroupMemberRow> getMembers() {
        return Collections.unmodifiableList(members);
    }

    void list(GroupMemberRow member) {
        if (!members.contains(member)) {
            members.add(member);
        }
    }

    /** Take a member out of the group; it is deleted with the next flush. */
    public void removeMember(GroupMemberRow member) {
        members.remove(member);
    }

    /** The members' symbols, in the order they were loaded. */
    public List<KeySymbol> symbols() {
        return members.stream().map(GroupMemberRow::getSymbol).toList();
    }

    @Override
    public String toString() {
        return "<GroupRow " + name + ">";
    }
}
