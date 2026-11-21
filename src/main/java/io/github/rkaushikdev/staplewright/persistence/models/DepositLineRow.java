package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
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
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Set;

/**
 * One movement of deposit money against a key.
 *
 * <p>Deposit lines are append only by construction. There is no balance column anywhere: the
 * balance is the sum of the lines, and the day somebody adds a cached total is the day it disagrees
 * with the lines. Every line moves something, so its amount is positive and its kind says which way.
 */
@Entity
@Table(
        name = "deposit_lines",
        indexes = {
            @Index(name = "ix_deposit_lines_keyholder", columnList = "keyholder"),
            @Index(name = "ix_deposit_lines_tag", columnList = "tag")
        })
public class DepositLineRow {

    private static final Set<String> ADDING = Set.of("received", "charge");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "tag", nullable = false, length = Schema.SHORT)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tag",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_deposit_lines_tag_issued_keys"))
    private IssuedKeyRow key;

    @Column(nullable = false, length = Schema.SHORT)
    private String keyholder;

    @Column(nullable = false, length = Schema.SHORT)
    private String kind;

    @Column(nullable = false)
    private int cents;

    @Column(nullable = false)
    private LocalDate happenedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected DepositLineRow() {}

    /** A movement of money against a key, by its tag. */
    public DepositLineRow(String tag, String keyholder, String kind, int cents, LocalDate happenedOn) {
        this.tag = tag;
        this.keyholder = keyholder;
        this.kind = kind;
        this.cents = cents;
        this.happenedOn = happenedOn;
    }

    public Integer getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    /** The key, as loaded; it follows {@link #getTag()} once the row is read again. */
    public IssuedKeyRow getKey() {
        return key;
    }

    public String getKeyholder() {
        return keyholder;
    }

    /** The stored value of the line's kind: charge, refund, forfeit and so on. */
    public String getKind() {
        return kind;
    }

    public int getCents() {
        return cents;
    }

    public LocalDate getHappenedOn() {
        return happenedOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** What the line does to the money held: a charge or a receipt adds, anything else takes away. */
    public int signedCents() {
        return ADDING.contains(kind) ? cents : -cents;
    }

    @Override
    public String toString() {
        return "<DepositLineRow " + tag + " " + kind + ">";
    }
}
