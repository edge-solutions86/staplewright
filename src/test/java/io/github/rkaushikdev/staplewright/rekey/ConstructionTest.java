package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Charts.sixPinChart;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConstructionTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static BallKeying aKeying() {
        return aKeying(b("542156"));
    }

    private static BallKeying aKeying(Bitting construction) {
        return aKeying(construction, List.of(1), 2);
    }

    private static BallKeying aKeying(Bitting construction, List<Integer> ballChambers, int ballSteps) {
        return new BallKeying(b("342156"), construction, ballChambers, ballSteps);
    }

    @Test
    void aSoundArrangementHasNothingToReport() {
        BallKeying keying = aKeying();
        assertThat(keying.isSound()).isTrue();
        assertThat(keying.problems()).isEmpty();
        assertThat(keying.voidedByFirstUse()).isTrue();
    }

    @Test
    void theChambersWithoutABallHaveToMatch() {
        BallKeying keying = aKeying(b("542356"));
        assertThat(keying.problems().get(0)).contains("chamber 4");
        assertThat(keying.isSound()).isFalse();
    }

    @Test
    void aConstructionCutShallowerThanThePermanentOneIsWrong() {
        BallKeying keying = aKeying(b("142156"));
        assertThat(keying.problems().get(0)).contains("deeper");
    }

    @Test
    void aConstructionCutTheSameAsThePermanentOneIsWrong() {
        BallKeying keying = aKeying(b("342156"));
        assertThat(keying.problems()).isNotEmpty();
        assertThat(keying.voidedByFirstUse()).isFalse();
    }

    @Test
    void theStepHasToBeTheBall() {
        BallKeying keying = aKeying(b("742156"));
        assertThat(keying.problems().get(0)).contains("worth 2");
    }

    @Test
    void aBiggerBallCanBeDeclared() {
        BallKeying keying = aKeying(b("742156"), List.of(1), 4);
        assertThat(keying.isSound()).isTrue();
    }

    @Test
    void twoBallChambersBothHaveToBeRight() {
        BallKeying keying = aKeying(b("542356"), List.of(1, 4), 2);
        assertThat(keying.isSound()).isTrue();
        assertThat(keying.plainChambers()).containsExactly(2, 3, 5, 6);
    }

    @Test
    void aCylinderWithNoBallIsNotConstructionKeyed() {
        assertThatThrownBy(() -> aKeying(b("542156"), List.of(), 2)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aBallInAChamberThatIsNotThere() {
        assertThatThrownBy(() -> aKeying(b("542156"), List.of(9), 2)).isInstanceOf(ConflictError.class);
    }

    @Test
    void twoKeysOfDifferentLengthsAreRefused() {
        assertThatThrownBy(() -> aKeying(b("5421"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aBallWorthNothingIsRefused() {
        assertThatThrownBy(() -> aKeying(b("542156"), List.of(1), 0)).isInstanceOf(InputError.class);
    }

    @Test
    void aConstructionKeyIsCutFromThePermanentOne() {
        Bitting made = BallKeying.buildConstructionKey(sixPinChart(), b("342156"), List.of(1));
        assertThat(made.render()).isEqualTo("542156");
    }

    @Test
    void twoBallChambersBothMove() {
        Bitting made = BallKeying.buildConstructionKey(sixPinChart(), b("342156"), List.of(1, 4));
        assertThat(made.render()).isEqualTo("542356");
    }

    @Test
    void aPermanentCutNearTheBottomLeavesNoRoom() {
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> BallKeying.buildConstructionKey(sixPinChart(), b("942156"), List.of(1)));
        assertThat(caught.facts().get("wanted")).isEqualTo(11);
    }

    @Test
    void aBallChamberThatIsNotOnTheKey() {
        assertThatThrownBy(() -> BallKeying.buildConstructionKey(sixPinChart(), b("342156"), List.of(9)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theWorkableChambersAreTheOnesWithRoom() {
        List<Integer> workable = BallKeying.workableBallChambers(sixPinChart(), b("342156"));
        assertThat(workable).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void aKeyCutDeepLosesChambers() {
        List<Integer> workable = BallKeying.workableBallChambers(sixPinChart(), b("998156"));
        assertThat(workable).containsExactly(4, 5, 6);
    }

    @Test
    void aWiderBallLosesMoreChambers() {
        assertThat(BallKeying.workableBallChambers(sixPinChart(), b("342156"), 6))
                .containsExactly(1, 3, 4);
    }

    @Test
    void aSwapLinesUpBothCoresAgainstADoor() {
        List<CoreSwap> plan =
                CoreSwap.swapPlan(List.of("MOO-214", "MOO-216"), List.of("X-1", "X-2"), List.of("K-1", "K-2"));
        assertThat(plan).extracting(CoreSwap::opening).containsExactly("MOO-214", "MOO-216");
        assertThat(plan.get(0).permanentCore()).isEqualTo("K-1");
    }

    @Test
    void aDoorCannotSwapACoreForItself() {
        assertThatThrownBy(() -> new CoreSwap("MOO-214", "K-1", "K-1")).isInstanceOf(ConflictError.class);
    }

    @Test
    void everyDoorNeedsBothCores() {
        assertThatThrownBy(
                        () -> CoreSwap.swapPlan(List.of("MOO-214", "MOO-216"), List.of("X-1"), List.of("K-1", "K-2")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aPermanentCoreCannotBePromisedTwice() {
        assertThatThrownBy(() ->
                        CoreSwap.swapPlan(List.of("MOO-214", "MOO-216"), List.of("X-1", "X-2"), List.of("K-1", "K-1")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theDoorsStillOnConstructionCoresAreReported() {
        List<String> doors = List.of("MOO-214", "MOO-216", "MOO-218");
        List<CoreSwap> plan = CoreSwap.swapPlan(doors, List.of("X-1", "X-2", "X-3"), List.of("K-1", "K-2", "K-3"));
        assertThat(CoreSwap.unswapped(plan, List.of("MOO-216"))).containsExactly("MOO-214", "MOO-218");
    }

    @Test
    void aFinishedHandoverLeavesNothing() {
        List<CoreSwap> plan = CoreSwap.swapPlan(List.of("MOO-214"), List.of("X-1"), List.of("K-1"));
        assertThat(CoreSwap.unswapped(plan, List.of("MOO-214"))).isEmpty();
    }
}
