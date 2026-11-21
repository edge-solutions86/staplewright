package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RelationsTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    @Test
    void twoKeysCutTheSameSplitNowhere() {
        MasteringReport report = Relations.compare(b("342156"), b("342156"));
        assertThat(report.isIdentical()).isTrue();
        assertThat(report.splitCount()).isZero();
        assertThat(report.maintainedChambers()).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void aSplitNamesBothDepthsAndTheStep() {
        MasteringReport report = Relations.compare(b("342156"), b("362156"));
        assertThat(report.splitChambers()).containsExactly(2);
        ChamberSplit split = report.splits().get(0);
        assertThat(List.of(split.above(), split.below(), split.step())).containsExactly(4, 6, 2);
        assertThat(split.belowIsDeeper()).isTrue();
    }

    @Test
    void aChangeKeyCutShallowerReadsTheOtherWay() {
        ChamberSplit split =
                Relations.compare(b("362156"), b("342156")).splits().get(0);
        assertThat(split.belowIsDeeper()).isFalse();
        assertThat(split.step()).isEqualTo(2);
    }

    @Test
    void theChambersThatAreNotSplitAreTheOnesLeftOver() {
        MasteringReport report = Relations.compare(b("342156"), b("362356"));
        assertThat(report.splitChambers()).containsExactly(2, 4);
        assertThat(report.maintainedChambers()).containsExactly(1, 3, 5, 6);
    }

    @Test
    void theWaferCountIsTheSplitCount() {
        assertThat(Relations.waferCount(b("342156"), b("362356"))).isEqualTo(2);
        assertThat(Relations.waferCount(b("342156"), b("342156"))).isZero();
    }

    @Test
    void askingAboutAChamberThatDidNotSplit() {
        MasteringReport report = Relations.compare(b("342156"), b("362156"));
        assertThat(report.stepIn(2)).isEqualTo(2);
        assertThat(report.stepIn(1)).isZero();
    }

    @Test
    void keysOfDifferentLengthsAreNotOneSystem() {
        assertThatThrownBy(() -> Relations.compare(b("3421"), b("342156"))).isInstanceOf(InputError.class);
    }

    @Test
    void aCylinderBreaksAtEveryDepthAnyOfItsKeysCuts() {
        Map<Integer, List<Integer>> breaks = Relations.wafersFor(b("342156"), List.of(b("362156"), b("342356")));
        assertThat(breaks.get(1)).containsExactly(3);
        assertThat(breaks.get(2)).containsExactly(4, 6);
        assertThat(breaks.get(4)).containsExactly(1, 3);
        assertThat(breaks.get(6)).containsExactly(6);
    }

    @Test
    void aMasterOnItsOwnBreaksOnceAChamber() {
        Map<Integer, List<Integer>> breaks = Relations.wafersFor(b("342156"), List.of());
        assertThat(breaks.values()).allSatisfy(depths -> assertThat(depths).hasSize(1));
    }

    @Test
    void breakDepthsComeBackShallowToDeep() {
        Map<Integer, List<Integer>> breaks = Relations.wafersFor(b("542156"), List.of(b("142156"), b("342156")));
        assertThat(breaks.get(1)).containsExactly(1, 3, 5);
    }

    @Test
    void aChangeKeyOfTheWrongLengthIsRefused() {
        assertThatThrownBy(() -> Relations.wafersFor(b("342156"), List.of(b("3421"))))
                .isInstanceOf(InputError.class);
    }

    @Test
    void twoStepProgressionPassesWhenEverySplitIsEven() {
        assertThat(Relations.stepsAreMultiplesOf(b("342156"), b("362356"), 2)).isTrue();
        assertThat(Relations.stepsAreMultiplesOf(b("342156"), b("352156"), 2)).isFalse();
    }

    @Test
    void everyKeyIsAMultipleOfOneStep() {
        assertThat(Relations.stepsAreMultiplesOf(b("342156"), b("352156"), 1)).isTrue();
    }

    @Test
    void aStepOfZeroIsRefused() {
        assertThatThrownBy(() -> Relations.stepsAreMultiplesOf(b("342156"), b("352156"), 0))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theTightestSplitIsTheThinWafer() {
        assertThat(Relations.shallowestSplit(b("342156"), b("372356"))).contains(2);
        assertThat(Relations.shallowestSplit(b("342156"), b("342156"))).isEmpty();
    }

    @Test
    void aSetOfKeysReportsItsOwnDuplicates() {
        List<Bitting> keys = List.of(b("342156"), b("362156"), b("342156"));
        assertThat(Relations.keysAreDistinct(keys)).isFalse();
        assertThat(Relations.duplicateBittings(keys)).containsExactly(b("342156"));
    }

    @Test
    void aCleanSetHasNoDuplicates() {
        List<Bitting> keys = List.of(b("342156"), b("362156"));
        assertThat(Relations.keysAreDistinct(keys)).isTrue();
        assertThat(Relations.duplicateBittings(keys)).isEmpty();
    }
}
