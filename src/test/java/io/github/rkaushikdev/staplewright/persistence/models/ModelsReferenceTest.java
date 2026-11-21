package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ModelsReferenceTest {

    private static final List<Integer> ZERO_TO_NINE = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    static ChartRow aChart() {
        return new ChartRow("Schlage", "C keyway, six pin", 6, ZERO_TO_NINE, 335, 15, 231, 156, 7);
    }

    static ChartRow aChart(Consumer<ChartRow> overrides) {
        ChartRow chart = aChart();
        overrides.accept(chart);
        return chart;
    }

    private KeywaySectionRow aFamily() {
        KeywaySectionRow root = new KeywaySectionRow("Sargent LA", "L", "Sargent");
        session.add(root);
        session.flush();
        KeywaySectionRow branch = new KeywaySectionRow("Sargent LA", "LA", "Sargent");
        branch.setParent(root);
        KeywaySectionRow leaf = new KeywaySectionRow("Sargent LA", "LC", "Sargent");
        leaf.setRestricted(true);
        leaf.setParent(branch);
        leaf.setPatentExpires(LocalDate.of(2031, 4, 1));
        session.addAll(List.of(branch, leaf));
        session.flush();
        return root;
    }

    private KeywaySectionRow theSection(String code) {
        return session.first("select s from KeywaySectionRow s where s.code = ?1", KeywaySectionRow.class, code)
                .orElseThrow();
    }

    @Test
    void theReferenceTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("charts", "keyway_sections", "key_blanks");
    }

    @Test
    void aChartRoundTripsWithItsDepthList() {
        session.add(aChart());
        session.expireAll();
        ChartRow found = session.one(ChartRow.class);
        assertThat(found.getDepths()).isEqualTo(ZERO_TO_NINE);
        assertThat(found.getDepthToleranceMils()).isEqualTo(2);
        assertThat(found.getNotes()).isEmpty();
        assertThat(found.toString()).contains("Schlage");
    }

    @Test
    void aChartWithAHoleInItsDepthsRoundTrips() {
        session.add(aChart(chart -> chart.setDepths(List.of(0, 1, 2, 3, 4, 6, 7, 8, 9))));
        session.expireAll();
        assertThat(session.one(ChartRow.class).getDepths()).doesNotContain(5);
    }

    @Test
    void oneFamilyPerManufacturer() {
        session.add(aChart());
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(aChart());
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameFamilyNameUnderAnotherManufacturerIsFine() {
        session.add(aChart());
        session.add(aChart(chart -> chart.setManufacturer("Corbin")));
        session.flush();
        assertThat(session.all(ChartRow.class)).hasSize(2);
    }

    static Stream<Consumer<ChartRow>> aChartThatContradictsItselfIsRefused() {
        return Stream.of(chart -> chart.setChambers(0), chart -> chart.setIncrementMils(0), chart -> chart.setMacs(0));
    }

    @ParameterizedTest
    @MethodSource
    void aChartThatContradictsItselfIsRefused(Consumer<ChartRow> broken) {
        assertThatThrownBy(() -> {
                    session.add(aChart(broken));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSectionTreeReadsDownward() {
        KeywaySectionRow root = aFamily();
        assertThat(root.isRoot()).isTrue();
        assertThat(root.getChildren().stream().map(KeywaySectionRow::getCode)).containsExactly("LA");
        assertThat(root.getChildren().get(0).getChildren().get(0).getCode()).isEqualTo("LC");
    }

    @Test
    void aSectionTreeReadsUpward() {
        aFamily();
        session.expireAll();
        KeywaySectionRow leaf = theSection("LC");
        assertThat(leaf.getParent().orElseThrow().getCode()).isEqualTo("LA");
        assertThat(leaf.getParent().orElseThrow().getParent().orElseThrow().getCode())
                .isEqualTo("L");
        assertThat(leaf.isRoot()).isFalse();
    }

    @Test
    void aPatentDateRoundTrips() {
        aFamily();
        session.expireAll();
        KeywaySectionRow leaf = theSection("LC");
        assertThat(leaf.getPatentExpires()).contains(LocalDate.of(2031, 4, 1));
        assertThat(leaf.isRestricted()).isTrue();
    }

    @Test
    void aCodeIsUniqueInsideAFamily() {
        aFamily();
        assertThatThrownBy(() -> {
                    session.add(new KeywaySectionRow("Sargent LA", "LA", "Sargent"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameCodeInAnotherFamilyIsFine() {
        aFamily();
        session.add(new KeywaySectionRow("Best A2", "LA", "Best"));
        session.flush();
        assertThat(session.all(KeywaySectionRow.class)).hasSize(4);
    }

    @Test
    void aSectionCannotBeItsOwnParent() {
        KeywaySectionRow root = aFamily();
        root.setParent(root);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aBlankHangsOffASection() {
        KeywaySectionRow root = aFamily();
        BlankRow blank = new BlankRow("LA-6", root, "Sargent", 6);
        blank.setStock(40);
        session.add(blank);
        session.expireAll();
        BlankRow found = session.one(BlankRow.class);
        assertThat(found.getSection().getCode()).isEqualTo("L");
        assertThat(found.getReorderAt()).isZero();
        assertThat(found.isRestricted()).isFalse();
        assertThat(found.toString()).contains("LA-6");
    }

    @Test
    void aBlankNeedsASectionThatExists() {
        assertThatThrownBy(() -> {
                    session.add(new BlankRow("LA-6", session.reference(KeywaySectionRow.class, 999), "Sargent", 6));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    static Stream<Consumer<BlankRow>> aBlankThatContradictsItselfIsRefused() {
        return Stream.of(blank -> blank.setChambers(0), blank -> blank.setStock(-1), blank -> blank.setReorderAt(-3));
    }

    @ParameterizedTest
    @MethodSource
    void aBlankThatContradictsItselfIsRefused(Consumer<BlankRow> broken) {
        KeywaySectionRow root = aFamily();
        BlankRow blank = new BlankRow("LA-6", root, "Sargent", 6);
        broken.accept(blank);
        assertThatThrownBy(() -> {
                    session.add(blank);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSectionListsTheBlanksCutOnIt() {
        KeywaySectionRow root = aFamily();
        session.addAll(List.of(new BlankRow("LA-6", root, "Sargent", 6), new BlankRow("LA-7", root, "Sargent", 7)));
        session.flush();
        session.refresh(root);
        assertThat(root.getBlanks().stream().map(BlankRow::getPartNumber).sorted())
                .containsExactly("LA-6", "LA-7");
    }
}
