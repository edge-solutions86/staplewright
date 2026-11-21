package io.github.rkaushikdev.staplewright.pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StackTest {

    private static PinningSpec aSpec() {
        return new PinningSpec(Charts.sixPinChart(), 150, 480, 30, 90);
    }

    @Test
    void aBottomPinGrowsWithTheCutDepth() {
        PinningSpec spec = aSpec();
        assertThat(spec.bottomPin(0)).isEqualTo(150);
        assertThat(spec.bottomPin(1)).isEqualTo(165);
        assertThat(spec.bottomPin(6)).isEqualTo(240);
    }

    @Test
    void aDepthOffTheChartHasNoPin() {
        assertThatThrownBy(() -> aSpec().bottomPin(11)).isInstanceOf(PinningError.class);
    }

    @Test
    void aWaferIsTheDistanceBetweenTwoBreaks() {
        PinningSpec spec = aSpec();
        assertThat(spec.wafer(2, 4)).isEqualTo(30);
        assertThat(spec.wafer(1, 7)).isEqualTo(90);
    }

    @Test
    void aWaferNeedsTwoDifferentDepths() {
        PinningSpec spec = aSpec();
        assertThatThrownBy(() -> spec.wafer(4, 4)).isInstanceOf(PinningError.class);
        assertThatThrownBy(() -> spec.wafer(6, 2)).isInstanceOf(PinningError.class);
    }

    @Test
    void aChamberWithOneBreakIsAPinAndADriver() {
        PinStack stack = PinStack.build(aSpec(), 1, List.of(3));
        assertThat(stack.bottomMils()).isEqualTo(195);
        assertThat(stack.wafersMils()).isEmpty();
        assertThat(stack.driverMils()).isEqualTo(285);
        assertThat(stack.pieceCount()).isEqualTo(2);
    }

    @Test
    void everyStackReachesTheShearLineExactly() {
        PinningSpec spec = aSpec();
        for (List<Integer> depths : List.of(List.of(3), List.of(3, 5), List.of(1, 5, 9), List.of(0, 2, 4, 6))) {
            assertThat(PinStack.build(spec, 1, depths).heightMils()).isEqualTo(spec.columnMils());
        }
    }

    @Test
    void aMasterChamberCarriesOneWafer() {
        PinStack stack = PinStack.build(aSpec(), 2, List.of(4, 6));
        assertThat(stack.bottomMils()).isEqualTo(210);
        assertThat(stack.wafersMils()).containsExactly(30);
        assertThat(stack.driverMils()).isEqualTo(240);
    }

    @Test
    void theOrderTheDepthsArriveInDoesNotMatter() {
        PinningSpec spec = aSpec();
        assertThat(PinStack.build(spec, 2, List.of(6, 4))).isEqualTo(PinStack.build(spec, 2, List.of(4, 6)));
    }

    @Test
    void aDepthNamedTwiceIsOneBreak() {
        PinStack stack = PinStack.build(aSpec(), 2, List.of(4, 6, 4));
        assertThat(stack.wafersMils()).containsExactly(30);
    }

    @Test
    void breaksAreReportedAsHeightsFromTheFloor() {
        PinStack stack = PinStack.build(aSpec(), 2, List.of(2, 4, 8));
        assertThat(stack.breakHeights()).containsExactly(180, 210, 270);
    }

    @Test
    void aOneStepSplitIsTooThinToStandUp() {
        PinningError caught = catchThrowableOfType(PinningError.class, () -> PinStack.build(aSpec(), 2, List.of(4, 5)));
        assertThat(caught.facts()).containsEntry("thickness", 15);
    }

    @Test
    void aShopThatAcceptsThinWafersGetsThem() {
        PinStack stack = PinStack.build(aSpec().withMinWaferMils(15), 2, List.of(4, 5));
        assertThat(stack.wafersMils()).containsExactly(15);
    }

    @Test
    void tooManyBreaksLeaveNoRoomForADriver() {
        PinningError caught = catchThrowableOfType(
                PinningError.class, () -> PinStack.build(aSpec().withColumnMils(300), 2, List.of(1, 3, 5, 7, 9)));
        assertThat(caught.facts()).containsEntry("shortest", 90);
        assertThat(caught.facts()).containsEntry("driver", 15);
    }

    @Test
    void aTallerCylinderTakesTheSameStack() {
        PinStack stack = PinStack.build(aSpec().withColumnMils(600), 2, List.of(1, 3, 5, 7, 9));
        assertThat(stack.pieceCount()).isEqualTo(6);
    }

    @Test
    void aChamberThatBreaksNowhereIsRefused() {
        assertThatThrownBy(() -> PinStack.build(aSpec(), 2, List.of())).isInstanceOf(PinningError.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, -2})
    void aChamberOffTheEndIsRefused(int chamber) {
        assertThatThrownBy(() -> PinStack.build(aSpec(), chamber, List.of(3))).isInstanceOf(PinningError.class);
    }

    @Test
    void thePartsListReadsBottomThenWafersThenDriver() {
        PinStack stack = PinStack.build(aSpec(), 2, List.of(2, 4, 8));
        List<PinSegment> parts = stack.segments();
        assertThat(parts.stream().map(PinSegment::kind))
                .containsExactly(SegmentKind.BOTTOM, SegmentKind.WAFER, SegmentKind.WAFER, SegmentKind.DRIVER);
        assertThat(parts.stream().map(PinSegment::lengthMils)).containsExactly(180, 30, 60, 210);
    }

    @Test
    void aSecurityDriverCarriesItsProfileIntoThePartsList() {
        PinStack stack = PinStack.build(aSpec(), 2, List.of(4), DriverProfile.SPOOL);
        assertThat(stack.segments().get(stack.segments().size() - 1).profile()).isSameAs(DriverProfile.SPOOL);
        assertThat(stack.segments().get(stack.segments().size() - 1).binCode()).endsWith("S");
    }

    @Test
    void aColumnShorterThanItsBottomPinIsRefused() {
        assertThatThrownBy(() -> aSpec().withColumnMils(120)).isInstanceOf(PinningError.class);
    }

    static Stream<UnaryOperator<PinningSpec>> aSpecThatContradictsItselfIsRefused() {
        return Stream.of(spec -> spec.withBottomAtShallowestMils(0), spec -> spec.withMinWaferMils(0));
    }

    @ParameterizedTest
    @MethodSource
    void aSpecThatContradictsItselfIsRefused(UnaryOperator<PinningSpec> broken) {
        assertThatThrownBy(() -> broken.apply(aSpec())).isInstanceOf(PinningError.class);
    }
}
