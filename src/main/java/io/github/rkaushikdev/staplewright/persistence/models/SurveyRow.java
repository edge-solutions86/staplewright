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

/** One hardware survey: a walk round a building on a day, once a day. Its lines go with it. */
@Entity
@Table(
        name = "surveys",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_surveys_building_code_walked_on",
                        columnNames = {"building_code", "walked_on"}))
public class SurveyRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "building_code", nullable = false, length = Schema.SHORT)
    private String buildingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "building_code",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_surveys_building_code_buildings"))
    private BuildingRow building;

    @Column(nullable = false)
    private LocalDate walkedOn;

    @Column(nullable = false, length = Schema.LONG)
    private String note = "";

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SurveyLineRow> lines = new ArrayList<>();

    protected SurveyRow() {}

    /** A walk with no openings recorded yet. */
    public SurveyRow(String buildingCode, LocalDate walkedOn) {
        this.buildingCode = buildingCode;
        this.walkedOn = walkedOn;
    }

    public Integer getId() {
        return id;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    /** The building, as loaded; it follows {@link #getBuildingCode()} once the row is read again. */
    public BuildingRow getBuilding() {
        return building;
    }

    public LocalDate getWalkedOn() {
        return walkedOn;
    }

    public void setWalkedOn(LocalDate walkedOn) {
        this.walkedOn = walkedOn;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    /** The openings as they were found, as loaded. */
    public List<SurveyLineRow> getLines() {
        return Collections.unmodifiableList(lines);
    }

    void list(SurveyLineRow line) {
        if (!lines.contains(line)) {
            lines.add(line);
        }
    }

    /** Take an opening off the survey; the line is deleted with the next flush. */
    public void removeLine(SurveyLineRow line) {
        lines.remove(line);
    }

    @Override
    public String toString() {
        return "<SurveyRow " + buildingCode + " " + walkedOn + ">";
    }
}
