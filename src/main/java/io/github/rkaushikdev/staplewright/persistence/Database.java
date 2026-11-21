package io.github.rkaushikdev.staplewright.persistence;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

/**
 * One database: a connection pool, the schema's migrations, and the entity manager factory every
 * session is opened from.
 *
 * <p>The command line opens one of these from a URL and nothing else, so a command runs without a
 * framework and honours {@code --database} before anything connects. The service is handed the same
 * object.
 *
 * <p>The entity manager factory is built when first asked for, and it checks the mapping against
 * the schema when it is. A database nobody has built can still be pinged, built and dropped.
 */
public final class Database implements AutoCloseable {

    /** Where the rows and their column converters live. */
    public static final String MAPPED_PACKAGE = "io.github.rkaushikdev.staplewright.persistence";

    /** Where the migrations live, one per revision of the schema. */
    public static final String MIGRATIONS = "classpath:db/migration";

    /** The prefix of a database held in memory, which lives as long as the process holds it. */
    public static final String MEMORY = "jdbc:h2:mem:";

    /**
     * Column names the schema uses that H2 otherwise reads as keywords. They are ordinary names in
     * the domain, so the URL tells H2 so rather than the schema renaming them.
     */
    static final String H2_NON_KEYWORDS = "NON_KEYWORDS=YEAR";

    private static final String HISTORY_TABLE = "flyway_schema_history";
    private static final AtomicInteger PRIVATE_MEMORY = new AtomicInteger();

    private final String url;
    private final HikariDataSource pool;
    private EntityManagerFactory factory;
    private LocalContainerEntityManagerFactoryBean ownedFactory;
    private boolean closed;

    private Database(String url, HikariDataSource pool) {
        this.url = url;
        this.pool = pool;
    }

    /** Open the database at {@code url}. Nothing connects until something is asked. */
    public static Database open(String url) {
        return new Database(url, pool(url));
    }

    /** A private database in memory, empty, which goes away when it is closed. */
    public static Database memory() {
        return open(MEMORY + "staplewright-" + PRIVATE_MEMORY.incrementAndGet());
    }

    private static HikariDataSource pool(String url) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl(url));
        config.setPoolName("staplewright");
        config.setMaximumPoolSize(4);
        config.setMinimumIdle(1);
        config.setInitializationFailTimeout(-1);
        // A database that will not answer is reported within seconds, not after half a minute.
        config.setConnectionTimeout(3_000);
        return new HikariDataSource(config);
    }

    /**
     * The URL as the driver gets it. H2 is told the schema's column names are not keywords, and a
     * database in memory is kept for as long as this process holds it rather than for as long as
     * one connection does, so every session sees the same tables.
     */
    public static String jdbcUrl(String configured) {
        String out = configured;
        String upper = configured.toUpperCase(Locale.ROOT);
        if (configured.startsWith(MEMORY) && !upper.contains("DB_CLOSE_DELAY")) {
            out = out + ";DB_CLOSE_DELAY=-1";
        }
        if (configured.startsWith("jdbc:h2:") && !upper.contains("NON_KEYWORDS")) {
            out = out + ";" + H2_NON_KEYWORDS;
        }
        return out;
    }

    /**
     * The factory definition every part of the process opens sessions from.
     *
     * @param ddl what Hibernate does to the schema when the factory starts: {@code validate} in
     *     service, {@code create} only to build a schema straight from the mapping for comparison
     */
    public static LocalContainerEntityManagerFactoryBean entityManagerFactoryBean(DataSource dataSource, String ddl) {
        LocalContainerEntityManagerFactoryBean bean = new LocalContainerEntityManagerFactoryBean();
        bean.setDataSource(dataSource);
        bean.setPackagesToScan(MAPPED_PACKAGE);
        bean.setPersistenceUnitName("staplewright");
        bean.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("hibernate.hbm2ddl.auto", ddl);
        properties.put("hibernate.show_sql", "false");
        properties.put("hibernate.default_batch_fetch_size", "64");
        properties.put("hibernate.jdbc.time_zone", "UTC");
        // Hibernate otherwise lays columns out by type size, composite keys included, and a
        // schema it builds stops matching the order the migrations declare.
        properties.put("hibernate.column_ordering_strategy", "legacy");
        // A field called rootMils is the column root_mils, as every table in the schema spells it.
        properties.put(
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        bean.setJpaPropertyMap(properties);
        return bean;
    }

    /** The URL as configured, which is what the command line prints. */
    public String url() {
        return url;
    }

    public DataSource dataSource() {
        return pool;
    }

    /** The factory, built and checked against the schema the first time it is asked for. */
    public synchronized EntityManagerFactory entityManagerFactory() {
        if (factory == null) {
            ownedFactory = entityManagerFactoryBean(pool, "validate");
            ownedFactory.afterPropertiesSet();
            factory = ownedFactory.getObject();
        }
        return factory;
    }

    /** A fresh session with its transaction begun. Closing it rolls back anything uncommitted. */
    public Session session() {
        return new Session(entityManagerFactory().createEntityManager());
    }

    /** A session that commits when {@code work} returns and rolls back when it throws. */
    public void inSession(Consumer<Session> work) {
        fromSession(session -> {
            work.accept(session);
            return null;
        });
    }

    /** What {@code work} answers, in a session that commits when it returns and rolls back when it throws. */
    public <T> T fromSession(Function<Session, T> work) {
        try (Session session = session()) {
            T answer = work.apply(session);
            session.commit();
            return answer;
        }
    }

    /** Build every table, or bring an older schema up to date. Running it twice changes nothing. */
    public void migrate() {
        flyway().migrate();
    }

    /** Take the schema out. Everything in it goes with it. */
    public void drop() {
        closeFactory();
        flyway().clean();
    }

    /** The migrations applied so far, oldest first, by version number. */
    public List<String> appliedMigrations() {
        return java.util.Arrays.stream(flyway().info().applied())
                .map(item -> item.getVersion().getVersion())
                .toList();
    }

    /** The tables in the database, lower case and sorted, the migrations' own bookkeeping aside. */
    public List<String> tableNames() {
        try (Connection connection = pool.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            TreeSet<String> names = new TreeSet<>();
            try (ResultSet tables = metadata.getTables(null, "PUBLIC", "%", new String[] {"TABLE"})) {
                while (tables.next()) {
                    names.add(tables.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
                }
            }
            names.remove(HISTORY_TABLE);
            return List.copyOf(names);
        } catch (SQLException trouble) {
            throw new DatabaseUnavailable(url, trouble);
        }
    }

    /** Ask the database to answer. Refuses, saying why, when it will not. */
    public void ping() {
        try (Connection connection = pool.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
        } catch (SQLException trouble) {
            throw new DatabaseUnavailable(url, trouble);
        }
    }

    /** Close what this database opened, and let a database in memory go. Safe to call twice. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        closeFactory();
        if (url.startsWith(MEMORY)) {
            try (Connection connection = pool.getConnection();
                    Statement statement = connection.createStatement()) {
                statement.execute("SHUTDOWN");
            } catch (SQLException alreadyGone) {
                // A database that cannot be reached any more has already let its memory go.
                pool.close();
                return;
            }
        }
        pool.close();
    }

    private synchronized void closeFactory() {
        if (ownedFactory != null) {
            ownedFactory.destroy();
            ownedFactory = null;
            factory = null;
        }
    }

    private Flyway flyway() {
        return Flyway.configure()
                .dataSource(pool)
                .locations(MIGRATIONS)
                .cleanDisabled(false)
                .load();
    }
}
