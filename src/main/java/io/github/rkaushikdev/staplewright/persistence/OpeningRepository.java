package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import java.util.List;

/** Openings by number, and the buildings they are in. */
public final class OpeningRepository extends Repository<OpeningRow> {

    public OpeningRepository(Session session) {
        super(session, OpeningRow.class, "opening", Sort.ascending("number"));
    }

    public List<OpeningRow> inBuilding(String code) {
        return rows(Where.equal("buildingCode", code));
    }

    /** The openings to the outside, which are the ones a lost master puts at risk first. */
    public List<OpeningRow> exterior() {
        return rows(Where.isTrue("exterior"));
    }

    /** Every building, by code. */
    public List<BuildingRow> buildings() {
        return rowsOf(BuildingRow.class, List.of(Sort.ascending("code")));
    }
}
