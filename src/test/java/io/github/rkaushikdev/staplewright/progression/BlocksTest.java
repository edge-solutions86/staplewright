package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import org.junit.jupiter.api.Test;

class BlocksTest {

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static KeyBittingArray anArray() {
        ProgressionPlan plan = new ProgressionPlan(
                Charts.sixPinChart(chart -> chart.macs(5)), Bitting.parse("342156"), List.of(2, 4, 6), 2);
        return new KeyBittingArray(plan);
    }

    private static List<KeySymbol> masters(int count) {
        return "ABCDEFGH"
                .substring(0, count)
                .chars()
                .mapToObj(letter -> sym("A" + (char) letter))
                .toList();
    }

    @Test
    void blocksHandOutRunsInOrder() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byBlocks(array, masters(3), 10);
        assertThat(found.stream().map(item -> item.symbol().render()).toList()).containsExactly("AA", "AB", "AC");
        assertThat(found.get(0).keys()).isEqualTo(array.blocks(10).get(0));
    }

    @Test
    void aBlockAllocationNamesNoConstant() {
        List<Allocation> found = Blocks.byBlocks(anArray(), masters(2), 10);
        assertThat(found.get(0).constantChamber()).isEmpty();
        assertThat(found.get(0).describe()).endsWith("10 keys");
    }

    @Test
    void moreMastersThanBlocksIsRefused() {
        assertThatThrownBy(() -> Blocks.byBlocks(anArray(), masters(8), 30)).isInstanceOf(CapacityError.class);
    }

    @Test
    void aMasterCannotTakeTwoBlocks() {
        KeyBittingArray array = anArray();
        assertThatThrownBy(() -> Blocks.byBlocks(array, List.of(sym("AA"), sym("AA")), 10))
                .isInstanceOf(ProgressionError.class);
    }

    @Test
    void theConstantDepthsAreWhatTheArrayUses() {
        assertThat(Blocks.constantDepths(anArray(), 2)).containsExactly(0, 2, 6);
    }

    @Test
    void aChamberOutsideTheFamilyIsRefused() {
        assertThatThrownBy(() -> Blocks.constantDepths(anArray(), 9)).isInstanceOf(ProgressionError.class);
    }

    @Test
    void aRotatingConstantGivesEachMasterOneDepth() {
        List<Allocation> found = Blocks.byRotatingConstant(anArray(), masters(3), 2);
        assertThat(found.stream()
                        .map(item -> item.constantDepth().orElseThrow())
                        .toList())
                .containsExactly(0, 2, 6);
        assertThat(found).allSatisfy(item -> assertThat(item.constantChamber()).contains(2));
    }

    @Test
    void everyKeyInAnAllocationCarriesTheConstant() {
        List<Allocation> found = Blocks.byRotatingConstant(anArray(), masters(2), 2);
        for (Allocation allocation : found) {
            assertThat(allocation.keys()).allSatisfy(key -> assertThat(key.at(2))
                    .isEqualTo(allocation.constantDepth().orElseThrow()));
        }
    }

    @Test
    void aRotatingAllocationDescribesItsConstant() {
        List<Allocation> found = Blocks.byRotatingConstant(anArray(), masters(1), 2);
        assertThat(found.get(0).describe()).contains("chamber 2 held at 0");
    }

    @Test
    void moreMastersThanDepthsIsRefused() {
        CapacityError caught =
                catchThrowableOfType(CapacityError.class, () -> Blocks.byRotatingConstant(anArray(), masters(8), 2));
        assertThat(caught.facts()).containsEntry("depths", 3);
    }

    @Test
    void aMasterCannotTakeTwoDepths() {
        assertThatThrownBy(() -> Blocks.byRotatingConstant(anArray(), List.of(sym("AA"), sym("AA")), 2))
                .isInstanceOf(ProgressionError.class);
    }

    @Test
    void aRotatingConstantPartitionsTheWholeArray() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byRotatingConstant(array, masters(3), 2);
        assertThat(Blocks.isPartition(array, found)).isTrue();
        assertThat(Blocks.waste(array, found)).isZero();
    }

    @Test
    void blocksLeaveTheTailOfTheArrayUnallocated() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byBlocks(array, masters(2), 10);
        assertThat(Blocks.waste(array, found)).isEqualTo(array.usableCount() - 20);
        assertThat(Blocks.isPartition(array, found)).isFalse();
    }

    @Test
    void theUnallocatedKeysAreTheOnesNobodyGot() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byBlocks(array, masters(1), 10);
        List<Bitting> left = Blocks.unallocated(array, found);
        assertThat(left).hasSize(array.usableCount() - 10);
        assertThat(left).allSatisfy(key -> assertThat(found.get(0).keys()).doesNotContain(key));
    }

    @Test
    void aKeyReadsBackToItsMaster() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byRotatingConstant(array, masters(3), 2);
        Bitting key = found.get(1).keys().get(0);
        assertThat(Blocks.masterOf(found, key)).contains(found.get(1).symbol());
    }

    @Test
    void aKeyNobodyWasGivenHasNoMaster() {
        KeyBittingArray array = anArray();
        List<Allocation> found = Blocks.byBlocks(array, masters(1), 10);
        assertThat(Blocks.masterOf(found, Blocks.unallocated(array, found).get(0)))
                .isEmpty();
    }

    @Test
    void theTwoMethodsHandOutDifferentSets() {
        KeyBittingArray array = anArray();
        List<Allocation> blocks = Blocks.byBlocks(array, masters(3), 10);
        List<Allocation> rotating = Blocks.byRotatingConstant(array, masters(3), 2);
        assertThat(blocks.get(0).keys()).isNotEqualTo(rotating.get(0).keys());
    }
}
