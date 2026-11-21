package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.blankFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.blankToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.chartFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.chartToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.familyFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ReferenceMappers.familyToRows;
import static io.github.rkaushikdev.staplewright.support.Systems.campusFamily;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.spec.Blank;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import io.github.rkaushikdev.staplewright.support.Charts;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class MappersReferenceTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    /** Write a family out the way a repository would, parents first. */
    private List<KeywaySectionRow> storeFamily(KeywayFamily family) {
        Map<String, KeywaySectionRow> byCode = new HashMap<>();
        List<KeywaySectionRow> rows = new ArrayList<>();
        for (String code : family.codes().stream()
                .sorted(Comparator.comparingInt(family::depthOf))
                .toList()) {
            KeywaySection section = family.section(code);
            KeywaySectionRow row = new KeywaySectionRow(family.name(), section.code(), section.manufacturer());
            row.setRestricted(section.restricted());
            family.parentOf(code).ifPresent(parent -> row.setParent(byCode.get(parent.code())));
            session.add(row);
            session.flush();
            byCode.put(code, row);
            rows.add(row);
        }
        return rows;
    }

    static Stream<DepthAndSpaceChart> charts() {
        return Stream.of(Charts.sixPinChart(), Charts.sevenPinCoreChart(), Charts.fivePinChart());
    }

    @ParameterizedTest
    @MethodSource("charts")
    void aChartSurvivesARoundTrip(DepthAndSpaceChart chart) {
        assertThat(chartFromRow(chartToRow(chart))).isEqualTo(chart);
    }

    @Test
    void aChartRoundTripsThroughTheDatabase() {
        DepthAndSpaceChart chart = Charts.sixPinChart(b -> b.notes("from the 2011 catalogue"));
        session.add(chartToRow(chart));
        session.flush();
        ChartRow row = session.one(ChartRow.class);
        assertThat(chartFromRow(row)).isEqualTo(chart);
        assertThat(chartFromRow(row).notes()).isEqualTo("from the 2011 catalogue");
    }

    @Test
    void aChartWithAWithdrawnDepthRoundTrips() {
        DepthAndSpaceChart chart = Charts.sixPinChart(b -> b.depths(List.of(0, 1, 2, 3, 4, 6, 7, 8, 9)));
        session.add(chartToRow(chart));
        session.flush();
        ChartRow row = session.one(ChartRow.class);
        assertThat(chartFromRow(row).depths()).containsExactly(0, 1, 2, 3, 4, 6, 7, 8, 9);
    }

    @Test
    void aFamilySurvivesARoundTrip() {
        KeywayFamily family = campusFamily();
        List<KeywaySectionRow> rows = storeFamily(family);
        KeywayFamily back = familyFromRows(family.name(), rows);
        assertThat(back.codes()).isEqualTo(family.codes());
        assertThat(back.ancestry("LC")).isEqualTo(family.ancestry("LC"));
        assertThat(back.leaves()).isEqualTo(family.leaves());
    }

    @Test
    void theRestrictedFlagSurvives() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        KeywayFamily back = familyFromRows("Sargent LA", rows);
        assertThat(back.section("LC").restricted()).isTrue();
        assertThat(back.section("LA").restricted()).isFalse();
    }

    @Test
    void theRowsComeOutParentsFirst() {
        List<KeywaySectionRow> rows = familyToRows(campusFamily());
        assertThat(rows.get(0).getCode()).isEqualTo("L");
        Set<String> next =
                rows.subList(1, 3).stream().map(KeywaySectionRow::getCode).collect(Collectors.toSet());
        assertThat(next).isEqualTo(Set.of("LA", "LB"));
    }

    @Test
    void aSetWithNoRootIsRefused() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        List<KeywaySectionRow> children =
                rows.stream().filter(row -> row.getParentId().isPresent()).toList();
        assertThatThrownBy(() -> familyFromRows("Sargent LA", children)).isInstanceOf(KeywayError.class);
    }

    @Test
    void aSetWithTwoRootsIsRefused() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        KeywaySectionRow extra = new KeywaySectionRow("Sargent LA", "M", "Sargent");
        session.add(extra);
        session.flush();
        List<KeywaySectionRow> all = new ArrayList<>(rows);
        all.add(extra);
        assertThatThrownBy(() -> familyFromRows("Sargent LA", all)).isInstanceOf(KeywayError.class);
    }

    @Test
    void aSectionHangingOffNothingIsRefused() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        KeywaySectionRow orphan = rows.get(rows.size() - 1);
        orphan.setParent(session.reference(KeywaySectionRow.class, 9999));
        assertThatThrownBy(() -> familyFromRows("Sargent LA", rows)).isInstanceOf(KeywayError.class);
    }

    @Test
    void aBlankSurvivesARoundTrip() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        KeywaySectionRow section = rows.stream()
                .filter(row -> row.getCode().equals("LC"))
                .findFirst()
                .orElseThrow();
        Blank blank = new Blank("LC-6", "LC", "Sargent", 6, 19);
        BlankRow row = blankToRow(blank, section);
        session.add(row);
        session.flush();
        session.refresh(row);
        assertThat(blankFromRow(row)).isEqualTo(blank);
    }

    @Test
    void aBlankWithNoStockRoundTrips() {
        List<KeywaySectionRow> rows = storeFamily(campusFamily());
        KeywaySectionRow section = rows.stream()
                .filter(row -> row.getCode().equals("LC"))
                .findFirst()
                .orElseThrow();
        BlankRow row = blankToRow(new Blank("LC-7", "LC", "Sargent", 7), section);
        session.add(row);
        session.flush();
        session.refresh(row);
        assertThat(blankFromRow(row).stock()).isZero();
    }

    @Test
    void aOneSectionFamilyRoundTrips() {
        KeywayFamily family = new KeywayFamily("Corbin 60", new KeywaySection("A", "Corbin"));
        List<KeywaySectionRow> rows = storeFamily(family);
        KeywayFamily back = familyFromRows(family.name(), rows);
        assertThat(back.codes()).containsExactly("A");
        assertThat(back.leaves()).containsExactly("A");
    }
}
