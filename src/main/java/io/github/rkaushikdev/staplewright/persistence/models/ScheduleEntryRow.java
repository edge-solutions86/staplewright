package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.BittingColumn;
import io.github.rkaushikdev.staplewright.persistence.Schema;
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
import java.util.Optional;
import org.hibernate.Hibernate;

/**
 * One line of a keying schedule.
 *
 * <p>Two uniqueness rules carry most of the weight of the schema. A symbol appears once in a system,
 * which is what makes AB3 mean something. And a bitting appears once in a system, which is what
 * stops two rooms quietly sharing a key. The second is a plain unique constraint across the system
 * and the cut, and it lets a schedule hold any number of symbols that have not been cut yet, because
 * an empty cut is not equal to another empty cut.
 */
@Entity
@Table(
        name = "schedule_entries",
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uq_schedule_entries_system_id_symbol",
                    columnNames = {"system_id", "symbol"}),
            @UniqueConstraint(
                    name = "uq_schedule_entries_system_id_bitting",
                    columnNames = {"system_id", "bitting"})
        })
public class ScheduleEntryRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "system_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_schedule_entries_system_id_keying_systems"))
    private SystemRow system;

    @Column(nullable = false, length = SymbolColumn.WIDTH)
    private KeySymbol symbol;

    @Column(nullable = false, length = Schema.SHORT)
    private String section;

    @Column(length = BittingColumn.WIDTH)
    private Bitting bitting;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    protected ScheduleEntryRow() {}

    /** An uncut line of a system's schedule, listed on the system when the system's lines are loaded. */
    public static ScheduleEntryRow of(SystemRow system, KeySymbol symbol, String section) {
        ScheduleEntryRow row = new ScheduleEntryRow();
        row.system = system;
        row.symbol = symbol;
        row.section = section;
        if (Hibernate.isInitialized(system)) {
            system.list(row);
        }
        return row;
    }

    /** A line of a system's schedule cut to a bitting, listed on the system when the system's lines are loaded. */
    public static ScheduleEntryRow of(SystemRow system, KeySymbol symbol, String section, Bitting bitting) {
        ScheduleEntryRow row = of(system, symbol, section);
        row.bitting = bitting;
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

    public KeySymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(KeySymbol symbol) {
        this.symbol = symbol;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public Optional<Bitting> getBitting() {
        return Optional.ofNullable(bitting);
    }

    /** Cut the line, or clear its cut with nothing. */
    public void setBitting(Bitting bitting) {
        this.bitting = bitting;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public boolean isCut() {
        return bitting != null;
    }

    @Override
    public String toString() {
        return "<ScheduleEntryRow " + symbol + " " + (bitting == null ? "open" : bitting.render()) + ">";
    }
}
