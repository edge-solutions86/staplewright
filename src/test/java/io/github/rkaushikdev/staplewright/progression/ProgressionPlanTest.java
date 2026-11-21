package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProgressionPlanTest {

    private static final Bitting MASTER = b("342156");
    private static final List<Integer> PROGRESSED = List.of(2, 4, 6);

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static ProgressionPlan aPlan() {
        return new ProgressionPlan(Charts.sixPinChart(), MASTER, PROGRESSED, 2);
    }

    private static ProgressionPlan aPlan(Bitting master) {
        return new ProgressionPlan(Charts.sixPinChart(), master, PROGRESSED, 2);
    }

    private static ProgressionPlan aPlan(List<Integer> progressed) {
        return new ProgressionPlan(Charts.sixPinChart(), MASTER, progressed, 2);
    }

    private static ProgressionPlan aPlan(Bitting master, List<Integer> progressed) {
        return new ProgressionPlan(Charts.sixPinChart(), master, progressed, 2);
    }

    @Test
    void twoStepDepthsComeOffTheMasterInBothDirections() {
        assertThat(ProgressionPlan.availableDepths(Charts.sixPinChart(), 4, 2)).containsExactly(0, 2, 6, 8);
    }

    @Test
    void theMasterCutIsNeverInItsOwnList() {
        assertThat(ProgressionPlan.availableDepths(Charts.sixPinChart(), 4, 2)).doesNotContain(4);
    }

    @Test
    void oneStepProgressionUsesEveryOtherDepthOnTheChart() {
        assertThat(ProgressionPlan.availableDepths(Charts.sixPinChart(), 4, 1))
                .containsExactly(0, 1, 2, 3, 5, 6, 7, 8, 9);
    }

    @Test
    void aMasterCutAtTheEndOfTheChartHasRoomOneWayOnly() {
        assertThat(ProgressionPlan.availableDepths(Charts.sixPinChart(), 0, 2)).containsExactly(2, 4, 6, 8);
        assertThat(ProgressionPlan.availableDepths(Charts.sixPinChart(), 9, 2)).containsExactly(1, 3, 5, 7);
    }

    @Test
    void aChartWithAHoleInItLosesThatDepth() {
        DepthAndSpaceChart chart = Charts.sixPinChart(c -> c.depths(List.of(0, 1, 2, 3, 4, 5, 7, 8, 9)));
        assertThat(ProgressionPlan.availableDepths(chart, 4, 2)).containsExactly(0, 2, 8);
    }

    @Test
    void aDepthTheChartNeverPublishedIsRefused() {
        DepthAndSpaceChart chart = Charts.sixPinChart(c -> c.depths(List.of(0, 1, 2)));
        assertThatThrownBy(() -> ProgressionPlan.availableDepths(chart, 7, 2)).isInstanceOf(ProgressionError.class);
    }

    @Test
    void aStepOfZeroIsRefused() {
        assertThatThrownBy(() -> ProgressionPlan.availableDepths(Charts.sixPinChart(), 4, 0))
                .isInstanceOf(ProgressionError.class);
    }

    @Test
    void constantsAreEverythingThePlanDidNotName() {
        assertThat(aPlan().constants()).containsExactly(1, 3, 5);
    }

    @Test
    void aConstantChamberOffersOnlyTheMasterCut() {
        ProgressionPlan plan = aPlan();
        assertThat(plan.depthsIn(1)).containsExactly(3);
        assertThat(plan.depthsIn(3)).containsExactly(2);
    }

    @Test
    void aProgressedChamberOffersItsWholeRun() {
        assertThat(aPlan().depthsIn(2)).containsExactly(0, 2, 6, 8);
    }

    @Test
    void capacityIsTheProductOfTheMovingChambers() {
        ProgressionPlan plan = aPlan();
        assertThat(plan.depthsIn(2)).hasSize(4);
        assertThat(plan.depthsIn(4)).hasSize(4);
        assertThat(plan.depthsIn(6)).hasSize(4);
        assertThat(plan.capacity()).isEqualTo(64);
    }

    @Test
    void capacityShrinksWhenAMasterCutSitsAtTheEndOfTheChart() {
        assertThat(aPlan(b("340150")).capacity()).isEqualTo(4 * 4 * 4);
        assertThat(aPlan(b("342156"), List.of(2)).capacity()).isEqualTo(4);
    }

    @Test
    void everyCombinationIsProducedOnce() {
        List<Bitting> keys = aPlan().combinations().toList();
        assertThat(keys).hasSize(64);
        assertThat(new HashSet<>(keys)).hasSize(64);
    }

    @Test
    void theLeftmostMovingChamberTurnsOverSlowest() {
        List<String> keys =
                aPlan(List.of(2, 6)).combinations().map(Bitting::render).toList();
        assertThat(keys.subList(0, 5)).containsExactly("302150", "302152", "302154", "302158", "322150");
    }

    @Test
    void theConstantsNeverMove() {
        aPlan().combinations().forEach(key -> {
            assertThat(key.at(1)).isEqualTo(3);
            assertThat(key.at(3)).isEqualTo(2);
            assertThat(key.at(5)).isEqualTo(5);
        });
    }

    @Test
    void theMasterItselfIsNotOneOfTheCombinations() {
        assertThat(aPlan().combinations().toList()).doesNotContain(b("342156"));
    }

    @Test
    void aPlanRecognisesItsOwnKeys() {
        ProgressionPlan plan = aPlan();
        assertThat(plan.holds(b("302350"))).isTrue();
        assertThat(plan.holds(b("312350"))).isFalse();
        assertThat(plan.holds(b("402350"))).isFalse();
        assertThat(plan.holds(b("342156"))).isFalse();
        assertThat(plan.holds(b("3023"))).isFalse();
    }

    @Test
    void theOrderNamesTheFastestChamberFirst() {
        assertThat(aPlan().order()).containsExactly(6, 4, 2);
    }

    @Test
    void aMasterOfTheWrongLengthIsRefused() {
        assertThatThrownBy(() -> aPlan(b("3421"))).isInstanceOf(ProgressionError.class);
    }

    @Test
    void aPlanWithNothingMovingIsRefused() {
        assertThatThrownBy(() -> aPlan(List.of())).isInstanceOf(ProgressionError.class);
    }

    @Test
    void aChamberNamedTwiceIsRefused() {
        assertThatThrownBy(() -> aPlan(List.of(2, 2, 4))).isInstanceOf(ProgressionError.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -1})
    void aChamberOffTheEndIsRefused(int chamber) {
        assertThatThrownBy(() -> aPlan(List.of(chamber))).isInstanceOf(ProgressionError.class);
    }

    @Test
    void aChamberWithNowhereToGoIsRefused() {
        DepthAndSpaceChart chart = Charts.fivePinChart(c -> c.depths(List.of(0, 1, 2)));
        ProgressionError caught = catchThrowableOfType(
                ProgressionError.class, () -> new ProgressionPlan(chart, b("10101"), List.of(1), 4));
        assertThat(caught.facts()).containsEntry("chamber", 1);
    }

    @Test
    void aPatternBuildsThePlanTheScheduleDrew() {
        ProgressionPlan plan =
                ProgressionPlan.planFromPattern(Charts.sixPinChart(), b("342156"), Bitting.parsePattern("3x2x5x", 6));
        assertThat(plan.progressed()).containsExactly(2, 4, 6);
        assertThat(plan.capacity()).isEqualTo(64);
    }

    @Test
    void aPatternOfTheWrongLengthIsRefused() {
        assertThatThrownBy(() -> ProgressionPlan.planFromPattern(
                        Charts.sixPinChart(), b("342156"), Bitting.parsePattern("3x2x", 4)))
                .isInstanceOf(ProgressionError.class);
    }

    @Test
    void thePlanDescribesItselfForTheCoverSheet() {
        assertThat(aPlan().describe()).isEqualTo("master 342156, chambers 2,4,6 progressed 2 steps, holds 64");
    }
}
