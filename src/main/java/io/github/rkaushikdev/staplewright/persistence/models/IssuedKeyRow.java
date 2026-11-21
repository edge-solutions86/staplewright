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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One stamped key and whoever signed for it.
 *
 * <p>The key ledger is the table the shop would rescue from a fire. Everything else can be rebuilt
 * from a survey and a filing cabinet, but who is holding what only exists here, and it exists as a
 * row per stamped key rather than a count, because a count cannot be handed back.
 *
 * <p>The deposit is never negative, and a key is never due back before it went out.
 */
@Entity
@Table(
        name = "issued_keys",
        indexes = {
            // Every counter question starts with one of these: who has what, and what is late.
            @Index(name = "ix_issued_keys_holder_state", columnList = "keyholder, state"),
            @Index(name = "ix_issued_keys_state_due", columnList = "state, due_on"),
            @Index(name = "ix_issued_keys_symbol_state", columnList = "symbol, state")
        })
public class IssuedKeyRow {

    private static final String OUT = "out";

    @Id
    @Column(length = Schema.SHORT)
    private String tag;

    @Column(nullable = false, length = Schema.SHORT)
    private String symbol;

    @Column(name = "keyholder", nullable = false, length = Schema.SHORT)
    private String keyholder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "keyholder",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_issued_keys_keyholder_keyholders"))
    private KeyholderRow holder;

    @Column(nullable = false)
    private LocalDate issuedOn;

    private LocalDate dueOn;

    @Column(nullable = false)
    private int depositCents;

    @Column(nullable = false, length = Schema.SHORT)
    private String state = OUT;

    private LocalDate closedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "key")
    private List<DepositLineRow> deposits = new ArrayList<>();

    protected IssuedKeyRow() {}

    /** A key out with its holder, with no deposit and no due date until set. */
    public IssuedKeyRow(String tag, String symbol, String keyholder, LocalDate issuedOn) {
        this.tag = tag;
        this.symbol = symbol;
        this.keyholder = keyholder;
        this.issuedOn = issuedOn;
    }

    public String getTag() {
        return tag;
    }

    /** The symbol stamped on the key, as printed. */
    public String getSymbol() {
        return symbol;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    /** The identifier of the person who signed for the key. */
    public String getKeyholder() {
        return keyholder;
    }

    public void setKeyholder(String keyholder) {
        this.keyholder = keyholder;
    }

    /** The person who signed, as loaded; it follows {@link #getKeyholder()} once the row is read again. */
    public KeyholderRow getHolder() {
        return holder;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public Optional<LocalDate> getDueOn() {
        return Optional.ofNullable(dueOn);
    }

    public void setDueOn(LocalDate dueOn) {
        this.dueOn = dueOn;
    }

    public int getDepositCents() {
        return depositCents;
    }

    public void setDepositCents(int depositCents) {
        this.depositCents = depositCents;
    }

    /** The stored value of the key's state. */
    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Optional<LocalDate> getClosedOn() {
        return Optional.ofNullable(closedOn);
    }

    public void setClosedOn(LocalDate closedOn) {
        this.closedOn = closedOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The deposit money moved against this key, as loaded. */
    public List<DepositLineRow> getDeposits() {
        return Collections.unmodifiableList(deposits);
    }

    public boolean isOut() {
        return OUT.equals(state);
    }

    @Override
    public String toString() {
        return "<IssuedKeyRow " + tag + ">";
    }
}
