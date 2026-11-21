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
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One door, hatch, gate or cabinet.
 *
 * <p>A room number appears once on a floor of a building. The opening is the thing that stays: the
 * cylinders sitting in it point at it, rather than the opening holding a list of them, because a
 * cylinder is the thing that moves.
 */
@Entity
@Table(
        name = "openings",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_openings_building_code_room_floor",
                        columnNames = {"building_code", "room", "floor"}),
        indexes = {
            @Index(name = "ix_openings_building", columnList = "building_code"),
            @Index(name = "ix_openings_function", columnList = "function")
        })
public class OpeningRow {

    @Id
    @Column(length = Schema.SHORT)
    private String number;

    @Column(name = "building_code", nullable = false, length = Schema.SHORT)
    private String buildingCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "building_code",
            nullable = false,
            insertable = false,
            updatable = false,
            foreignKey = @ForeignKey(name = "fk_openings_building_code_buildings"))
    private BuildingRow building;

    @Column(nullable = false, length = Schema.SHORT)
    private String floor;

    @Column(nullable = false, length = Schema.SHORT)
    private String room;

    @Column(nullable = false, length = Schema.SHORT)
    private String function;

    @Column(nullable = false)
    private boolean exterior;

    @Column(nullable = false, length = Schema.LONG)
    private String description = "";

    @OneToMany(mappedBy = "opening")
    private List<CylinderRow> cylinders = new ArrayList<>();

    protected OpeningRow() {}

    /** An interior opening with no description until set. */
    public OpeningRow(String number, String buildingCode, String floor, String room, String function) {
        this.number = number;
        this.buildingCode = buildingCode;
        this.floor = floor;
        this.room = room;
        this.function = function;
    }

    public String getNumber() {
        return number;
    }

    public String getBuildingCode() {
        return buildingCode;
    }

    public void setBuildingCode(String buildingCode) {
        this.buildingCode = buildingCode;
    }

    /** The building, as loaded; it follows {@link #getBuildingCode()} once the row is read again. */
    public BuildingRow getBuilding() {
        return building;
    }

    public String getFloor() {
        return floor;
    }

    public void setFloor(String floor) {
        this.floor = floor;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    /** What the opening is for, as the stored value of a door function. */
    public String getFunction() {
        return function;
    }

    public void setFunction(String function) {
        this.function = function;
    }

    public boolean isExterior() {
        return exterior;
    }

    public void setExterior(boolean exterior) {
        this.exterior = exterior;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** The cylinders installed in the opening, as loaded. */
    public List<CylinderRow> getCylinders() {
        return Collections.unmodifiableList(cylinders);
    }

    @Override
    public String toString() {
        return "<OpeningRow " + number + ">";
    }
}
