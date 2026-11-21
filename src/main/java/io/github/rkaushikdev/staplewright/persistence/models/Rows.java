package io.github.rkaushikdev.staplewright.persistence.models;

import java.util.List;

/**
 * Every row class in the schema, listed once.
 *
 * <p>The schema's table order is worked out from this list, and a test makes sure every mapped row
 * class is on it, so a table cannot be mapped and quietly left out of the order.
 */
public final class Rows {

    /** Every mapped row class. */
    public static final List<Class<?>> ALL = List.of(
            ChartRow.class,
            KeywaySectionRow.class,
            BlankRow.class,
            SystemRow.class,
            ScheduleEntryRow.class,
            GroupRow.class,
            GroupMemberRow.class,
            BuildingRow.class,
            OpeningRow.class,
            CylinderRow.class,
            AccessGroupRow.class,
            AccessRequirementRow.class,
            KeyholderRow.class,
            AuthorizerRow.class,
            KeyRequestRow.class,
            IssuedKeyRow.class,
            DepositLineRow.class,
            ReviewRow.class,
            ReviewLineRow.class,
            KeyedAlikeSetRow.class,
            KeyedAlikeMemberRow.class,
            LockoutRow.class,
            SurveyRow.class,
            SurveyLineRow.class);

    private Rows() {}
}
