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
import java.util.Arrays;
import java.util.List;
import org.hibernate.Hibernate;

/**
 * Somebody who may sign for keys, and how far the signature reaches.
 *
 * <p>The row is keyed by the keyholder's own identifier, so a person signs once, and it goes when
 * the person does. The buildings are stored comma separated; an authorizer with none signs
 * everywhere.
 */
@Entity
@Table(name = "authorizers")
public class AuthorizerRow {

    @Id
    @Column(name = "identifier", length = Schema.SHORT)
    private String identifier;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "identifier",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_authorizers_identifier_keyholders"))
    private KeyholderRow person;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String department;

    @Column(nullable = false, length = Schema.LONG)
    private String buildings = "";

    @Column(nullable = false)
    private boolean mayApproveMasters;

    protected AuthorizerRow() {}

    /** A signature for a keyholder by identifier, reaching every building, masters excluded. */
    public AuthorizerRow(String identifier, String department) {
        this.identifier = identifier;
        this.department = department;
    }

    /** A signature for a keyholder, listed on the keyholder when the keyholder is loaded. */
    public static AuthorizerRow of(KeyholderRow person, String department) {
        AuthorizerRow row = new AuthorizerRow(person.getIdentifier(), department);
        row.person = person;
        if (Hibernate.isInitialized(person)) {
            person.list(row);
        }
        return row;
    }

    public String getIdentifier() {
        return identifier;
    }

    /** The keyholder signing, as loaded; it follows {@link #getIdentifier()} once the row is read again. */
    public KeyholderRow getPerson() {
        return person;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    /** The building codes the signature reaches, comma separated as stored. */
    public String getBuildings() {
        return buildings;
    }

    public void setBuildings(String buildings) {
        this.buildings = buildings;
    }

    public boolean isMayApproveMasters() {
        return mayApproveMasters;
    }

    public void setMayApproveMasters(boolean mayApproveMasters) {
        this.mayApproveMasters = mayApproveMasters;
    }

    /** The building codes the signature reaches, in the order stored; empty for everywhere. */
    public List<String> buildingCodes() {
        if (buildings.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(buildings.split(",", -1))
                .filter(part -> !part.isEmpty())
                .toList();
    }

    @Override
    public String toString() {
        return "<AuthorizerRow " + identifier + ">";
    }
}
