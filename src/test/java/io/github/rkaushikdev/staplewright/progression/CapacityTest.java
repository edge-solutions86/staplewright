package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CapacityTest {

    private static final List<Integer> PROGRESSED = List.of(2, 4);

    private static ProgressionPlan aPlan() {
        return aPlan(PROGRESSED, 2);
    }

    private static ProgressionPlan aPlan(int step) {
        return aPlan(PROGRESSED, step);
    }

    private static ProgressionPlan aPlan(List<Integer> progressed, int step) {
        return new ProgressionPlan(
                Charts.sixPinChart(chart -> chart.macs(5)), Bitting.parse("342156"), progressed, step);
    }

    private static KeyBittingArray anArray() {
        return new KeyBittingArray(aPlan());
    }

    @Test
    void headroomCountsWhatIsLeft() {
        Headroom room = Capacity.headroom(anArray(), 4);
        assertThat(room.used()).isEqualTo(4);
        assertThat(room.free()).isEqualTo(room.cuttable() - 4);
        assertThat(room.capacity()).isGreaterThanOrEqualTo(room.cuttable());
    }

    @Test
    void theReserveIsAFractionOfTheCuttableKeys() {
        Headroom room = Capacity.headroom(anArray(), 0, 0.5);
        assertThat(room.reserveWanted()).isEqualTo(Math.floorDiv(room.cuttable(), 2));
    }

    @Test
    void aSystemUnderItsReserveSaysSo() {
        KeyBittingArray array = anArray();
        Headroom room = Capacity.headroom(array, array.usableCount(), 0.2);
        assertThat(room.underReserve()).isTrue();
        assertThat(room.reserveLeft()).isNegative();
    }

    @Test
    void aSystemWithRoomIsNotUnderItsReserve() {
        assertThat(Capacity.headroom(anArray(), 0).underReserve()).isFalse();
    }

    @Test
    void aFullSystemHasNoFreeKeys() {
        KeyBittingArray array = anArray();
        Headroom room = Capacity.headroom(array, array.usableCount() + 5);
        assertThat(room.free()).isZero();
        assertThat(room.usedFraction()).isGreaterThan(1);
    }

    @Test
    void theUsedFractionIsRoundedForAReport() {
        KeyBittingArray array = anArray();
        Headroom room = Capacity.headroom(array, Math.floorDiv(array.usableCount(), 2));
        assertThat(room.usedFraction()).isGreaterThan(0.4).isLessThan(0.6);
    }

    @Test
    void headroomDescribesItself() {
        assertThat(Capacity.headroom(anArray(), 3).describe()).contains("free");
    }

    static Stream<Function<KeyBittingArray, Headroom>> headroomThatMakesNoSenseIsRefused() {
        return Stream.of(array -> Capacity.headroom(array, -1), array -> Capacity.headroom(array, 0, 1.0));
    }

    @ParameterizedTest
    @MethodSource
    void headroomThatMakesNoSenseIsRefused(Function<KeyBittingArray, Headroom> broken) {
        KeyBittingArray array = anArray();
        assertThatThrownBy(() -> broken.apply(array)).isInstanceOf(InputError.class);
    }

    @Test
    void theYearsLeftFallOutOfTheRate() {
        Headroom room = Capacity.headroom(anArray(), 0, 0);
        assertThat(Capacity.yearsLeft(room, Math.floorDiv(room.free(), 4))).contains(4);
    }

    @Test
    void aSystemNobodyIsGrowingDoesNotRunOut() {
        assertThat(Capacity.yearsLeft(Capacity.headroom(anArray(), 0), 0)).isEmpty();
    }

    @Test
    void aSystemAlreadyPastItsReserveHasNoYears() {
        KeyBittingArray array = anArray();
        Headroom room = Capacity.headroom(array, array.usableCount());
        assertThat(Capacity.yearsLeft(room, 5)).contains(0);
    }

    @Test
    void aNegativeRateIsRefused() {
        assertThatThrownBy(() -> Capacity.yearsLeft(Capacity.headroom(anArray(), 0), -1))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theForecastWalksForwardAtThisYearsRate() {
        assertThat(Capacity.forecast(10, 6, 4)).isEqualTo(34);
        assertThat(Capacity.forecast(10, 0, 4)).isEqualTo(10);
    }

    @Test
    void aForecastCannotRunBackward() {
        assertThatThrownBy(() -> Capacity.forecast(10, 6, -1)).isInstanceOf(InputError.class);
    }

    @Test
    void progressingAnotherChamberIsOffered() {
        List<String> names = Capacity.wideningOptions(aPlan()).stream()
                .map(WideningOption::name)
                .toList();
        assertThat(names).anyMatch(name -> name.startsWith("progress chamber"));
    }

    @Test
    void everyOptionIsWiderThanThePlan() {
        ProgressionPlan plan = aPlan();
        int current = new KeyBittingArray(plan).usableCount();
        for (WideningOption option : Capacity.wideningOptions(plan)) {
            assertThat(option.gainOver(current)).isPositive();
        }
    }

    @Test
    void aChamberAlreadyProgressedIsNotOfferedAgain() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan());
        assertThat(options).noneMatch(option -> option.name().equals("progress chamber 2"));
    }

    @Test
    void progressingAChamberCostsAWafer() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan());
        WideningOption chamber = options.stream()
                .filter(option -> option.name().startsWith("progress chamber"))
                .findFirst()
                .orElseThrow();
        assertThat(chamber.wafersAdded()).isEqualTo(1);
    }

    @Test
    void aTighterStepIsOfferedAndCostsNoWafer() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan());
        List<WideningOption> tighter = options.stream()
                .filter(option -> option.name().startsWith("progress 1 steps"))
                .toList();
        assertThat(tighter).isNotEmpty();
        assertThat(tighter.get(0).wafersAdded()).isZero();
        assertThat(tighter.get(0).thinnestSplit()).isEqualTo(15);
    }

    @Test
    void aOneStepPlanOffersNoTighterStep() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan(1));
        assertThat(options).noneMatch(option -> option.name().contains("steps instead"));
    }

    @Test
    void theWidestOptionComesFirst() {
        List<Integer> capacities = Capacity.wideningOptions(aPlan()).stream()
                .map(WideningOption::capacity)
                .toList();
        assertThat(capacities)
                .isEqualTo(capacities.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Test
    void theBestOptionRespectsTheThinnestWafer() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan());
        Optional<WideningOption> picked = Capacity.bestOption(options, 30);
        assertThat(picked).isPresent();
        assertThat(picked.get().thinnestSplit()).isGreaterThanOrEqualTo(30);
    }

    @Test
    void nothingWorkableComesBackAsNothing() {
        List<WideningOption> options = Capacity.wideningOptions(aPlan());
        assertThat(Capacity.bestOption(options, 500)).isEmpty();
    }

    @Test
    void aPlanWithNothingLeftToWidenOffersNothing() {
        ProgressionPlan plan = aPlan(List.of(1, 2, 3, 4, 5, 6), 1);
        assertThat(Capacity.wideningOptions(plan)).isEmpty();
    }
}
