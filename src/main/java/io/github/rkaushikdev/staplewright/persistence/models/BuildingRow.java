package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * One building on the campus.
 *
 * <p>A year built, when the shop knows it, is after 1850: the town was not there before then, and a
 * check in the schema refuses anything earlier.
 */
@Entity
@Table(name = "buildings")
public class BuildingRow {

    @Id
    @Column(length = Schema.SHORT)
    private String code;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String name;

    private Integer yearBuilt;

    @OneToMany(mappedBy = "building")
    private List<OpeningRow> openings = new ArrayList<>();

    protected BuildingRow() {}

    /** A building with no year built until one is set. */
    public BuildingRow(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Optional<Integer> getYearBuilt() {
        return Optional.ofNullable(yearBuilt);
    }

    /** Set the year built, or clear it with nothing. */
    public void setYearBuilt(Integer yearBuilt) {
        this.yearBuilt = yearBuilt;
    }

    /** The openings in the building, as loaded. */
    public List<OpeningRow> getOpenings() {
        return Collections.unmodifiableList(openings);
    }

    @Override
    public String toString() {
        return "<BuildingRow " + code + ">";
    }
}
