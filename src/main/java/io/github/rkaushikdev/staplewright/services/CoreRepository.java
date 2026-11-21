package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.CoreRow;
import java.util.List;
import java.util.Optional;

/** The interchangeable cores, by serial. */
public final class CoreRepository extends Repository<CoreRow> {

    public CoreRepository(Session session) {
        super(session, CoreRow.class, "core", Sort.ascending("serial"));
    }

    /** The cores one control key pulls. */
    public List<CoreRow> pulledBy(String code) {
        return rows(Where.equal("controlCode", code));
    }

    public List<CoreRow> installed() {
        return rows(Where.isNotNull("installedAt"));
    }

    public List<CoreRow> onTheShelf() {
        return rows(Where.isNull("installedAt"));
    }

    /** The core in one housing of one door, if any. */
    public Optional<CoreRow> inHousing(String opening, String housing) {
        return one(Where.equal("installedAt", opening), Where.equal("housing", housing));
    }
}
