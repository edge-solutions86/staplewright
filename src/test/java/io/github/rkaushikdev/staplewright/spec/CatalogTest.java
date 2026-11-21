package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.pinning.CoreStack;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CatalogTest {

    @Test
    void everyChartIsInternallyConsistent() {
        for (DepthAndSpaceChart chart : Catalog.CHARTS) {
            assertThat(chart.depths()).contains(chart.deepest());
            assertThat(chart.dimensionFor(chart.deepest())).isPositive();
            assertThat(chart.spaces()).hasSize(chart.chambers());
        }
    }

    @Test
    void theChartsAreNamedOnceEach() {
        List<Map.Entry<String, String>> keys = Catalog.CHARTS.stream()
                .map(chart -> Map.entry(chart.manufacturer(), chart.family()))
                .toList();
        assertThat(keys).hasSameSizeAs(Set.copyOf(keys));
    }

    @Test
    void aWithdrawnDepthIsMissingFromThatChart() {
        DepthAndSpaceChart withdrawn = Catalog.CHARTS.stream()
                .filter(chart -> chart.family().contains("withdrawn"))
                .findFirst()
                .orElseThrow();
        assertThat(withdrawn.depths()).doesNotContain(4);
        assertThat(withdrawn.allowsDepth(4)).isFalse();
    }

    @Test
    void thePadlockChartHasTheTightMacs() {
        assertThat(Catalog.CORBIN_60.macs()).isEqualTo(4);
        assertThat(Catalog.CORBIN_60.chambers()).isEqualTo(5);
    }

    @Test
    void theCoreChartIsSevenChambers() {
        assertThat(Catalog.BEST_A2.chambers()).isEqualTo(7);
        assertThat(Catalog.BEST_A2.incrementMils()).isEqualTo(25);
    }

    @Test
    void theCatalogueIsKeyedTheWayAPartNumberReads() {
        Map<String, Family> found = Catalog.catalogue();
        assertThat(found).containsKey("Sargent/LA six pin");
        assertThat(found.get("Sargent/LA six pin").chart()).isSameAs(Catalog.SARGENT_LA);
    }

    @Test
    void aFamilyCanBeAskedForByName() {
        Family found = Catalog.family("Best", "A2 seven pin core");
        assertThat(found.chart()).isSameAs(Catalog.BEST_A2);
        assertThat(found.takesCores()).isTrue();
    }

    @Test
    void aFamilyNobodyStocks() {
        assertThatThrownBy(() -> Catalog.family("Medeco", "biaxial")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theFamiliesOfOneManufacturerComeBackTogether() {
        List<Family> found = Catalog.familiesFor("Sargent");
        assertThat(found).hasSize(2);
        assertThat(found)
                .allSatisfy(item -> assertThat(item.chart().manufacturer()).isEqualTo("Sargent"));
    }

    @Test
    void aManufacturerNobodyStocksHasNoFamilies() {
        assertThat(Catalog.familiesFor("Medeco")).isEmpty();
    }

    @Test
    void theManufacturersAreListedOnceEach() {
        assertThat(Catalog.manufacturers()).containsExactly("Best", "Corbin", "Sargent", "Schlage");
    }

    @Test
    void onlyTheCoreFamilyTakesCores() {
        List<Family> found = Catalog.coreFamilies();
        assertThat(found.stream().map(item -> item.chart().manufacturer())).containsExactly("Best");
        assertThat(found.get(0).core()).isPresent();
    }

    @Test
    void everyFamilyCarriesAPinningSpecForItsOwnChart() {
        for (Family item : Catalog.FAMILIES) {
            assertThat(item.pinning().chart()).isSameAs(item.chart());
        }
    }

    @Test
    void aConventionalFamilyPinsACylinder() {
        Family item = Catalog.family("Sargent", "LA six pin");
        CylinderPinning pinning = CylinderPinning.pin(item.pinning(), List.of(Bitting.parse("342156")));
        assertThat(pinning.chambers()).isEqualTo(6);
        assertThat(pinning.stacks().get(0).heightMils())
                .isEqualTo(item.pinning().columnMils());
    }

    @Test
    void theCoreFamilyPinsACore() {
        Family item = Catalog.family("Best", "A2 seven pin core");
        CoreStack stack = CoreStack.build(item.core().orElseThrow(), 1, List.of(2, 6), 6);
        assertThat(stack.controlWaferMils()).isEqualTo(50);
    }

    @Test
    void thePadlockFamilyPinsFiveChambers() {
        Family item = Catalog.family("Corbin", "60 keyway five pin");
        CylinderPinning pinning = CylinderPinning.pin(item.pinning(), List.of(Bitting.parse("13024")));
        assertThat(pinning.chambers()).isEqualTo(5);
    }

    @Test
    void everyFamilyHasAReadableKey() {
        for (Family item : Catalog.FAMILIES) {
            assertThat(item.key()).contains("/");
            assertThat(item.key()).startsWith(item.chart().manufacturer());
        }
    }
}
