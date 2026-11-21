package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.HierarchyError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SymbolTest {

    private static KeySymbol s(String text) {
        return KeySymbol.parse(text);
    }

    @Test
    void theTopKeyHasItsOwnSpelling() {
        KeySymbol top = s("GGM");
        assertThat(top.isTop()).isTrue();
        assertThat(top.level()).isZero();
        assertThat(top.render()).isEqualTo("GGM");
        assertThat(top.levelName()).isEqualTo("great grand master");
    }

    @Test
    void lettersAloneAreAMasterAtThatDepth() {
        assertThat(s("A").level()).isEqualTo(1);
        assertThat(s("AB").level()).isEqualTo(2);
        assertThat(s("ABC").level()).isEqualTo(3);
        assertThat(s("A").levelName()).isEqualTo("grand master");
        assertThat(s("AB").levelName()).isEqualTo("master");
        assertThat(s("ABC").levelName()).isEqualTo("sub master");
    }

    @Test
    void aNumberMakesItAChangeKeyOneLevelDown() {
        KeySymbol symbol = s("AB3");
        assertThat(symbol.isChangeKey()).isTrue();
        assertThat(symbol.level()).isEqualTo(3);
        assertThat(symbol.levelName()).isEqualTo("change key");
        assertThat(symbol.number()).contains(3);
    }

    @Test
    void parsingTidiesCaseAndWhitespace() {
        assertThat(s("  ab3 ")).isEqualTo(new KeySymbol("AB", 3));
    }

    @Test
    void aSymbolRendersBackToWhatWasRead() {
        for (String text : List.of("GGM", "A", "AB", "ABC", "AB3", "ABCD17")) {
            assertThat(s(text).render()).isEqualTo(text);
        }
    }

    @Test
    void aChangeKeyHangsUnderItsMaster() {
        assertThat(s("AB3").parent()).contains(s("AB"));
    }

    @Test
    void aMasterHangsUnderTheOneWithALetterLess() {
        assertThat(s("ABC").parent()).contains(s("AB"));
        assertThat(s("A").parent()).contains(s("GGM"));
    }

    @Test
    void theTopKeyHasNothingAboveIt() {
        assertThat(s("GGM").parent()).isEmpty();
    }

    @Test
    void ancestryClimbsAllTheWay() {
        assertThat(s("AB3").ancestry().stream().map(KeySymbol::render)).containsExactly("AB3", "AB", "A", "GGM");
    }

    @Test
    void descentReadsDownwardOnly() {
        KeySymbol change = s("AB3");
        assertThat(change.descendsFrom(s("AB"))).isTrue();
        assertThat(change.descendsFrom(s("A"))).isTrue();
        assertThat(change.descendsFrom(s("GGM"))).isTrue();
        assertThat(change.descendsFrom(change)).isTrue();
        assertThat(s("AB").descendsFrom(change)).isFalse();
    }

    @Test
    void keysOnDifferentBranchesDoNotDescendFromEachOther() {
        assertThat(s("AB3").descendsFrom(s("AC"))).isFalse();
        assertThat(s("AB3").descendsFrom(s("B"))).isFalse();
    }

    @Test
    void buildingAMasterDownward() {
        assertThat(s("A").childMaster("b")).isEqualTo(s("AB"));
        assertThat(s("GGM").childMaster("A")).isEqualTo(s("A"));
    }

    @Test
    void buildingAChangeKeyDownward() {
        assertThat(s("AB").changeKey(7)).isEqualTo(s("AB7"));
    }

    @Test
    void nothingHangsOffAChangeKey() {
        KeySymbol change = s("AB3");
        assertThatThrownBy(() -> change.childMaster("C")).isInstanceOf(HierarchyError.class);
        assertThatThrownBy(() -> change.changeKey(1)).isInstanceOf(HierarchyError.class);
    }

    @Test
    void changeKeysDoNotHangOffTheTop() {
        assertThatThrownBy(() -> s("GGM").changeKey(1)).isInstanceOf(HierarchyError.class);
    }

    @Test
    void aMasterIsNamedByOneLetter() {
        assertThatThrownBy(() -> s("A").childMaster("BC")).isInstanceOf(HierarchyError.class);
        assertThatThrownBy(() -> s("A").childMaster("4")).isInstanceOf(HierarchyError.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "1A", "A-1", "ABCDE", "A0", "a b"})
    void junkIsNotASymbol(String text) {
        assertThatThrownBy(() -> KeySymbol.parse(text)).isInstanceOf(HierarchyError.class);
    }

    @Test
    void aNumberWithoutLettersIsRefused() {
        assertThatThrownBy(() -> new KeySymbol("", 3)).isInstanceOf(HierarchyError.class);
    }

    @Test
    void theCommonKeyAboveTwoSymbols() {
        assertThat(KeySymbol.commonAncestor(s("AB3"), s("AB7")).render()).isEqualTo("AB");
        assertThat(KeySymbol.commonAncestor(s("AB3"), s("AC1")).render()).isEqualTo("A");
        assertThat(KeySymbol.commonAncestor(s("AB3"), s("BC1")).render()).isEqualTo("GGM");
        assertThat(KeySymbol.commonAncestor(s("AB3"), s("AB")).render()).isEqualTo("AB");
    }

    @Test
    void scheduleOrderIsShallowFirstThenAlphabetical() {
        List<KeySymbol> order = KeySymbol.sortSymbols(List.of(s("AB2"), s("A"), s("AB10"), s("GGM"), s("AB"), s("AA")));
        assertThat(order.stream().map(KeySymbol::render)).containsExactly("GGM", "A", "AA", "AB", "AB2", "AB10");
    }
}
