package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class LayoutTest {

    @Test
    void columnsPadEveryValueButTheLast() {
        assertThat(Layout.columns(List.of("tag", "cut", "due"), 6, 5)).isEqualTo("tag   cut  due");
    }

    @Test
    void aValuePastItsColumnPushesTheLineAlong() {
        assertThat(Layout.columns(List.of("MOO-214-A", "x"), 4)).isEqualTo("MOO-214-Ax");
    }

    @Test
    void aMissingValuePrintsAsNothingAndTheLineEndsClean() {
        assertThat(Layout.columns(Arrays.asList("C-1099", null, null), 8, 8)).isEqualTo("C-1099");
    }

    @Test
    void numbersReadRightAligned() {
        assertThat(Layout.right(List.of(180, 30, 210), 5)).isEqualTo("  180   30  210");
    }

    @Test
    void aTitleGoesOnTopOnlyWhenThereIsOne() {
        assertThat(Layout.titled("moore hall", List.of("a"))).containsExactly("moore hall", "a");
        assertThat(Layout.titled("", List.of("a"))).containsExactly("a");
    }

    @Test
    void aPageHasNoNewlineAfterItsLastLine() {
        assertThat(Layout.asText(List.of("one", "", "two"))).isEqualTo("one\n\ntwo");
        assertThat(Layout.rule(3)).isEqualTo("---");
        assertThat(Layout.rule()).hasSize(Layout.PAGE_WIDTH);
    }

    @Test
    void theEndOfALineLosesEveryKindOfWhiteSpace() {
        assertThat(Layout.stripEnd("  AA1 \t 　")).isEqualTo("  AA1");
        assertThat(Layout.stripEnd("   ")).isEmpty();
    }
}
