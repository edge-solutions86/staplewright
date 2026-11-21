package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One keying system, which is the boundary for symbols and cuts.
 *
 * <p>A campus runs several keying systems at once. The 1971 dormitories are on cores and the 1953
 * academic buildings are on mortise cylinders, and the two share nothing but the shop that
 * maintains them, so the system is the boundary everything else sits inside. Its schedule entries
 * and keying groups go with it when it goes.
 */
@Entity
@Table(
        name = "keying_systems",
        uniqueConstraints = @UniqueConstraint(name = "uq_keying_systems_name", columnNames = "name"))
public class SystemRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "chart_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_keying_systems_chart_id_charts"))
    private ChartRow chart;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String familyName;

    @Column(nullable = false, length = Schema.SHORT)
    private String topSection;

    @Column(nullable = false)
    private int progressionStep = 2;

    @Column(nullable = false)
    private boolean archived;

    private LocalDate openedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String notes = "";

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ScheduleEntryRow> entries = new ArrayList<>();

    @OneToMany(mappedBy = "system", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<GroupRow> groups = new ArrayList<>();

    protected SystemRow() {}

    /** A system on a chart, stepping by two, open and unarchived. */
    public SystemRow(String name, ChartRow chart, String familyName, String topSection) {
        this.name = name;
        this.chart = chart;
        this.familyName = familyName;
        this.topSection = topSection;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ChartRow getChart() {
        return chart;
    }

    /** The key of the chart row. */
    public Integer getChartId() {
        return chart.getId();
    }

    public void setChart(ChartRow chart) {
        this.chart = chart;
    }

    public String getFamilyName() {
        return familyName;
    }

    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }

    public String getTopSection() {
        return topSection;
    }

    public void setTopSection(String topSection) {
        this.topSection = topSection;
    }

    public int getProgressionStep() {
        return progressionStep;
    }

    public void setProgressionStep(int progressionStep) {
        this.progressionStep = progressionStep;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Optional<LocalDate> getOpenedOn() {
        return Optional.ofNullable(openedOn);
    }

    public void setOpenedOn(LocalDate openedOn) {
        this.openedOn = openedOn;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    /** The schedule's lines, as loaded. */
    public List<ScheduleEntryRow> getEntries() {
        return Collections.unmodifiableList(entries);
    }

    /** The keying groups, as loaded. */
    public List<GroupRow> getGroups() {
        return Collections.unmodifiableList(groups);
    }

    void list(ScheduleEntryRow entry) {
        if (!entries.contains(entry)) {
            entries.add(entry);
        }
    }

    void list(GroupRow group) {
        if (!groups.contains(group)) {
            groups.add(group);
        }
    }

    /** Take a line out of the schedule; it is deleted with the next flush. */
    public void removeEntry(ScheduleEntryRow entry) {
        entries.remove(entry);
    }

    /** Take a group out of the system; it is deleted with the next flush. */
    public void removeGroup(GroupRow group) {
        groups.remove(group);
    }

    @Override
    public String toString() {
        return "<SystemRow " + name + ">";
    }
}
