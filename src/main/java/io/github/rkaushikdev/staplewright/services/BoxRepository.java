package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.persistence.Repository;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Sort;
import io.github.rkaushikdev.staplewright.persistence.Where;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** The key boxes on the walls, by number. */
public final class BoxRepository extends Repository<KeyBoxRow> {

    public BoxRepository(Session session) {
        super(session, KeyBoxRow.class, "key box", Sort.ascending("number"));
    }

    public List<KeyBoxRow> inBuilding(String code) {
        return rows(Where.equal("buildingCode", code));
    }

    /** Every box not taken off the wall. */
    public List<KeyBoxRow> live() {
        List<String> states = Arrays.stream(BoxState.values())
                .filter(state -> state != BoxState.WITHDRAWN)
                .map(BoxState::value)
                .toList();
        return rows(Where.in("state", states));
    }

    public List<KeyBoxRow> inState(String state) {
        return rows(Where.equal("state", state));
    }

    /** The box beside an opening, if there is one. */
    public Optional<KeyBoxRow> onOpening(String number) {
        return one(Where.equal("openingNumber", number));
    }
}
