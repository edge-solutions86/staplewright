package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

/**
 * The migrations and the mapped rows have to describe the same schema.
 *
 * <p>This is the test that catches the commit where somebody adds a column to a row and forgets the
 * migration. It builds one database from the migrations alone and one from the mapping alone, then
 * compares every mapped table column by column and constraint by constraint. Check constraints live
 * in the migrations only, so they are not compared.
 *
 * <p>The indexes compared are the ones somebody named. The database makes an index of its own to
 * back a foreign key that no index covers yet, and whether it does depends only on whether the
 * named index on that column was built before the key or after it, which the migrations and the
 * mapping do in opposite orders. The key itself is compared either way.
 */
class MigrationsTest {

    /**
     * What the migrations hold that no row maps, by table: the column behind the one open claim per
     * key rule, which a unique index over that column enforces, and the unique constraint a filed
     * return carries on its year beside the primary key on the same column, which the mapping names
     * but never builds because the key already makes the year unique.
     */
    private static final Map<String, Set<String>> MIGRATION_ONLY = Map.of(
            "key_transfers", Set.of("tag_while_claimed", "ux_key_transfers_one_open_claim"),
            "annual_returns", Set.of("uq_annual_returns_year"));

    private Database migrated;

    @BeforeEach
    void open() {
        migrated = Database.memory();
    }

    @AfterEach
    void close() {
        migrated.close();
    }

    private static Database mapped() {
        Database built = Database.memory();
        LocalContainerEntityManagerFactoryBean factory =
                Database.entityManagerFactoryBean(built.dataSource(), "create");
        factory.afterPropertiesSet();
        factory.destroy();
        return built;
    }

    /** One table's shape, as one set of readable facts per aspect. */
    record Shape(
            Set<String> columns,
            List<String> primaryKey,
            Set<String> unique,
            Set<String> foreign,
            Set<String> indexes) {}

    private static Shape shape(DataSource source, String table) {
        String name = table.toUpperCase(Locale.ROOT);
        return new Shape(
                strings(
                        source,
                        """
                        select column_name || ' ' || data_type
                            || coalesce('(' || character_maximum_length || ')', '')
                            || case when is_nullable = 'NO' then ' not null' else '' end
                            || case when is_identity = 'YES' then ' identity' else '' end
                        from information_schema.columns where table_schema = 'PUBLIC' and table_name = ?
                        """,
                        name),
                new ArrayList<>(listed(
                        source,
                        """
                        select k.column_name from information_schema.table_constraints c
                        join information_schema.key_column_usage k
                            on k.constraint_name = c.constraint_name and k.table_name = c.table_name
                        where c.table_schema = 'PUBLIC' and c.table_name = ? and c.constraint_type = 'PRIMARY KEY'
                        order by k.ordinal_position
                        """,
                        name)),
                strings(
                        source,
                        """
                        select c.constraint_name || ' ' || listagg(k.column_name, ',') within group (order by k.ordinal_position)
                        from information_schema.table_constraints c
                        join information_schema.key_column_usage k
                            on k.constraint_name = c.constraint_name and k.table_name = c.table_name
                        where c.table_schema = 'PUBLIC' and c.table_name = ? and c.constraint_type = 'UNIQUE'
                        group by c.constraint_name
                        """,
                        name),
                strings(
                        source,
                        """
                        select c.constraint_name || ' ' || listagg(k.column_name, ',') within group (order by k.ordinal_position)
                            || ' ' || max(u.table_name)
                        from information_schema.table_constraints c
                        join information_schema.key_column_usage k
                            on k.constraint_name = c.constraint_name and k.table_name = c.table_name
                        join information_schema.referential_constraints r on r.constraint_name = c.constraint_name
                        join information_schema.table_constraints u on u.constraint_name = r.unique_constraint_name
                        where c.table_schema = 'PUBLIC' and c.table_name = ? and c.constraint_type = 'FOREIGN KEY'
                        group by c.constraint_name
                        """,
                        name),
                strings(
                        source,
                        """
                        select i.index_name || ' ' || listagg(c.column_name, ',') within group (order by c.ordinal_position)
                        from information_schema.indexes i
                        join information_schema.index_columns c
                            on c.index_name = i.index_name and c.table_name = i.table_name
                        where i.table_schema = 'PUBLIC' and i.table_name = ? and i.index_type_name = 'INDEX'
                            and not i.is_generated
                        group by i.index_name
                        """,
                        name));
    }

    private static Set<String> strings(DataSource source, String sql, String table) {
        return new TreeSet<>(listed(source, sql, table));
    }

