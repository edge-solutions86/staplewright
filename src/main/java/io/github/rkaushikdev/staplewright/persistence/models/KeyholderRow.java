package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One person the shop will hand metal to.
 *
 * <p>A separated record carries the day the person left, which a check in the schema insists on.
 * The keys the person has signed for hang off the record, and so does their signature when they
 * may sign for other people's keys: deleting the person takes the signature with them.
 */
@Entity
@Table(name = "keyholders")
public class KeyholderRow {

    @Id
    @Column(length = Schema.SHORT)
    private String identifier;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String name;

    @Column(nullable = false, length = Schema.SHORT)
    private String affiliation;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String department;

    @Column(nullable = false, length = Schema.SHORT)
    private String standing = "active";

    private LocalDate endsOn;

    private LocalDate separatedOn;

    @OneToMany(mappedBy = "person", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<AuthorizerRow> signatures = new ArrayList<>();

    @OneToMany(mappedBy = "holder")
    private List<IssuedKeyRow> keys = new ArrayList<>();

    protected KeyholderRow() {}

    /** An active keyholder with no end date. */
    public KeyholderRow(String identifier, String name, String affiliation, String department) {
        this.identifier = identifier;
        this.name = name;
        this.affiliation = affiliation;
        this.department = department;
    }

    public String getIdentifier() {
        return identifier;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /** The stored value of the person's affiliation: faculty, staff, student, contractor. */
    public String getAffiliation() {
        return affiliation;
    }

    public void setAffiliation(String affiliation) {
        this.affiliation = affiliation;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    /** The stored value of the person's standing with the shop. */
    public String getStanding() {
        return standing;
    }

    public void setStanding(String standing) {
        this.standing = standing;
    }

    /** When the person's affiliation runs out, for anybody whose time on campus is fixed. */
    public Optional<LocalDate> getEndsOn() {
        return Optional.ofNullable(endsOn);
    }

    public void setEndsOn(LocalDate endsOn) {
        this.endsOn = endsOn;
    }

    public Optional<LocalDate> getSeparatedOn() {
        return Optional.ofNullable(separatedOn);
    }

    public void setSeparatedOn(LocalDate separatedOn) {
        this.separatedOn = separatedOn;
    }

    /** The person's signature, when they may sign for keys, as loaded. */
    public Optional<AuthorizerRow> getAuthorizer() {
        return signatures.stream().findFirst();
    }

    void list(AuthorizerRow authorizer) {
        if (!signatures.contains(authorizer)) {
            signatures.add(authorizer);
        }
    }

    /** Take the person's signature away; it is deleted with the next flush. */
    public void removeAuthorizer() {
        signatures.clear();
    }

    /** Every key the person has signed for, out or not, as loaded. */
    public List<IssuedKeyRow> getKeys() {
        return Collections.unmodifiableList(keys);
    }

    @Override
    public String toString() {
        return "<KeyholderRow " + identifier + ">";
    }
}
