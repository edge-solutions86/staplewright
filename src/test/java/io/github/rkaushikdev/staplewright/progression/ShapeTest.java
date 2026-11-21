package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ShapeTest {

    private static List<KeySymbol> symbols(String... names) {
        return Stream.of(names).map(KeySymbol::parse).toList();
    }

    /** These symbols, then a numbered change key under {@code master} for each number given. */
    private static List<KeySymbol> symbols(List<String> names, String master, int first, int last) {
        List<String> all = new ArrayList<>(names);
        IntStream.rangeClosed(first, last).forEach(number -> all.add(master + number));
        return symbols(all.toArray(String[]::new));
    }

    private static List<KeySymbol> aSmallSystem() {
        return symbols("GGM", "A", "B", "AA", "AB", "AA1", "AA2", "AB1", "BA", "BA1");
    }

    private static List<KeySymbol> aWideSystem() {
        return symbols(List.of("GGM", "A"), "A", 1, 30);
    }

    @Test
    void nothingToMeasureIsRefused() {
        assertThatThrownBy(() -> Shapes.shapeOf(List.of())).isInstanceOf(InputError.class);
    }

    @Test
    void theSymbolCountIsWhatWentIn() {
        assertThat(Shapes.shapeOf(aSmallSystem()).symbols()).isEqualTo(10);
    }

    @Test
    void theDepthIsTheDeepestLevel() {
        assertThat(Shapes.shapeOf(aSmallSystem()).depth()).isEqualTo(3);
    }

    @Test
    void theTopKeySitsOnLevelNothing() {
        Shape found = Shapes.shapeOf(aSmallSystem());
        assertThat(found.levels().get(0).depth()).isZero();
        assertThat(found.levels().get(0).keys()).isEqualTo(1);
    }

    @Test
    void aLevelSeparatesTheMastersFromTheChangeKeys() {
        Shape found = Shapes.shapeOf(aSmallSystem());
        Level third = found.levels().stream()
                .filter(level -> level.depth() == 3)
                .findFirst()
                .orElseThrow();
        assertThat(third.masters()).isZero();
        assertThat(third.changeKeys()).isEqualTo(4);
    }

    @Test
    void aLevelReadsBackAsOneLine() {
        Shape found = Shapes.shapeOf(aSmallSystem());
        assertThat(found.levels().get(0).describe()).isEqualTo("level 0: 1 keys, 1 of them masters");
    }

    @Test
    void theFanOutCountsWhatHangsOffEachMaster() {
        Shape found = Shapes.shapeOf(aSmallSystem());
        assertThat(found.fanOut()).containsEntry("GGM", 2);
        assertThat(found.fanOut()).containsEntry("AA", 2);
    }

    @Test
    void theTopKeyHangsOffNothing() {
        assertThat(Shapes.shapeOf(aSmallSystem()).fanOut()).doesNotContainKey("");
    }

    @Test
    void theWidestMasterIsNamedWithItsCount() {
        assertThat(Shapes.shapeOf(aWideSystem()).widest()).isEqualTo(new Fan("A", 30));
    }

    @Test
    void aSystemWithNothingUnderAnythingHasNoWidest() {
        Shape found = Shapes.shapeOf(symbols("GGM"));
        assertThat(found.widest()).isEqualTo(new Fan("", 0));
    }

    @Test
    void theAverageFanOutIsTheMean() {
        Shape found = Shapes.shapeOf(symbols("GGM", "A", "B"));
        assertThat(found.averageFanOut()).isEqualTo(2.0);
    }

    @Test
    void theMedianFanOutIgnoresTheOutlier() {
        Shape found = Shapes.shapeOf(symbols("GGM", "A", "B", "C", "A1", "A2", "A3", "B1", "C1"));
        assertThat(found.medianFanOut()).isEqualTo(2.0);
    }

    @Test
    void anEmptySystemAveragesNothing() {
        Shape found = Shapes.shapeOf(symbols("GGM"));
        assertThat(found.averageFanOut()).isEqualTo(0.0);
        assertThat(found.medianFanOut()).isEqualTo(0.0);
    }

    @Test
    void aSmallTreeIsNeverCalledLopsided() {
        assertThat(Shapes.shapeOf(symbols("GGM", "A", "A1")).isLopsided()).isFalse();
    }

    @Test
    void oneBranchCarryingEverythingIsLopsided() {
        Shape found = Shapes.shapeOf(symbols(List.of("GGM", "A", "B", "C", "B1", "C1"), "A", 1, 19));
        assertThat(found.isLopsided()).isTrue();
    }

    @Test
    void anEvenTreeIsNotLopsided() {
        Shape found = Shapes.shapeOf(symbols("GGM", "A", "B", "C", "A1", "A2", "B1", "B2", "C1", "C2"));
        assertThat(found.isLopsided()).isFalse();
    }

    @Test
    void aShapeReadsBackAsOneLine() {
        assertThat(Shapes.shapeOf(aSmallSystem()).describe()).isEqualTo("10 symbols, 3 deep, widest master 2");
    }

    @Test
    void aWideMasterIsNamed() {
        List<Fan> found = Shapes.wideMasters(Shapes.shapeOf(aWideSystem()), 25);
        assertThat(found).containsExactly(new Fan("A", 30));
    }

    @Test
    void nothingWideComesBackEmpty() {
        assertThat(Shapes.wideMasters(Shapes.shapeOf(aSmallSystem()))).isEmpty();
    }

    @Test
    void aLowerLimitCatchesMore() {
        List<Fan> found = Shapes.wideMasters(Shapes.shapeOf(aSmallSystem()), 1);
        assertThat(found.stream().map(Fan::master).toList()).containsExactly("A", "AA", "GGM");
    }

    @Test
    void aLimitOfNothingIsRefused() {
        assertThatThrownBy(() -> Shapes.wideMasters(Shapes.shapeOf(aSmallSystem()), 0))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aMasterWithOneKeyUnderItIsNamed() {
        List<String> found = Shapes.onlyChildren(Shapes.shapeOf(aSmallSystem()));
        assertThat(found).containsExactly("AB", "B", "BA");
    }

    @Test
    void aMasterWithTwoIsNot() {
        assertThat(Shapes.onlyChildren(Shapes.shapeOf(aSmallSystem()))).doesNotContain("AA");
    }

    @Test
    void aMasterWithNothingUnderItIsNamed() {
        assertThat(Shapes.barrenMasters(symbols("GGM", "A", "B", "A1"))).containsExactly("B");
    }

    @Test
    void aMasterWithSomethingUnderItIsNot() {
        assertThat(Shapes.barrenMasters(symbols("GGM", "A", "A1"))).isEmpty();
    }

    @Test
    void theTopKeyIsNeverBarren() {
        assertThat(Shapes.barrenMasters(symbols("GGM"))).doesNotContain("GGM");
    }

    @Test
    void aKeyWhoseMasterIsMissingIsAnOrphan() {
        assertThat(Shapes.orphans(symbols("GGM", "AA1"))).containsExactly("AA");
    }

    @Test
    void aFullTreeHasNoOrphans() {
        assertThat(Shapes.orphans(aSmallSystem())).isEmpty();
    }

    @Test
    void theLevelTableCountsTheRanks() {
        List<String> lines = Shapes.levelTable(Shapes.shapeOf(aSmallSystem()));
        assertThat(lines.get(0)).isEqualTo("10 symbols across 4 levels");
        assertThat(lines).hasSize(5);
    }

    @Test
    void theReportLeadsWithTheShape() {
        List<String> lines = Shapes.shapeReport(aSmallSystem());
        assertThat(lines.get(0)).isEqualTo("10 symbols, 3 deep, widest master 2");
    }

    @Test
    void theReportSaysWhenNothingIsWide() {
        List<String> lines = Shapes.shapeReport(aSmallSystem());
        assertThat(lines).anyMatch(line -> line.equals("no master carries more than 25 keys"));
    }

    @Test
    void theReportNamesAWideMaster() {
        List<String> lines = Shapes.shapeReport(aWideSystem());
        assertThat(lines).anyMatch(line -> line.startsWith("1 masters carry more than 25"));
    }

    @Test
    void theReportNamesTheThinMasters() {
        List<String> lines = Shapes.shapeReport(aSmallSystem());
        assertThat(lines).anyMatch(line -> line.startsWith("3 masters have one key under them"));
    }

    @Test
    void theReportWarnsAboutADeepTree() {
        List<KeySymbol> deep = symbols("GGM", "A", "AA", "AAA", "AAAA", "AAAA1");
        List<String> lines = Shapes.shapeReport(deep);
        assertThat(lines).anyMatch(line -> line.contains("wafer in every chamber"));
    }

    @Test
    void theReportNamesTheOrphans() {
        List<String> lines = Shapes.shapeReport(symbols("GGM", "AA1"));
        assertThat(lines).anyMatch(line -> line.startsWith("1 masters are referred to"));
    }

    @Test
    void twoSystemsCanBePutSideBySide() {
        List<String> lines = Shapes.compare(Shapes.shapeOf(aSmallSystem()), Shapes.shapeOf(aWideSystem()));
        assertThat(lines.get(0)).startsWith("symbols");
        List<String> depth = Text.words(lines.get(1));
        assertThat(depth.subList(1, depth.size())).containsExactly("3", "2");
    }
}
