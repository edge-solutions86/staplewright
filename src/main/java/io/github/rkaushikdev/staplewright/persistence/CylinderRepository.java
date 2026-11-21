package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import java.util.List;

/** Cylinders by serial, and by where they are. */
public final class CylinderRepository extends Repository<CylinderRow> {

    public CylinderRepository(Session session) {
        super(session, CylinderRow.class, "cylinder", Sort.ascending("serial"));
    }

    /** The cylinders installed in one opening. */
    public List<CylinderRow> onOpening(String number) {
        return rows(Where.equal("installedAt", number));
    }

    /** The cylinders in the drawer rather than in a door. */
    public List<CylinderRow> onTheShelf() {
        return rows(Where.isNull("installedAt"));
    }

    /** The cylinders answering to one keying group. */
    public List<CylinderRow> inGroup(int groupId) {
        return rows(Where.equal("group.id", groupId));
    }

    /** Installed, and nobody wrote down what it answers to. */
    public List<CylinderRow> unkeyed() {
        return rows(Where.isNotNull("installedAt"), Where.isNull("group"));
    }
}
