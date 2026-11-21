package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import jakarta.persistence.NonUniqueResultException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Rows of one table, out of one session.
 *
 * <p>Repositories are the only place a query is allowed to live. Every one of them takes a session
 * and hands back rows. They do not commit, they do not open transactions, and they do not know about
 * the domain objects either: the mappers do that, and keeping the two apart is what lets a query be
 * tested without a keying system and a mapper be tested without a database.
 *
 * <p>The base is small on purpose. The interesting methods are the ones on each repository, because
 * those are the questions the shop actually asks. What the base carries is the shape every one of
 * those questions has: pick a row type, narrow it, sort it, hand back a list, in the repository's
 * usual order unless the caller names another, so no report prints in arrival order by accident.
 *
 * @param <T> the row type
 */
public abstract class Repository<T> {

    private final Session session;
    private final Class<T> model;
    private final String noun;
    private final List<Sort> order;

    /**
     * A repository over one row type.
     *
     * @param noun what one row is called in a refusal: {@code no such chart}
     * @param order the usual order of its rows
     */
    protected Repository(Session session, Class<T> model, String noun, Sort... order) {
        this.session = session;
        this.model = model;
        this.noun = noun;
        this.order = List.of(order);
    }

    /** The session the repository reads and writes through. */
    public Session session() {
        return session;
    }

    /** What one row is called in a refusal. */
    public String noun() {
        return noun;
    }

    public T add(T row) {
        return session.add(row);
    }

    /** {@link #add} for each row, in order; the same rows back. */
    public List<T> addAll(List<T> rows) {
        session.addAll(rows);
        return rows;
    }

    /** The row with this key, if there is one. */
    public Optional<T> find(Object key) {
        return session.get(model, key);
    }

    /** The row with this key, refusing as {@link #require(Object)} does. */
    public T get(Object key) {
        return require(key);
    }

    /**
     * The row with this key, or a refusal that names it.
     *
     * @throws NotFoundError {@code no such <noun>}, with the key as text under {@code key}
     */
    public T require(Object key) {
        return require(key, "", Map.of());
    }

    /**
     * The row with this key, or a refusal in the caller's words, with the key as text under
     * {@code key}. An empty message is the usual {@code no such <noun>}.
     */
    public T require(Object key, String message) {
        return require(key, message, Map.of());
    }

    /**
     * The row with this key, or a refusal in the caller's words carrying the caller's facts.
     *
     * <p>Every service names its refusals the way its own callers read them, and the facts are
     * spelled the same way on every refusal from one repository: given none, the refusal carries
     * the key as text under {@code key}; given some, it carries exactly those.
     */
    public T require(Object key, String message, Map<String, ?> facts) {
        Optional<T> found = find(key);
        if (found.isPresent()) {
            return found.get();
        }
        throw new NotFoundError(
                message.isEmpty() ? "no such " + noun : message,
                facts.isEmpty() ? Facts.of("key", String.valueOf(key)) : facts);
    }

    /** Take the row out at the next flush. */
    public void delete(T row) {
        session.delete(row);
    }

    /** Every row, in the usual order. */
    public List<T> all() {
        return rows();
    }

    /** How many rows the table holds, counting what this session has written. */
    public long count() {
        CriteriaBuilder builder = session.entityManager().getCriteriaBuilder();
        CriteriaQuery<Long> query = builder.createQuery(Long.class);
        query.select(builder.count(query.from(model)));
        return session.entityManager().createQuery(query).getSingleResult();
    }

    public boolean exists(Object key) {
        return find(key).isPresent();
    }

    /** This repository's own rows, narrowed, in its usual order. */
    public List<T> rows(Where... narrow) {
        return rowsOf(model, order, narrow);
    }

    /** This repository's own rows, narrowed, in the order the caller names instead. */
    public List<T> sortedBy(Sort sorting, Where... narrow) {
        return rowsOf(model, List.of(sorting), narrow);
    }

    /**
     * The single row that matches, or nothing.
     *
     * @throws NonUniqueResultException when two rows match
     */
    public Optional<T> one(Where... narrow) {
        List<T> found = session.entityManager()
                .createQuery(select(model, List.of(), narrow))
                .setMaxResults(2)
                .getResultList();
        if (found.size() > 1) {
            throw new NonUniqueResultException("more than one " + noun + " matches");
        }
        return found.stream().findFirst();
    }

    /** Somebody else's rows, for the questions that cross a table, in the order the table gives. */
    public <O> List<O> rowsOf(Class<O> other, Where... narrow) {
        return rowsOf(other, List.of(), narrow);
    }

    /** Somebody else's rows, narrowed and sorted. */
    public <O> List<O> rowsOf(Class<O> other, List<Sort> sorting, Where... narrow) {
        return session.entityManager()
                .createQuery(select(other, sorting, narrow))
                .getResultList();
    }

    private <O> CriteriaQuery<O> select(Class<O> other, List<Sort> sorting, Where... narrow) {
        CriteriaBuilder builder = session.entityManager().getCriteriaBuilder();
        CriteriaQuery<O> query = builder.createQuery(other);
        Root<O> row = query.from(other);
        query.select(row)
                .where(Arrays.stream(narrow)
                        .map(condition -> condition.on(builder, row))
                        .toArray(Predicate[]::new));
        if (!sorting.isEmpty()) {
            query.orderBy(sorting.stream().map(sort -> sort.on(builder, row)).toList());
        }
        return query;
    }
}
