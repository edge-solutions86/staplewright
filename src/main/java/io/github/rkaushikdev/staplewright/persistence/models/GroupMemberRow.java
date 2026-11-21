package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.SymbolColumn;
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

/** One symbol in one keying group, once. */
@Entity
@Table(
        name = "keying_group_members",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_keying_group_members_group_id_symbol",
                        columnNames = {"group_id", "symbol"}))
public class GroupMemberRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "group_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_keying_group_members_group_id_keying_groups"))
    private GroupRow group;

    @Column(nullable = false, length = SymbolColumn.WIDTH)
    private KeySymbol symbol;

    protected GroupMemberRow() {}

    /** A member of a group, listed on the group. */
    public static GroupMemberRow of(GroupRow group, KeySymbol symbol) {
        GroupMemberRow row = new GroupMemberRow();
        row.group = group;
        row.symbol = symbol;
        if (Hibernate.isInitialized(group)) {
            group.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public GroupRow getGroup() {
        return group;
    }

    /** The key of the group row. */
    public Integer getGroupId() {
        return group.getId();
    }

    public KeySymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(KeySymbol symbol) {
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return "<GroupMemberRow " + symbol + ">";
    }
}
