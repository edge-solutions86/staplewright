package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PersistenceBaseTest {

    /** A table of its own, so it stays out of the migrations. */
    private static final String SAMPLE =
            """
            create table sample_cuts (
                id integer not null,
                symbol character varying(16),
                bitting character varying(48),
                chambers character varying(96),
                note character varying(512),
                constraint pk_sample_cuts primary key (id))
            """;

    private Database database;

    @BeforeEach
    void open() throws SQLException {
        database = Database.memory();
        database.migrate();
        run(SAMPLE);
    }

    @AfterEach
    void close() {
        database.close();
    }

    private void run(String sql) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void insert(String column, String stored) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                PreparedStatement statement =
                        connection.prepareStatement("insert into sample_cuts (id, " + column + ") values (1, ?)")) {
            statement.setString(1, stored);
            statement.executeUpdate();
        }
    }

    private String stored(String column) throws SQLException {
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("select " + column + " from sample_cuts")) {
            rows.next();
            return rows.getString(1);
        }
    }

    @Test
    void theColumnWidthsAreOrdered() {
        assertThat(Schema.SHORT).isLessThan(Schema.MEDIUM);
        assertThat(Schema.MEDIUM).isLessThan(Schema.LONG);
    }

    @Test
    void everyTableIsNamedAndReachable() {
        List<String> names = Schema.tableNames();
        assertThat(names).contains("charts").doesNotContain("sample_cuts");
        assertThat(new HashSet<>(names)).hasSize(names.size());
    }

    @Test
    void theNamingConventionReachesThePrimaryKey() throws SQLException {
        int checked = 0;
        try (Connection connection = database.dataSource().getConnection();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery(
                        """
                        select table_name, constraint_name from information_schema.table_constraints
                        where table_schema = 'PUBLIC' and constraint_type = 'PRIMARY KEY'
                        """)) {
            while (rows.next()) {
                String table = rows.getString(1).toLowerCase(Locale.ROOT);
                if (table.equals("flyway_schema_history")) {
                    continue;
                }
                assertThat(rows.getString(2).toLowerCase(Locale.ROOT)).isEqualTo("pk_" + table);
                checked++;
            }
        }
        assertThat(checked).isGreaterThan(Schema.tableNames().size() - 1);
    }

    @Test
    void theDatabaseEnforcesForeignKeys() {
        assertThatThrownBy(
                        () -> run(
                                """
                        insert into key_blanks (part_number, section_id, manufacturer, chambers, stock,
                            restricted, supplier, reorder_at)
                        values ('LA-6', 999, 'Sargent', 6, 0, false, '', 0)
                        """))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("FK_KEY_BLANKS_SECTION_ID_KEYWAY_SECTIONS");
    }

    @Test
    void aBittingGoesInAndComesBackAsABitting() throws SQLException {
        insert("bitting", BittingColumn.bind(Bitting.parse("342156")));
        assertThat(BittingColumn.read(stored("bitting"))).isEqualTo(Bitting.parse("342156"));
    }

    @Test
    void aBittingIsStoredTheWayTheShopWritesIt() throws SQLException {
        insert("bitting", new BittingColumn().convertToDatabaseColumn(Bitting.of(10, 4, 2)));
        assertThat(stored("bitting")).isEqualTo("10.4.2");
    }

    @Test
    void aBittingCanBeWrittenAsText() throws SQLException {
        insert("bitting", BittingColumn.bind("342156"));
        assertThat(new BittingColumn().convertToEntityAttribute(stored("bitting")))
                .isEqualTo(Bitting.parse("342156"));
    }

    @Test
    void aSymbolRoundTrips() throws SQLException {
        insert("symbol", SymbolColumn.bind(KeySymbol.parse("AB3")));
        assertThat(SymbolColumn.read(stored("symbol"))).isEqualTo(KeySymbol.parse("AB3"));
    }

    @Test
    void theTopKeyRoundTripsToo() throws SQLException {
        insert("symbol", new SymbolColumn().convertToDatabaseColumn(KeySymbol.parse("GGM")));
        assertThat(new SymbolColumn().convertToEntityAttribute(stored("symbol")).isTop())
                .isTrue();
    }

    @Test
    void aChamberListRoundTrips() throws SQLException {
        insert("chambers", DepthListColumn.bind(List.of(2, 4, 6)));
        assertThat(DepthListColumn.read(stored("chambers"))).containsExactly(2, 4, 6);
    }

    @Test
    void anEmptyChamberListComesBackEmpty() throws SQLException {
        insert("chambers", new DepthListColumn().convertToDatabaseColumn(List.of()));
        assertThat(new DepthListColumn().convertToEntityAttribute(stored("chambers")))
                .isEmpty();
    }

    @Test
    void nullsStayNull() throws SQLException {
        run("insert into sample_cuts (id) values (1)");
        assertThat(BittingColumn.read(stored("bitting"))).isNull();
        assertThat(SymbolColumn.read(stored("symbol"))).isNull();
        assertThat(DepthListColumn.read(stored("chambers"))).isNull();
        assertThat(BittingColumn.bind(null)).isNull();
        assertThat(SymbolColumn.bind(null)).isNull();
        assertThat(DepthListColumn.bind(null)).isNull();
    }

    @Test
    void somethingThatIsNotABittingIsRefused() {
        assertThatThrownBy(() -> BittingColumn.bind(42))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("cannot store Integer as a bitting");
    }

    @Test
    void somethingThatIsNotASymbolIsRefused() {
        assertThatThrownBy(() -> SymbolColumn.bind(7))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("key symbol");
    }

    @Test
    void aFailedScopeRollsBack() {
        assertThatThrownBy(() -> database.inSession(session -> {
                    session.execute("insert into sample_cuts (id, bitting) values (1, '342156')");
                    throw new IllegalStateException("something went wrong halfway");
                }))
                .isInstanceOf(IllegalStateException.class);
        long left = database.fromSession(session -> ((Number) session.entityManager()
                        .createNativeQuery("select count(*) from sample_cuts")
                        .getSingleResult())
                .longValue());
        assertThat(left).isZero();
    }
}
