package io.github.rkaushikdev.staplewright.persistence.models;

import io.github.rkaushikdev.staplewright.persistence.DepthListColumn;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.List;

/**
 * One manufacturer depth and space chart, as stored.
 *
 * <p>The manufacturer facts change when the shop changes supplier or a factory withdraws a depth,
 * which happens about as often as a new building goes up, so this is an ordinary table with ordinary
 * constraints and no history behind it. A family appears once per manufacturer, and the chart's
 * chambers, increment and macs are all positive.
 */
@Entity
@Table(
        name = "charts",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_charts_manufacturer_family",
                        columnNames = {"manufacturer", "family"}))
public class ChartRow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String manufacturer;

    @Column(nullable = false, length = Schema.MEDIUM)
    private String family;

    @Column(nullable = false)
    private int chambers;

    @Convert(converter = DepthListColumn.class)
    @Column(nullable = false, length = DepthListColumn.WIDTH)
    private List<Integer> depths;

    @Column(nullable = false)
    private int rootMils;

    @Column(nullable = false)
    private int incrementMils;

    @Column(nullable = false)
    private int firstSpaceMils;

    @Column(nullable = false)
    private int spaceIncrementMils;

    @Column(nullable = false)
    private int macs;

    @Column(nullable = false)
    private boolean gaugedFromTip;

    @Column(nullable = false)
    private int depthToleranceMils = 2;

    @Column(nullable = false, length = Schema.LONG)
    private String notes = "";

    protected ChartRow() {}

    /** A chart gauged from the shoulder, with a two mil tolerance and no notes until set. */
    public ChartRow(
            String manufacturer,
            String family,
            int chambers,
            List<Integer> depths,
            int rootMils,
            int incrementMils,
            int firstSpaceMils,
            int spaceIncrementMils,
            int macs) {
        this.manufacturer = manufacturer;
        this.family = family;
        this.chambers = chambers;
        this.depths = List.copyOf(depths);
        this.rootMils = rootMils;
        this.incrementMils = incrementMils;
        this.firstSpaceMils = firstSpaceMils;
        this.spaceIncrementMils = spaceIncrementMils;
        this.macs = macs;
    }

    public Integer getId() {
        return id;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getFamily() {
        return family;
    }

    public void setFamily(String family) {
        this.family = family;
    }

    public int getChambers() {
        return chambers;
    }

    public void setChambers(int chambers) {
        this.chambers = chambers;
    }

    public List<Integer> getDepths() {
        return depths;
    }

    public void setDepths(List<Integer> depths) {
        this.depths = List.copyOf(depths);
    }

    public int getRootMils() {
        return rootMils;
    }

    public void setRootMils(int rootMils) {
        this.rootMils = rootMils;
    }

    public int getIncrementMils() {
        return incrementMils;
    }

    public void setIncrementMils(int incrementMils) {
        this.incrementMils = incrementMils;
    }

    public int getFirstSpaceMils() {
        return firstSpaceMils;
    }

    public void setFirstSpaceMils(int firstSpaceMils) {
        this.firstSpaceMils = firstSpaceMils;
    }

    public int getSpaceIncrementMils() {
        return spaceIncrementMils;
    }

    public void setSpaceIncrementMils(int spaceIncrementMils) {
        this.spaceIncrementMils = spaceIncrementMils;
    }

    public int getMacs() {
        return macs;
    }

    public void setMacs(int macs) {
        this.macs = macs;
    }

    public boolean isGaugedFromTip() {
        return gaugedFromTip;
    }

    public void setGaugedFromTip(boolean gaugedFromTip) {
        this.gaugedFromTip = gaugedFromTip;
    }

    public int getDepthToleranceMils() {
        return depthToleranceMils;
    }

    public void setDepthToleranceMils(int depthToleranceMils) {
        this.depthToleranceMils = depthToleranceMils;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    @Override
    public String toString() {
        return "<ChartRow " + manufacturer + " " + family + ">";
    }
}
