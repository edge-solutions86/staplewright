package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import java.util.List;
import java.util.Optional;

/** Depth and space charts, by family. */
public final class ChartRepository extends Repository<ChartRow> {

    public ChartRepository(Session session) {
        super(session, ChartRow.class, "chart", Sort.ascending("family"));
    }

    /** The chart for one family of one manufacturer, if it is on file. */
    public Optional<ChartRow> byFamily(String manufacturer, String family) {
        return one(Where.equal("manufacturer", manufacturer), Where.equal("family", family));
    }

    public List<ChartRow> forManufacturer(String manufacturer) {
        return rows(Where.equal("manufacturer", manufacturer));
    }
}
