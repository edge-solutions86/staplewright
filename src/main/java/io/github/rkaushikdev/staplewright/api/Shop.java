package io.github.rkaushikdev.staplewright.api;

import io.github.rkaushikdev.staplewright.persistence.Database;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;

/**
 * What the routes reach for: one database and the shop's settings, and nothing else.
 *
 * @param database where every request's session comes from
 * @param settings the policy the services are built with
 */
public record Shop(Database database, ShopSettings settings) {

    /** A shop on a database, running on the settings the environment gives. */
    public static Shop on(Database database) {
        return new Shop(database, Settings.fromEnvironment());
    }
}
