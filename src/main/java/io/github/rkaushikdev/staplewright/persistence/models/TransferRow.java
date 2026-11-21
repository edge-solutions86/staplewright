package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Optional;

/**
 * One key moving from one pair of hands to another.
 *
 * <p>The handover is deliberately not part of the key row. The obvious design is a column on the
 * issued key saying who has it now, moved whenever a handover confirms. That is what the shop did in
 * the spreadsheet and it is exactly what lost the history: the column said rlaakso and nobody could
 * say when it stopped saying kmaki or who signed for the change. So a handover is its own row and the
 * key row is never touched by one. Working out who has a key means reading the key row and then
 * walking the confirmed handovers on top of it, which is slower and is the only version that can be
 * audited.
 *
 * <p>The load bearing rule is one open claim per key, because two people cannot each be about to
 * receive it. The schema holds it with a unique index over a column it works out for itself, which
 * carries the tag only while the claim is open; no row maps that column. The checks keep the state
 * to one the schema knows, the two people apart, a settled handover dated on or after its claim, and
 * a refusal explained.
 */
@Entity
@Table(name = "key_transfers", indexes = @Index(name = "ix_key_transfers_passed_to", columnList = "passed_to, state"))
public class TransferRow {

    private static final String CLAIMED = "claimed";

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "tag", nullable = false, length = Schema.SHORT)
    private String tag;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "tag",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_transfers_tag_issued_keys"))
    private IssuedKeyRow key;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol;

    @Column(name = "passed_by", nullable = false, length = Schema.SHORT)
    private String passedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "passed_by",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_transfers_passed_by_keyholders"))
    private KeyholderRow giver;

    @Column(name = "passed_to", nullable = false, length = Schema.SHORT)
    private String passedTo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "passed_to",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_key_transfers_passed_to_keyholders"))
    private KeyholderRow receiver;

    @Column(nullable = false)
    private LocalDate claimedOn;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = CLAIMED;

    private LocalDate settledOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected TransferRow() {}

    /** An open claim that a key passed from one keyholder to another. */
    public TransferRow(
            String number, String tag, String symbol, String passedBy, String passedTo, LocalDate claimedOn) {
        this.number = number;
        this.tag = tag;
        this.symbol = symbol;
        this.passedBy = passedBy;
        this.passedTo = passedTo;
        this.claimedOn = claimedOn;
    }

    public String getNumber() {
        return number;
    }

    public String getTag() {
        return tag;
    }

    /** The key, as loaded; it follows {@link #getTag()} once the row is read again. */
    public IssuedKeyRow getKey() {
        return key;
    }

    public String getSymbol() {
        return symbol;
    }

    /** The identifier of the keyholder handing the key over. */
    public String getPassedBy() {
        return passedBy;
    }

    /** The keyholder handing the key over, as loaded. */
    public KeyholderRow getGiver() {
        return giver;
    }

    /** The identifier of the keyholder receiving the key. */
    public String getPassedTo() {
        return passedTo;
    }

    /** The keyholder receiving the key, as loaded. */
    public KeyholderRow getReceiver() {
        return receiver;
    }

    public LocalDate getClaimedOn() {
        return claimedOn;
    }

    /** The stored value of the handover's state: claimed, confirmed, refused, lapsed or cancelled. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Optional<LocalDate> getSettledOn() {
        return Optional.ofNullable(settledOn);
    }

    public void setSettledOn(LocalDate settledOn) {
        this.settledOn = settledOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** Whether the claim is still waiting for the receiver to confirm it. */
    public boolean isOpen() {
        return CLAIMED.equals(state);
    }

    /** Whether the key actually changed hands, which only a confirmed handover means. */
    public boolean moved() {
        return "confirmed".equals(state);
    }

    @Override
    public String toString() {
        return "<TransferRow " + number + " " + state + ">";
    }
}
