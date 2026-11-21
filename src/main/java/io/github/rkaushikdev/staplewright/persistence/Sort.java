package io.github.rkaushikdev.staplewright.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;

/** One sort key for a repository's rows: an attribute, ascending or descending. */
@FunctionalInterface
public interface Sort {

    /** The ordering, for one query over one row. */
    Order on(CriteriaBuilder builder, Root<?> row);

    /** Smallest first, by an attribute named as {@link Where} names them. */
    static Sort ascending(String attribute) {
        return (builder, row) -> builder.asc(Where.path(row, attribute));
    }

    /** Largest first, by an attribute named as {@link Where} names them. */
    static Sort descending(String attribute) {
        return (builder, row) -> builder.desc(Where.path(row, attribute));
    }
}
