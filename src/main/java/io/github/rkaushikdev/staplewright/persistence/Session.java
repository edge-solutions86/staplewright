package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.TypedQuery;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * One persistence context with its transaction always begun: what every repository, loader and
 * service works through.
 *
 * <p>A commit makes the work so far stick and begins the next transaction straight away, so work
 * can carry on in the same session. A rollback undoes the work since the last commit and forgets
 * everything loaded. Closing rolls back whatever nobody committed. Queries see rows added earlier
 * in the same session without a commit.
 *
 * <p>Rows keyed by an identity column are written the moment they are added, so the key is known
 * straight away and a constraint the row breaks is refused there; rows keyed by a value the shop
 * assigns are written at the next flush, query or commit.
 */
public final class Session implements AutoCloseable {

    private final EntityManager entityManager;
    private boolean closed;

    /** A session over an entity manager somebody opened, its transaction begun here. */
    public Session(EntityManager entityManager) {
        this.entityManager = entityManager;
        entityManager.getTransaction().begin();
    }

    /** The entity manager underneath, for a query this class has no shortcut for. */
    public EntityManager entityManager() {
        checkOpen();
        return entityManager;
    }

    /** The row with this key, from the session if it is already loaded. */
    public <T> Optional<T> get(Class<T> type, Object key) {
        checkOpen();
        return Optional.ofNullable(entityManager.find(type, key));
    }

    /** A stand in for the row with this key, which is enough to point a reference at it. */
    public <T> T reference(Class<T> type, Object key) {
        checkOpen();
        return entityManager.getReference(type, key);
    }

    /** Start keeping track of a new row. A row the session already holds is left as it is. */
    public <T> T add(T row) {
        checkOpen();
        if (!entityManager.contains(row)) {
            entityManager.persist(row);
        }
        return row;
    }

    /** {@link #add} for each row, in order. */
    public void addAll(Collection<?> rows) {
        rows.forEach(this::add);
    }

    /** Take a row out at the next flush. */
    public void delete(Object row) {
        checkOpen();
        entityManager.remove(row);
    }

    /** Push pending writes down without ending the transaction. */
    public void flush() {
        checkOpen();
        entityManager.flush();
    }

    /** Read a row's columns again from the database, collections included. */
    public void refresh(Object row) {
        checkOpen();
        entityManager.refresh(row);
    }

    /** Write pending changes and forget every loaded row, so the next read comes from the database. */
    public void expireAll() {
        checkOpen();
        entityManager.flush();
        entityManager.clear();
    }

    /** Every row of a type, in the order the table holds them, which is the order they arrived. */
    public <T> List<T> all(Class<T> type) {
        checkOpen();
        String name = entityManager.getMetamodel().entity(type).getName();
        return entityManager.createQuery("select r from " + name + " r", type).getResultList();
    }

    /** The one row of a type, refusing none or several. */
    public <T> T one(Class<T> type) {
        List<T> found = all(type);
        if (found.size() != 1) {
            throw new ConflictError(
                    "expected exactly one row", Facts.of("type", type.getSimpleName(), "found", found.size()));
        }
        return found.get(0);
    }

    /** A query in the persistence query language, with positional parameters {@code ?1, ?2...}. */
    public <T> TypedQuery<T> query(String statement, Class<T> type, Object... parameters) {
        checkOpen();
        TypedQuery<T> query = entityManager.createQuery(statement, type);
        for (int index = 0; index < parameters.length; index++) {
            query.setParameter(index + 1, parameters[index]);
        }
        return query;
    }

    /** The rows a query finds, in the order it asks for. */
    public <T> List<T> list(String statement, Class<T> type, Object... parameters) {
        return query(statement, type, parameters).getResultList();
    }

    /** The first row a query finds, if any. */
    public <T> Optional<T> first(String statement, Class<T> type, Object... parameters) {
        return query(statement, type, parameters).setMaxResults(1).getResultList().stream()
                .findFirst();
    }

    /** Run a plain statement against the tables, after writing everything pending. */
    public int execute(String sql) {
        checkOpen();
        entityManager.flush();
        return entityManager.createNativeQuery(sql).executeUpdate();
    }

    /** Make the work so far stick, and begin the next transaction. */
    public void commit() {
        checkOpen();
        entityManager.getTransaction().commit();
        entityManager.getTransaction().begin();
    }

    /** Undo the work since the last commit, forget what was loaded, and begin again. */
    public void rollback() {
        checkOpen();
        EntityTransaction transaction = entityManager.getTransaction();
        if (transaction.isActive()) {
            transaction.rollback();
        }
        entityManager.clear();
        transaction.begin();
    }

    public boolean isOpen() {
        return !closed;
    }

    /** Roll back whatever was not committed and close. Safe to call twice. */
    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        try {
            EntityTransaction transaction = entityManager.getTransaction();
            if (transaction.isActive()) {
                transaction.rollback();
            }
        } finally {
            entityManager.close();
        }
    }

    private void checkOpen() {
        if (closed) {
            throw new ConflictError("this session is closed");
        }
    }
}
