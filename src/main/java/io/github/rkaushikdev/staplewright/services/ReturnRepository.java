package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.AnnualReturnRow;
import java.util.List;
import java.util.Optional;

/** The filed annual returns, one a year, oldest first. */
public final class ReturnRepository extends Repository<AnnualReturnRow> {

    public ReturnRepository(Session session) {
        super(session, AnnualReturnRow.class, "annual return", Sort.ascending("year"));
    }

    /** The returns filed for {@code year} and every year after it. */
    public List<AnnualReturnRow> since(int year) {
        return rows(Where.atLeast("year", year));
    }

    /** The most recent year on file, if any year is. */
    public Optional<AnnualReturnRow> latest() {
        List<AnnualReturnRow> found = all();
        return found.isEmpty() ? Optional.empty() : Optional.of(found.getLast());
    }
}
