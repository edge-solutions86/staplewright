package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import java.time.LocalDate;
import java.util.List;

/** Missing key incidents by number. */
public final class IncidentRepository extends Repository<IncidentRow> {

    public IncidentRepository(Session session) {
        super(session, IncidentRow.class, "incident", Sort.ascending("number"));
    }

    /** The incidents reported on {@code when} or later, oldest first. */
    public List<IncidentRow> since(LocalDate when) {
        return sortedBy(Sort.ascending("reportedOn"), Where.atLeast("reportedOn", when));
    }

    /** The incidents of one severity, given by its stored value. */
    public List<IncidentRow> bySeverity(String severity) {
        return rows(Where.equal("severity", severity));
    }
}