    private static List<String> listed(DataSource source, String sql, String table) {
        List<String> found = new ArrayList<>();
        try (Connection connection = source.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, table);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    found.add(rows.getString(1).toLowerCase(Locale.ROOT));
                }
            }
        } catch (SQLException trouble) {
            throw new IllegalStateException(trouble);
        }
        return found;
    }

    private static Shape withoutMigrationOnly(String table, Shape shape) {
        Set<String> drop = MIGRATION_ONLY.getOrDefault(table, Set.of());
        Function<Set<String>, Set<String>> keep = facts -> new TreeSet<>(facts.stream()
                .filter(fact -> drop.stream().noneMatch(name -> fact.startsWith(name + " ")))
                .toList());
        return new Shape(
                keep.apply(shape.columns()),
                shape.primaryKey(),
                keep.apply(shape.unique()),
                keep.apply(shape.foreign()),
                keep.apply(shape.indexes()));
    }

    private static Map<String, Shape> shapes(Database database) {
        Map<String, Shape> found = new TreeMap<>();
        for (String table : Schema.tableNames()) {
            found.put(table, withoutMigrationOnly(table, shape(database.dataSource(), table)));
        }
        return found;
    }

    @Test
    void theMigrationsBuildEveryTable() {
        migrated.migrate();
        Set<String> built = new TreeSet<>(migrated.tableNames());
        assertThat(built).containsAll(Schema.tableNames());
        assertThat(migrated.appliedMigrations()).isNotEmpty();
    }

    @Test
    void theMigrationsAndTheModelsAgree() {
        migrated.migrate();
        try (Database fromMapping = mapped()) {
            assertThat(shapes(migrated)).isEqualTo(shapes(fromMapping));
        }
    }

    @Test
    void theComparisonReadsEveryAspectAndSeesADifference() throws SQLException {
        migrated.migrate();
        Shape keying = shape(migrated.dataSource(), "keying_groups");
        assertThat(keying.columns()).isNotEmpty();
        assertThat(keying.primaryKey()).containsExactly("id");
        assertThat(keying.unique()).contains("uq_keying_groups_system_id_name system_id,name");
        assertThat(keying.foreign()).contains("fk_keying_groups_system_id_keying_systems system_id keying_systems");
        assertThat(keying.indexes()).contains("ix_keying_groups_name name");
        Shape untouched = shape(migrated.dataSource(), "charts");
        try (Connection connection = migrated.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("alter table keying_groups add column planted integer");
            statement.execute("create index ix_planted on keying_groups (note)");
            statement.execute("alter table keying_groups drop constraint uq_keying_groups_system_id_name");
            statement.execute("alter table keying_groups drop constraint fk_keying_groups_system_id_keying_systems");
        }
        Shape planted = shape(migrated.dataSource(), "keying_groups");
        assertThat(planted.columns()).isNotEqualTo(keying.columns());
        assertThat(planted.indexes()).isNotEqualTo(keying.indexes());
        assertThat(planted.unique()).isNotEqualTo(keying.unique());
        assertThat(planted.foreign()).isNotEqualTo(keying.foreign());
        assertThat(shape(migrated.dataSource(), "charts")).isEqualTo(untouched);
    }

    @Test
    void theSchemaComesApartAgain() {
        migrated.migrate();
        migrated.drop();
        assertThat(migrated.tableNames()).isEmpty();
    }

    @Test
    void theSchemaCanBeBuiltTwice() {
        migrated.migrate();
        migrated.drop();
        migrated.migrate();
        assertThat(migrated.tableNames()).containsAll(Schema.tableNames());
    }

    @Test
    void theHeadIsASingleRevision() {
        migrated.migrate();
        List<String> versions = migrated.appliedMigrations();
        assertThat(versions)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, versions.size())
                        .mapToObj(String::valueOf)
                        .toList());
    }

    @Test
    void everyRevisionCanBeTakenOutAgain() {
        migrated.migrate();
        int head = migrated.appliedMigrations().size();
        for (int target = 1; target <= head; target++) {
            try (Database partial = Database.memory()) {
                org.flywaydb.core.Flyway.configure()
                        .dataSource(partial.dataSource())
                        .locations(Database.MIGRATIONS)
                        .target(String.valueOf(target))
                        .load()
                        .migrate();
                assertThat(partial.appliedMigrations()).hasSize(target);
                partial.drop();
                assertThat(partial.tableNames()).isEmpty();
            }
        }
    }

    @Test
    void theForeignKeysArriveWithTheTables() {
        migrated.migrate();
        Set<String> referred = new TreeSet<>();
        for (String fact : shape(migrated.dataSource(), "cylinders").foreign()) {
            referred.add(fact.substring(fact.lastIndexOf(' ') + 1));
        }
        assertThat(referred).contains("openings");
    }

    @Test
    void theNamedConstraintsArriveWithTheTables() {
        migrated.migrate();
        Set<String> names = new TreeSet<>();
        for (String fact : shape(migrated.dataSource(), "schedule_entries").unique()) {
            names.add(fact.substring(0, fact.indexOf(' ')));
        }
        assertThat(names).contains("uq_schedule_entries_system_id_symbol");
    }
}
