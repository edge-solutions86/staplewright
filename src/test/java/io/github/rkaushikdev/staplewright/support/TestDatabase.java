package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.persistence.Database;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * The suite's database: one H2 in memory, migrated once for the whole run and emptied before each
 * test that asks for it.
 *
 * <p>Starting a mapping per test costs more than the tests do, so this keeps one database and one
 * entity manager factory and gives every test a database of its own in effect: the schema, nothing
 * in it, and the generated keys counting from one.
 */
public final class TestDatabase {

    /** Where the shared database lives. */
    public static final String URL = Database.MEMORY + "staplewright-tests";

    private static Database shared;

    private TestDatabase() {}

    /** The shared database, emptied. */
    public static synchronized Database fresh() {
        Database database = database();
        empty(database);
        return database;
    }

    /** The shared database as it stands, migrated the first time it is asked for. */
    public static synchronized Database database() {
        if (shared == null) {
            shared = Database.open(URL);
            shared.migrate();
        }
        return shared;
    }

    private static void empty(Database database) {
        List<String> tables = database.tableNames();
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String table : tables) {
                statement.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
            }
            statement.execute("SET REFERENTIAL_INTEGRITY TRUE");
        } catch (SQLException trouble) {
            throw new IllegalStateException("could not empty the test database", trouble);
        }
    }
}
