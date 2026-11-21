package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.KeywayError;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class KeywayTest {

    /** The campus family. L enters everything, the leaves are the buildings. */
    static KeywayFamily aFamily() {
        KeywayFamily family = new KeywayFamily("Sargent LA", new KeywaySection("L", "Sargent"));
        family.add(new KeywaySection("LA", "Sargent"), "L");
        family.add(new KeywaySection("LB", "Sargent"), "L");
        family.add(new KeywaySection("LC", "Sargent", true), "LA");
        family.add(new KeywaySection("LD", "Sargent", true), "LA");
        family.add(new KeywaySection("LE", "Sargent"), "LB");
        return family;
    }

    @Test
    void theRootEntersEverySection() {
        assertThat(aFamily().sectionsEntered("L")).containsExactlyInAnyOrder("L", "LA", "LB", "LC", "LD", "LE");
    }

    @Test
    void aBranchEntersOnlyWhatHangsOffIt() {
        assertThat(aFamily().sectionsEntered("LA")).containsExactlyInAnyOrder("LA", "LC", "LD");
    }

    @Test
    void aLeafEntersOnlyItself() {
        assertThat(aFamily().sectionsEntered("LC")).containsExactly("LC");
    }

    @Test
    void enteringReadsDownTheTreeNotUp() {
        KeywayFamily family = aFamily();
        assertThat(family.enters("LA", "LC")).isTrue();
        assertThat(family.enters("LC", "LA")).isFalse();
        assertThat(family.enters("LC", "LC")).isTrue();
    }

    @Test
    void twoBranchesDoNotEnterEachOther() {
        KeywayFamily family = aFamily();
        assertThat(family.enters("LA", "LE")).isFalse();
        assertThat(family.enters("LB", "LC")).isFalse();
    }

    @Test
    void ancestryWalksUpToTheRoot() {
        assertThat(aFamily().ancestry("LC")).containsExactly("LC", "LA", "L");
    }

    @Test
    void theCommonSectionIsTheLowestOneThatCoversBoth() {
        KeywayFamily family = aFamily();
        assertThat(family.commonSection("LC", "LD")).isEqualTo("LA");
        assertThat(family.commonSection("LC", "LE")).isEqualTo("L");
        assertThat(family.commonSection("LC", "LC")).isEqualTo("LC");
        assertThat(family.commonSection("LC", "LA")).isEqualTo("LA");
    }

    @Test
    void depthCountsFromTheRoot() {
        KeywayFamily family = aFamily();
        assertThat(family.depthOf("L")).isZero();
        assertThat(family.depthOf("LA")).isEqualTo(1);
        assertThat(family.depthOf("LC")).isEqualTo(2);
    }

    @Test
    void theLeavesAreTheSectionsWithNothingUnderThem() {
        assertThat(aFamily().leaves()).containsExactly("LC", "LD", "LE");
    }

    @Test
    void parentAndChildrenReadBothWays() {
        KeywayFamily family = aFamily();
        assertThat(family.parentOf("LC").orElseThrow().code()).isEqualTo("LA");
        assertThat(family.parentOf("L")).isEmpty();
        assertThat(family.childrenOf("LA").stream().map(KeywaySection::code)).containsExactly("LC", "LD");
        assertThat(family.childrenOf("LC")).isEmpty();
    }

    @Test
    void aFamilyIsAContainerOfItsSections() {
        KeywayFamily family = aFamily();
        assertThat(family.contains("LC")).isTrue();
        assertThat(family.contains("LZ")).isFalse();
        assertThat(family.size()).isEqualTo(6);
        List<String> walked = new ArrayList<>();
        family.forEach(section -> walked.add(section.code()));
        assertThat(walked).containsExactly("L", "LA", "LB", "LC", "LD", "LE");
    }

    @Test
    void aSectionCannotBeAddedTwice() {
        assertThatThrownBy(() -> aFamily().add(new KeywaySection("LC", "Sargent"), "LB"))
                .isInstanceOf(KeywayError.class);
    }

    @Test
    void aSectionCannotHangOffAParentThatIsNotThere() {
        assertThatThrownBy(() -> aFamily().add(new KeywaySection("LZ", "Sargent"), "LQ"))
                .isInstanceOf(KeywayError.class);
    }

    @Test
    void aFamilyHoldsOneManufacturer() {
        KeywayError caught =
                catchThrowableOfType(KeywayError.class, () -> aFamily().add(new KeywaySection("LZ", "Best"), "L"));
        assertThat(caught.facts()).containsEntry("expected", "Sargent");
    }

    @Test
    void askingAboutASectionThatIsNotThere() {
        assertThatThrownBy(() -> aFamily().section("LZ")).isInstanceOf(KeywayError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "lc", " LC"})
    void aSectionCodeIsUpperCaseAndTrimmed(String code) {
        assertThatThrownBy(() -> new KeywaySection(code, "Sargent")).isInstanceOf(KeywayError.class);
    }

    @Test
    void aFamilyNeedsAName() {
        assertThatThrownBy(() -> new KeywayFamily("  ", new KeywaySection("L", "Sargent")))
                .isInstanceOf(KeywayError.class);
    }

    @Test
    void restrictedSectionsAreReportedInOrder() {
        assertThat(Keyways.restrictedSections(aFamily())).containsExactly("LC", "LD");
    }

    @Test
    void blanksComeBackDeepestStockFirst() {
        List<Blank> drawer = List.of(
                new Blank("LC-6", "LC", "Sargent", 6, 4),
                new Blank("LC-7", "LC", "Sargent", 7, 19),
                new Blank("LA-6", "LA", "Sargent", 6, 40));
        assertThat(Keyways.blanksFor(drawer, "LC").stream().map(Blank::partNumber))
                .containsExactly("LC-7", "LC-6");
        assertThat(Keyways.blanksFor(drawer, "LZ")).isEmpty();
    }

    @Test
    void twoBlanksAtTheSameStockSortByPartNumber() {
        List<Blank> drawer =
                List.of(new Blank("LC-7", "LC", "Sargent", 7, 4), new Blank("LC-6", "LC", "Sargent", 6, 4));
        assertThat(Keyways.blanksFor(drawer, "LC").stream().map(Blank::partNumber))
                .containsExactly("LC-6", "LC-7");
    }

    static Stream<Supplier<Blank>> anImpossibleBlankIsRefused() {
        return Stream.of(() -> new Blank("LC-6", "LC", "Sargent", 0), () -> new Blank("LC-6", "LC", "Sargent", 6, -1));
    }

    @ParameterizedTest
    @MethodSource
    void anImpossibleBlankIsRefused(Supplier<Blank> broken) {
        assertThatThrownBy(broken::get).isInstanceOf(KeywayError.class);
    }
}
