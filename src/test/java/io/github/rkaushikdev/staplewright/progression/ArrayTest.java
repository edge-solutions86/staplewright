package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ProgressionError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.spec.CuttingRules;
import io.github.rkaushikdev.staplewright.spec.DepthAndSpaceChart;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ArrayTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static KeyBittingArray anArray() {
        return anArray(Charts.sixPinChart(chart -> chart.macs(5)));
    }

    private static KeyBittingArray anArray(DepthAndSpaceChart chart) {
        ProgressionPlan plan = new ProgressionPlan(chart, b("342156"), List.of(2, 4, 6), 2);
        return new KeyBittingArray(plan, new CuttingRules());
    }

    private static List<KeySymbol> symbols(String... texts) {
        return Stream.of(texts).map(KeySymbol::parse).toList();
    }

    @Test
    void theArrayIsThePlanMinusWhatTheRulesRefuse() {
        KeyBittingArray array = anArray();
        assertThat(array.capacity()).isEqualTo(64);
        assertThat(array.usableCount() + array.loss()).isEqualTo(64);
        assertThat(array.usableCount()).isLessThan(64);
    }

    @Test
    void everyKeyInTheArrayPassesTheRules() {
        KeyBittingArray array = anArray();
        for (Bitting key : array) {
            assertThat(array.plan().holds(key)).isTrue();
        }
        assertThat(new HashSet<>(array.usable())).hasSize(array.usableCount());
    }

    @Test
    void nothingTheRulesRefusedIsInTheUsableList() {
        KeyBittingArray array = anArray();
        for (KeyBittingArray.Rejection rejection : array.rejected()) {
            assertThat(array.holds(rejection.bitting())).isFalse();
        }
    }

    @Test
    void theReasonsAreCountedWorstFirst() {
        Map<String, Integer> reasons = anArray().rejectionReasons();
        assertThat(reasons).containsKey("macs");
        assertThat(new ArrayList<>(reasons.values()))
                .isEqualTo(reasons.values().stream()
                        .sorted(Comparator.reverseOrder())
                        .toList());
    }

    @Test
    void aLooserMacsThrowsAwayFewerKeys() {
        KeyBittingArray tight = anArray(Charts.sixPinChart(chart -> chart.macs(3)));
        KeyBittingArray loose = anArray(Charts.sixPinChart(chart -> chart.macs(9)));
        assertThat(tight.usableCount()).isLessThan(loose.usableCount());
        assertThat(loose.loss()).isZero();
    }

    @Test
    void blocksComeOffInPrintedOrder() {
        KeyBittingArray array = anArray();
        List<List<Bitting>> blocks = array.blocks(10);
        assertThat(blocks.get(0)).isEqualTo(array.usable().subList(0, 10));
        assertThat(blocks.get(1)).isEqualTo(array.usable().subList(10, 20));
    }

    @Test
    void theLastBlockIsShortRatherThanDropped() {
        KeyBittingArray array = anArray();
        List<List<Bitting>> blocks = array.blocks(10);
        assertThat(blocks.stream().mapToInt(List::size).sum()).isEqualTo(array.usableCount());
        assertThat(blocks.get(blocks.size() - 1).size()).isLessThanOrEqualTo(10);
    }

    @Test
    void aBlockOfOneIsOneBlockAKey() {
        KeyBittingArray array = anArray();
        assertThat(array.blockCount(1)).isEqualTo(array.usableCount());
    }

    @Test
    void aBlockBiggerThanTheArrayIsOneBlock() {
        assertThat(anArray().blockCount(500)).isEqualTo(1);
    }

    @Test
    void aKeyReportsTheBlockItSitsIn() {
        KeyBittingArray array = anArray();
        Bitting first = array.usable().get(0);
        Bitting eleventh = array.usable().get(10);
        assertThat(array.blockHolding(first, 10)).contains(1);
        assertThat(array.blockHolding(eleventh, 10)).contains(2);
    }

    @Test
    void aKeyThatIsNotInTheArraySitsInNoBlock() {
        assertThat(anArray().blockHolding(b("111111"), 10)).isEmpty();
    }

    @Test
    void aBlockSizeOfZeroIsRefused() {
        assertThatThrownBy(() -> anArray().blocks(0)).isInstanceOf(ProgressionError.class);
    }

    @Test
    void takingKeysWalksTheArrayInOrder() {
        KeyBittingArray array = anArray();
        assertThat(array.take(3)).isEqualTo(array.usable().subList(0, 3));
        assertThat(array.take(3, 3)).isEqualTo(array.usable().subList(3, 6));
    }

    @Test
    void takingMoreThanIsLeftIsRefused() {
        KeyBittingArray array = anArray();
        CapacityError caught = catchThrowableOfType(CapacityError.class, () -> array.take(5, array.usableCount() - 2));
        assertThat(caught.facts()).containsEntry("left", 2);
    }

    @Test
    void takingNothingIsAllowed() {
        assertThat(anArray().take(0)).isEmpty();
    }

    @Test
    void takingANegativeRunIsRefused() {
        assertThatThrownBy(() -> anArray().take(-1)).isInstanceOf(ProgressionError.class);
    }

    @Test
    void eachMasterGetsItsOwnBlock() {
        KeyBittingArray array = anArray();
        List<KeySymbol> masters = symbols("AA", "AB", "AC");
        Map<KeySymbol, List<Bitting>> handed = KeyBittingArray.assignBlocks(array, masters, 10);
        assertThat(new ArrayList<>(handed.keySet())).isEqualTo(masters);
        assertThat(handed.get(masters.get(0))).isEqualTo(array.blocks(10).get(0));
        assertThat(handed.get(masters.get(1))).isEqualTo(array.blocks(10).get(1));
    }

    @Test
    void noKeyIsHandedToTwoMasters() {
        KeyBittingArray array = anArray();
        Map<KeySymbol, List<Bitting>> handed = KeyBittingArray.assignBlocks(array, symbols("AA", "AB", "AC"), 10);
        List<Bitting> seen = handed.values().stream().flatMap(List::stream).toList();
        assertThat(new HashSet<>(seen)).hasSize(seen.size());
    }

    @Test
    void moreMastersThanBlocksIsRefused() {
        KeyBittingArray array = anArray();
        List<KeySymbol> masters = "ABCDEFGHIJ"
                .chars()
                .mapToObj(letter -> KeySymbol.parse("A" + (char) letter))
                .toList();
        CapacityError caught =
                catchThrowableOfType(CapacityError.class, () -> KeyBittingArray.assignBlocks(array, masters, 40));
        assertThat(caught.facts()).containsEntry("wanted", 10);
    }

    @Test
    void theSameMasterCannotTakeTwoBlocks() {
        KeyBittingArray array = anArray();
        KeySymbol symbol = KeySymbol.parse("AA");
        assertThatThrownBy(() -> KeyBittingArray.assignBlocks(array, List.of(symbol, symbol), 10))
                .isInstanceOf(ProgressionError.class);
    }

    @Test
    void theArrayDescribesItsOwnLosses() {
        KeyBittingArray array = anArray();
        String described = array.describe();
        assertThat(described).contains(String.valueOf(array.usableCount()));
        assertThat(described).contains(String.valueOf(array.loss()));
    }
}
