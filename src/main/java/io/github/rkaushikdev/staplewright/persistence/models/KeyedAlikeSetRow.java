package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A run of openings that carry one key between them, named once. Its members go with it. */
@Entity
@Table(name = "keyed_alike_sets")
public class KeyedAlikeSetRow {

    @Id
    @Column(length = Schema.MEDIUM)
    private String name;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "alike", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<KeyedAlikeMemberRow> members = new ArrayList<>();

    protected KeyedAlikeSetRow() {}

    /** An empty set. */
    public KeyedAlikeSetRow(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The set's members, as loaded. */
    public List<KeyedAlikeMemberRow> getMembers() {
        return Collections.unmodifiableList(members);
    }

    void list(KeyedAlikeMemberRow member) {
        if (!members.contains(member)) {
            members.add(member);
        }
    }

    /** Take a door out of the set; the member is deleted with the next flush. */
    public void removeMember(KeyedAlikeMemberRow member) {
        members.remove(member);
    }

    /** The numbers of the openings in the set, sorted. */
    public List<String> openings() {
        return members.stream()
                .map(KeyedAlikeMemberRow::getOpeningNumber)
                .sorted()
                .toList();
    }

    @Override
    public String toString() {
        return "<KeyedAlikeSetRow " + name + ">";
    }
}
