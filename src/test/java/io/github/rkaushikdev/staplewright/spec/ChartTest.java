package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.BittingError;
import io.github.rkaushikdev.staplewright.SpecificationError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ChartTest {

    private static final List<Integer> WITHOUT_FIVE = List.of(0, 1, 2, 3, 4, 6, 7, 8, 9);

    private static DepthAndSpaceChart aSixPinChart() {
        return Charts.sixPinChart();
    }

    private static DepthAndSpaceChart aSixPinChart(UnaryOperator<DepthAndSpaceChart.Builder> overrides) {
        return Charts.sixPinChart(overrides);
    }

    @Test
    void theShallowestCutSitsAtTheRoot() {
        assertThat(aSixPinChart().dimensionFor(0)).isEqualTo(335);
    }

    @Test
    void eachStepTakesOneIncrementOfMetal() {
        DepthAndSpaceChart chart = aSixPinChart();
        assertThat(chart.dimensionFor(1)).isEqualTo(320);
        assertThat(chart.dimensionFor(9)).isEqualTo(200);
    }

    @Test
    void aDepthTheChartSkippedIsNotCuttable() {
        DepthAndSpaceChart chart = aSixPinChart(chart1 -> chart1.depths(WITHOUT_FIVE));
        assertThat(chart.allowsDepth(5)).isFalse();
        assertThatThrownBy(() -> chart.dimensionFor(5)).isInstanceOf(BittingError.class);
    }

    @Test
    void spacingWalksAlongTheBlade() {
        DepthAndSpaceChart chart = aSixPinChart();
        assertThat(chart.spaceFor(1)).isEqualTo(231);
        assertThat(chart.spaceFor(2)).isEqualTo(387);
        assertThat(chart.spaces()).containsExactly(231, 387, 543, 699, 855, 1011);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -3})
    void askingAboutAChamberThatIsNotThere(int chamber) {
        assertThatThrownBy(() -> aSixPinChart().spaceFor(chamber)).isInstanceOf(BittingError.class);
    }

    @Test
    void aMeasuredKeyReadsBackToADepth() {
        DepthAndSpaceChart chart = aSixPinChart();
        assertThat(chart.depthForDimension(320)).isEqualTo(1);
        assertThat(chart.depthForDimension(319)).isEqualTo(1);
        assertThat(chart.depthForDimension(322)).isEqualTo(1);
    }

    @Test
    void aMeasurementBetweenDepthsIsRefused() {
        assertThatThrownBy(() -> aSixPinChart().depthForDimension(312)).isInstanceOf(BittingError.class);
    }

    @Test
    void aWiderToleranceAcceptsTheSameMeasurement() {
        assertThat(aSixPinChart(chart -> chart.depthToleranceMils(8)).depthForDimension(314))
                .isEqualTo(1);
    }

    @Test
    void aKeyOfTheRightShapeValidates() {
        DepthAndSpaceChart chart = aSixPinChart();
        chart.validate(Bitting.parse("342156"));
        assertThat(chart.isCuttable(Bitting.parse("342156"))).isTrue();
    }

    @Test
    void aKeyOfTheWrongLengthDoesNot() {
        BittingError caught =
                catchThrowableOfType(BittingError.class, () -> aSixPinChart().validate(Bitting.parse("3421")));
        assertThat(caught.facts()).containsEntry("got", 4);
    }

    @Test
    void aKeyCarryingAWithdrawnDepthDoesNot() {
        DepthAndSpaceChart chart = aSixPinChart(chart1 -> chart1.depths(WITHOUT_FIVE));
        BittingError caught = catchThrowableOfType(BittingError.class, () -> chart.validate(Bitting.parse("342156")));
        assertThat(caught.facts()).containsEntry("chamber", 5);
        assertThat(chart.isCuttable(Bitting.parse("342156"))).isFalse();
    }

    @Test
    void theChartReportsItsOwnRange() {
        DepthAndSpaceChart chart = aSixPinChart();
        assertThat(chart.shallowest()).isZero();
        assertThat(chart.deepest()).isEqualTo(9);
        assertThat(chart.depthCount()).isEqualTo(10);
        assertThat(chart.describe()).contains("six pin");
    }

    static Stream<UnaryOperator<DepthAndSpaceChart.Builder>> aChartThatContradictsItselfIsRefused() {
        return Stream.of(
                chart -> chart.chambers(0),
                chart -> chart.depths(List.of()),
                chart -> chart.depths(List.of(0, 2, 1)),
                chart -> chart.depths(List.of(0, 0, 1)),
                chart -> chart.incrementMils(0),
                chart -> chart.spaceIncrementMils(-4),
                chart -> chart.macs(0),
                chart -> chart.rootMils(100));
    }

    @ParameterizedTest
    @MethodSource
    void aChartThatContradictsItselfIsRefused(UnaryOperator<DepthAndSpaceChart.Builder> broken) {
        assertThatThrownBy(() -> aSixPinChart(broken)).isInstanceOf(SpecificationError.class);
    }

    @Test
    void notesDoNotChangeWhatAChartIs() {
        assertThat(aSixPinChart(chart -> chart.notes("from the 2011 catalogue")))
                .isEqualTo(aSixPinChart());
        assertThat(aSixPinChart(chart -> chart.notes("from the 2011 catalogue")).hashCode())
                .isEqualTo(aSixPinChart().hashCode());
    }
}
