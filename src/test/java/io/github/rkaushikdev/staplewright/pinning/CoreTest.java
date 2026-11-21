package io.github.rkaushikdev.staplewright.pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CoreTest {

    private static final int COLUMN = 600;
    private static final int GAP = 50;

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static CoreSpec aSpec() {
        return aSpec(COLUMN, GAP);
    }

    private static CoreSpec aSpec(int column, int gap) {
        PinningSpec pinning = new PinningSpec(Charts.sevenPinCoreChart(), 100, column, 25, 75);
        return new CoreSpec(pinning, gap);
    }

    @Test
    void theControlBreakSitsTheGapAboveTheOperatingOne() {
        CoreSpec spec = aSpec();
        assertThat(spec.operatingBreak(4)).isEqualTo(200);
        assertThat(spec.controlBreak(4)).isEqualTo(250);
    }

    @Test
    void aControlGapOfNothingIsRefused() {
        assertThatThrownBy(() -> aSpec(COLUMN, 0)).isInstanceOf(PinningError.class);
    }

    @Test
    void aControlGapThinnerThanAWaferIsRefused() {
        assertThatThrownBy(() -> aSpec(COLUMN, 10)).isInstanceOf(PinningError.class);
    }

    @Test
    void aCoreChamberCarriesAControlWaferEvenWithOneKey() {
        CoreStack stack = CoreStack.build(aSpec(), 1, List.of(4), 4);
        assertThat(stack.wafersMils()).containsExactly(50);
        assertThat(stack.controlWaferMils()).isEqualTo(50);
        assertThat(stack.operatingBreaks()).containsExactly(200);
        assertThat(stack.pieceCount()).isEqualTo(3);
    }

    @Test
    void theColumnStillAddsUpToTheChamberHeight() {
        CoreSpec spec = aSpec();
        CoreStack stack = CoreStack.build(spec, 1, List.of(2, 6), 5);
        assertThat(stack.heightMils()).isEqualTo(spec.pinning().columnMils());
    }

    @Test
    void theControlBreakIsAboveEveryOperatingBreak() {
        CoreStack stack = CoreStack.build(aSpec(), 1, List.of(2, 6), 5);
        assertThat(stack.controlBreak()).isGreaterThan(Collections.max(stack.operatingBreaks()));
    }

    @Test
    void aMasteredChamberHoldsItsWafersUnderTheControlWafer() {
        CoreStack stack = CoreStack.build(aSpec(), 1, List.of(2, 6), 6);
        assertThat(stack.wafersMils()).containsExactly(100, 50);
        assertThat(stack.controlWaferMils()).isEqualTo(50);
    }

    @Test
    void aControlCutTooShallowLeavesNoRoomForTheWafer() {
        PinningError caught =
                catchThrowableOfType(PinningError.class, () -> CoreStack.build(aSpec(), 1, List.of(2, 6), 3));
        assertThat(caught.facts()).containsEntry("deepest_operating", 6);
    }

    @Test
    void aControlCutTooDeepLeavesNoRoomForADriver() {
        PinningError caught =
                catchThrowableOfType(PinningError.class, () -> CoreStack.build(aSpec(400, GAP), 1, List.of(2), 9));
        assertThat(caught.facts()).containsEntry("shortest", 75);
    }

    @Test
    void theWorkableControlCutsAreARunInTheMiddle() {
        List<Integer> workable = aSpec(400, GAP).controlCutRange(List.of(2, 6));
        assertThat(workable).containsExactly(5, 6, 7);
    }

    @Test
    void everyWorkableControlCutActuallyBuilds() {
        CoreSpec spec = aSpec(400, GAP);
        for (int depth : spec.controlCutRange(List.of(2, 6))) {
            CoreStack.build(spec, 1, List.of(2, 6), depth);
        }
    }

    @Test
    void aCutOutsideTheRangeDoesNotBuild() {
        CoreSpec spec = aSpec(400, GAP);
        Set<Integer> workable = Set.copyOf(spec.controlCutRange(List.of(2, 6)));
        for (int depth : spec.pinning().chart().depths()) {
            if (workable.contains(depth)) {
                continue;
            }
            assertThatThrownBy(() -> CoreStack.build(spec, 1, List.of(2, 6), depth))
                    .isInstanceOf(PinningError.class);
        }
    }

    @Test
    void aChamberWithNoOperatingCutsHasNoRange() {
        assertThatThrownBy(() -> aSpec().controlCutRange(List.of())).isInstanceOf(PinningError.class);
    }

    @Test
    void aWholeCoreIsPinnedForItsKeysAndTheControl() {
        CorePinning core = CorePinning.pin(aSpec(), List.of(b("2462462"), b("2662462")), b("6666666"));
        assertThat(core.chambers()).isEqualTo(7);
        assertThat(core.pieceCount()).isEqualTo(7 * 3 + 1);
        assertThat(core.stack(2).wafersMils()).containsExactly(50, 50);
    }

    @Test
    void thePartsListHasAWaferForEveryControlBreak() {
        CorePinning core = CorePinning.pin(aSpec(), List.of(b("2462462")), b("6666666"));
        List<PinSegment> wafers = core.parts().stream()
                .filter(part -> part.kind() == SegmentKind.WAFER)
                .toList();
        assertThat(wafers).hasSize(7);
    }

    @Test
    void aCoreWithNoKeysIsRefused() {
        assertThatThrownBy(() -> CorePinning.pin(aSpec(), List.of(), b("6666666")))
                .isInstanceOf(PinningError.class);
    }

    @Test
    void aControlKeyOfTheWrongLengthIsRefused() {
        assertThatThrownBy(() -> CorePinning.pin(aSpec(), List.of(b("2462462")), b("666666")))
                .isInstanceOf(PinningError.class);
    }

    @Test
    void askingACoreAboutAChamberItDoesNotHave() {
        CorePinning core = CorePinning.pin(aSpec(), List.of(b("2462462")), b("6666666"));
        assertThatThrownBy(() -> core.stack(9)).isInstanceOf(PinningError.class);
    }
}
