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

/**
 * One door in one keyed alike set, and in no other.
 *
 * <p>The unique constraint is on the opening alone rather than on the pair. That is the domain rule
 * written into the schema: a door belongs to one set or to none, and the second set that tries to
 * claim it is refused by the database rather than by whichever service happened to check first.
 */
@Entity
@Table(
        name = "keyed_alike_members",
        uniqueConstraints =
                @UniqueConstraint(name = "uq_keyed_alike_members_opening_number", columnNames = "opening_number"))
public class KeyedAlikeMemberRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "set_name", nullable = false, length = Schema.MEDIUM)
    private String setName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "set_name",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_keyed_alike_members_set_name_keyed_alike_sets"))
    private KeyedAlikeSetRow alike;

    @Column(name = "opening_number", nullable = false, length = Schema.SHORT)
    private String openingNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "opening_number",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_keyed_alike_members_opening_number_openings"))
    private OpeningRow opening;

    protected KeyedAlikeMemberRow() {}

    /** A door in a set, both by name. */
    public KeyedAlikeMemberRow(String setName, String openingNumber) {
        this.setName = setName;
        this.openingNumber = openingNumber;
    }

    /** A door in a set, listed on the set when the set's members are loaded. */
    public static KeyedAlikeMemberRow of(KeyedAlikeSetRow alike, String openingNumber) {
        KeyedAlikeMemberRow row = new KeyedAlikeMemberRow(alike.getName(), openingNumber);
        row.alike = alike;
        if (Hibernate.isInitialized(alike)) {
            alike.list(row);
        }
        return row;
    }

    public Integer getId() {
        return id;
    }

    public String getSetName() {
        return setName;
    }

    /** The set, as loaded; it follows {@link #getSetName()} once the row is read again. */
    public KeyedAlikeSetRow getAlike() {
        return alike;
    }

    public String getOpeningNumber() {
        return openingNumber;
    }

    /** The opening, as loaded; it follows {@link #getOpeningNumber()} once the row is read again. */
    public OpeningRow getOpening() {
        return opening;
    }

    @Override
    public String toString() {
        return "<KeyedAlikeMemberRow " + openingNumber + ">";
    }
}
