package io.github.rkaushikdev.staplewright.api;

import java.util.List;

/**
 * Every group of routes the service exposes, in one list.
 *
 * <p>A group that is written and never listed is a group nobody can reach, and nothing fails to
 * say so; the tests check this list against the routes the running service answers. Health comes
 * first because nothing else claims a path under it.
 */
public final class Routes {

    /** Every route group, health first. */
    public static final List<Class<?>> ROUTERS = List.of(
            HealthRoutes.class,
            AnnualRoutes.class,
            BoxRoutes.class,
            DoorRoutes.class,
            IncidentRoutes.class,
            KeyRoutes.class,
            ReportRoutes.class,
            StampingRoutes.class,
            StockRoutes.class,
            SystemRoutes.class,
            TransferRoutes.class);

    private Routes() {}
}
