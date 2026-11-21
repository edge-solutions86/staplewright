package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import java.util.List;
import java.util.TreeSet;

/** The keyway section rows, in code order. */
public final class SectionRepository extends Repository<KeywaySectionRow> {

    public SectionRepository(Session session) {
        super(session, KeywaySectionRow.class, "keyway section", Sort.ascending("code"));
    }

    public List<KeywaySectionRow> inFamily(String familyName) {
        return rows(Where.equal("familyName", familyName));
    }

    /** Every family any section belongs to, sorted. */
    public List<String> families() {
        return List.copyOf(new TreeSet<>(
                all().stream().map(KeywaySectionRow::getFamilyName).toList()));
    }
}
