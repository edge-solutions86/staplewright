package io.github.rkaushikdev.staplewright.persistence;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.Collection;
import java.util.List;

/**
 * One narrowing of a repository's rows: a condition on an attribute of the row.
 *
 * <p>Attributes are named as the row's fields are, and a dotted name reaches through a relation:
 * {@code system.id} is the key of the system a schedule line belongs to. Several conditions given
 * together must all hold.
 */
@FunctionalInterface
public interface Where {

    /** The condition, for one query over one row. */
    Predicate on(CriteriaBuilder builder, Root<?> row);

    /** The attribute equals the value. A converted attribute takes its own type: a bitting, a symbol. */
    static Where equal(String attribute, Object value) {
        return (builder, row) -> builder.equal(path(row, attribute), value);
    }

    /** The attribute holds nothing. */
    static Where isNull(String attribute) {
        return (builder, row) -> builder.isNull(path(row, attribute));
    }

    /** The attribute holds something. */
    static Where isNotNull(String attribute) {
        return (builder, row) -> builder.isNotNull(path(row, attribute));
    }

    /** The yes or no attribute is yes. */
    static Where isTrue(String attribute) {
        return (builder, row) -> builder.isTrue(Where.<Boolean>path(row, attribute));
    }

    /** The yes or no attribute is no. */
    static Where isFalse(String attribute) {
        return (builder, row) -> builder.isFalse(Where.<Boolean>path(row, attribute));
    }

    /** The attribute is before the value. An attribute holding nothing never is. */
    static <Y extends Comparable<? super Y>> Where less(String attribute, Y value) {
        return (builder, row) -> builder.lessThan(Where.<Y>path(row, attribute), value);
    }

    /** The attribute is the value or after it. An attribute holding nothing never is. */
    static <Y extends Comparable<? super Y>> Where atLeast(String attribute, Y value) {
        return (builder, row) -> builder.greaterThanOrEqualTo(Where.<Y>path(row, attribute), value);
    }

    /** The attribute is one of the values. */
    static Where in(String attribute, Collection<?> values) {
        List<?> held = List.copyOf(values);
        return (builder, row) -> path(row, attribute).in(held);
    }

    /** The path a possibly dotted attribute name reaches from a row. */
    static <Y> Path<Y> path(Root<?> row, String attribute) {
        Path<?> reached = row;
        for (String step : attribute.split("\\.", -1)) {
            reached = reached.get(step);
        }
        @SuppressWarnings("unchecked")
        Path<Y> typed = (Path<Y>) reached;
        return typed;
    }
}
