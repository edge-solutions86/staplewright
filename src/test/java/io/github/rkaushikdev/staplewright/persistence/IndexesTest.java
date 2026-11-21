package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The indexes exist and the queries that need them use them.
 *
 * <p>A test that an index exists is nearly worthless on its own, because the question is always
 * whether the planner picks it. So these ask the database for the plan and look for the index by
 * name, which is the only thing that would have caught the two indexes that were on the wrong
 * column order.
 */
class IndexesTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
        session.commit();
    }

    @AfterEach
    void close() {
        session.close();
    }

    /** The plan the database would follow for a query, as one lower case line. */
    private String planFor(String sql) {
        @SuppressWarnings("unchecked")
        List<Object> rows =
                session.entityManager().createNativeQuery("explain " + sql).getResultList();
        return rows.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(" "))
                .toLowerCase(Locale.ROOT);
    }

    private Set<String> indexesOn(String table) {
        @SuppressWarnings("unchecked")
        List<Object> names = session.entityManager()
                .createNativeQuery(
                        "select index_name from information_schema.indexes where table_schema = 'PUBLIC' and table_name = ?1")
                .setParameter(1, table.toUpperCase(Locale.ROOT))
                .getResultList();
        return names.stream()
                .map(name -> String.valueOf(name).toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    @Test
    void theKeyIndexesAreOnTheTable() {
        assertThat(indexesOn("issued_keys"))
                .contains("ix_issued_keys_holder_state", "ix_issued_keys_state_due", "ix_issued_keys_symbol_state");
    }

    @Test
    void theCylinderIndexesAreOnTheTable() {
        assertThat(indexesOn("cylinders")).contains("ix_cylinders_installed_at", "ix_cylinders_group_id");
    }

    @Test
    void theOpeningIndexesAreOnTheTable() {
        assertThat(indexesOn("openings")).contains("ix_openings_building", "ix_openings_function");
    }

    @Test
    void whatAPersonHoldsUsesTheHolderIndex() {
        assertThat(planFor("select * from issued_keys where keyholder = 'kmaki' and state = 'out'"))
                .contains("ix_issued_keys_holder_state");
    }

    @Test
    void theOverdueListUsesTheStateIndex() {
        assertThat(planFor("select * from issued_keys where state = 'out' and due_on < date '2025-06-30'"))
                .contains("ix_issued_keys_state_due");
    }

    @Test
    void theKeysCutToASymbolUseTheSymbolIndex() {
        assertThat(planFor("select * from issued_keys where symbol = 'AA1' and state = 'out'"))
                .contains("ix_issued_keys_symbol_state");
    }

    @Test
    void theCylindersOnADoorUseTheInstalledIndex() {
        assertThat(planFor("select * from cylinders where installed_at = 'MOO-214'"))
                .contains("ix_cylinders_installed_at");
    }

    @Test
    void theCylindersInAGroupUseTheGroupIndex() {
        assertThat(planFor("select * from cylinders where group_id = 1")).contains("ix_cylinders_group_id");
    }

    @Test
    void theDoorsInABuildingUseTheBuildingIndex() {
        assertThat(planFor("select * from openings where building_code = 'MOO'"))
                .contains("ix_openings_building");
    }

    @Test
    void theIndexesDoNotChangeAnyAnswer() {
        assertThat(new KeyRepository(session).heldBy("phamm").stream().map(IssuedKeyRow::getTag))
                .containsExactly("41210", "41211");
        assertThat(new CylinderRepository(session).onOpening("MOO-214").stream().map(CylinderRow::getSerial))
                .containsExactly("C-1001");
    }

    @Test
    void everyIndexThisSchemaDeclaresIsNamed() {
        int seen = 0;
        for (Class<?> row : Schema.rows()) {
            for (Index index : row.getAnnotation(Table.class).indexes()) {
                assertThat(index.name()).as(Schema.tableOf(row)).isNotEmpty();
                assertThat(index.name()).matches("(ix|ux)_.+");
                seen++;
            }
        }
        assertThat(seen).isGreaterThan(10);
    }
}
