package io.github.rkaushikdev.staplewright.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class TalliesTest {

    @Test
    void theBiggestCountComesFirstAndATieGoesByName() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("VOS", 1);
        counts.put("MOO", 2);
        counts.put("HEI", 1);
        counts.put("ARK", 0);
        assertThat(Tallies.biggestFirst(counts).keySet()).containsExactly("MOO", "HEI", "VOS", "ARK");
    }

    @Test
    void countingTalliesEachNameOnce() {
        Map<String, Integer> counts = Tallies.count(List.of("AA1", "AA", "AA1", "AB2"), Function.identity());
        assertThat(counts).containsExactly(Map.entry("AA1", 2), Map.entry("AA", 1), Map.entry("AB2", 1));
        assertThat(Tallies.count(List.<String>of(), Function.identity())).isEmpty();
    }

    @Test
    void theCountsComeBackReadOnly() {
        Map<String, Integer> counts = Tallies.count(List.of("AA1"), Function.identity());
        assertThatThrownBy(() -> counts.put("AA2", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
