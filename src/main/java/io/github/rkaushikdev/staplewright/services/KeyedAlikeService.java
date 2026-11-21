package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.KeyedAlikeSet;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.AccessMappers.AlikeRows;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeMemberRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyedAlikeSetRow;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.Optional;

/**
 * Sets of doors that carry one key, with the database as the referee.
 *
 * <p>The service leans on the database for its one rule. A door belongs to one set, the unique
 * constraint says so, and the service does not check first: it writes and lets the constraint
 * refuse, because checking first is a race and the constraint is not.
 */
public final class KeyedAlikeService {

    private final Session session;

    public KeyedAlikeService(Session session) {
        this.session = session;
    }

    public Session session() {
        return session;
    }

    /**
     * Write a set, letting the unique constraint refuse a shared door.
     *
     * <p>A refusal rolls the session back, so whatever else it held that was not committed goes
     * with the set.
     *
     * @throws ConflictError when one of the doors is already in a set
     */
    public KeyedAlikeSetRow create(KeyedAlikeSet alike) {
        AlikeRows rows = AccessMappers.alikeToRows(alike);
        try {
            session.add(rows.alike());
            session.addAll(rows.members());
            session.flush();
        } catch (PersistenceException clash) {
            session.rollback();
            ConflictError refusal =
                    new ConflictError("one of those doors is already in a set", Facts.of("name", alike.name()));
            refusal.initCause(clash);
            throw refusal;
        }
        return rows.alike();
    }

    /** The set with this name, refusing a name nobody used. */
    public KeyedAlikeSet get(String name) {
        return AccessMappers.alikeFromRow(row(name));
    }

    /** The set a door belongs to, if it belongs to one. */
    public Optional<KeyedAlikeSet> setHolding(String opening) {
        return session.first(
                        "select m from KeyedAlikeMemberRow m where m.openingNumber = ?1",
                        KeyedAlikeMemberRow.class,
                        opening)
                .map(member -> get(member.getSetName()));
    }

    /** Take a set apart, which frees every door in it. */
    public void dissolve(String name) {
        session.delete(row(name));
        session.flush();
    }

    /** Every set, by name. */
    public List<KeyedAlikeSet> sets() {
        return session.list("select s from KeyedAlikeSetRow s order by s.name", KeyedAlikeSetRow.class).stream()
                .map(AccessMappers::alikeFromRow)
                .toList();
    }

    private KeyedAlikeSetRow row(String name) {
        return session.get(KeyedAlikeSetRow.class, name)
                .orElseThrow(() -> new NotFoundError("no such keyed alike set", Facts.of("name", name)));
    }
}
