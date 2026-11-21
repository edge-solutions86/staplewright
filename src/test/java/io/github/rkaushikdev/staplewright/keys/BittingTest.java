package io.github.rkaushikdev.staplewright.keys;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BittingTest {

    @Test
    void packedDigitsAreOneCutEach() {
        assertThat(Bitting.parse("342156").depths()).containsExactly(3, 4, 2, 1, 5, 6);
    }

    @Test
    void dottedFormReadsDoubleDigitDepths() {
        assertThat(Bitting.parse("10.4.12.3").depths()).containsExactly(10, 4, 12, 3);
    }

    @Test
    void dashesAndSpacesAreTheSameAsDots() {
        assertThat(Bitting.parse(" 10-4-12-3 ")).isEqualTo(Bitting.parse("10.4.12.3"));
    }

    @Test
    void chambersCountFromOne() {
        Bitting b = Bitting.parse("342156");
        assertThat(b.at(1)).isEqualTo(3);
        assertThat(b.at(6)).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 7})
    void aChamberOffTheEndIsRefused(int chamber) {
        assertThatThrownBy(() -> Bitting.parse("342156").at(chamber)).isInstanceOf(InputError.class);
    }

    @Test
    void withDepthLeavesTheOriginalAlone() {
        Bitting original = Bitting.parse("342156");
        Bitting changed = original.withDepth(3, 7);
        assertThat(changed.render()).isEqualTo("347156");
        assertThat(original.render()).isEqualTo("342156");
    }

    @Test
    void differsFromNamesTheChambers() {
        assertThat(Bitting.parse("342156").differsFrom(Bitting.parse("352158"))).containsExactly(2, 6);
    }

    @Test
    void differingLengthsCannotBeCompared() {
        assertThatThrownBy(() -> Bitting.parse("3421").differsFrom(Bitting.parse("342156")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void adjacentDifferencesAreAbsolute() {
        assertThat(Bitting.parse("342156").adjacentDifferences()).containsExactly(1, 2, 1, 4, 1);
    }

    @Test
    void renderGoesDottedOnItsOwnPastNine() {
        assertThat(Bitting.of(10, 2, 3).render()).isEqualTo("10.2.3");
        assertThat(Bitting.of(1, 2, 3).render()).isEqualTo("123");
        assertThat(Bitting.of(1, 2, 3).render(true)).isEqualTo("1.2.3");
    }

    @Test
    void bittingsCompareAndHashByValue() {
        assertThat(Bitting.parse("342156")).isEqualTo(Bitting.of(3, 4, 2, 1, 5, 6));
        assertThat(Set.of(Bitting.parse("1234")).contains(Bitting.of(1, 2, 3, 4)))
                .isTrue();
        assertThat(Bitting.parse("1234").hashCode())
                .isEqualTo(Bitting.of(1, 2, 3, 4).hashCode());
        assertThat(Bitting.parse("1234")).isLessThan(Bitting.parse("1235"));
    }

    @Test
    void deepestAndShallowest() {
        Bitting b = Bitting.parse("342156");
        assertThat(b.deepest()).isEqualTo(6);
        assertThat(b.shallowest()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "34a156", "3..4", "3.4.x"})
    void junkIsRefused(String text) {
        assertThatThrownBy(() -> Bitting.parse(text)).isInstanceOf(InputError.class);
    }

    @Test
    void aNegativeCutIsRefused() {
        assertThatThrownBy(() -> new Bitting(List.of(3, -1, 2))).isInstanceOf(InputError.class);
    }

    @Test
    void aMissingDepthIsNotADepth() {
        // The compiler only lets whole numbers in; a hole where a depth should be is refused alike.
        assertThatThrownBy(() -> new Bitting(Arrays.asList(null, 2, 3)))
                .isInstanceOf(InputError.class)
                .hasMessageStartingWith("a cut depth is a whole number");
    }

    @Test
    void anEmptyBittingIsRefused() {
        assertThatThrownBy(() -> new Bitting(List.of())).isInstanceOf(InputError.class);
    }

    @Test
    void iteratingGivesTheCutsInOrder() {
        List<Integer> walked = new ArrayList<>();
        Bitting.parse("3421").depths().forEach(walked::add);
        assertThat(walked).containsExactly(3, 4, 2, 1);
        assertThat(Bitting.parse("3421").size()).isEqualTo(4);
        assertThat(Bitting.parse("3421").get(0)).isEqualTo(3);
    }

    @Test
    void patternHolesComeBackEmpty() {
        assertThat(Bitting.parsePattern("3x21x6", 6))
                .containsExactly(
                        Optional.of(3),
                        Optional.empty(),
                        Optional.of(2),
                        Optional.of(1),
                        Optional.empty(),
                        Optional.of(6));
    }

    @Test
    void patternLengthHasToMatchTheSystem() {
        assertThatThrownBy(() -> Bitting.parsePattern("3x21", 6)).isInstanceOf(InputError.class);
    }

    @Test
    void aPatternTakesDepthsOrAnXAndNothingElse() {
        assertThatThrownBy(() -> Bitting.parsePattern("3q21x6", 6)).isInstanceOf(InputError.class);
    }

    @Test
    void matchingIgnoresTheOpenChambers() {
        List<Optional<Integer>> pattern = Bitting.parsePattern("3x21x6", 6);
        assertThat(Bitting.matchesPattern(Bitting.parse("342156"), pattern)).isTrue();
        assertThat(Bitting.matchesPattern(Bitting.parse("392186"), pattern)).isTrue();
        assertThat(Bitting.matchesPattern(Bitting.parse("442156"), pattern)).isFalse();
    }

    @Test
    void matchingAKeyOfTheWrongLengthIsFalseNotAnError() {
        assertThat(Bitting.matchesPattern(Bitting.parse("3421"), Bitting.parsePattern("3x21x6", 6)))
                .isFalse();
    }
}
