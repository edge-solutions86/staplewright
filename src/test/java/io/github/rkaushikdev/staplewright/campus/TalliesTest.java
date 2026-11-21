package io.github.rkaushikdev.staplewright.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.util.Tallies;
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
        counts.put("SCI", 0);
        assertThat(Tallies.biggestFirst(counts).keySet()).containsExactly("MOO", "HEI", "VOS", "SCI");
    }

    @Test
    void countingTalliesEachNameOnce() {
        Map<String, Integer> counts =
                Tallies.count(List.of("MOO-214", "MOO-216", "MOO-214", "HEI-001"), Function.identity());
        assertThat(counts).containsExactly(Map.entry("MOO-214", 2), Map.entry("HEI-001", 1), Map.entry("MOO-216", 1));
        assertThat(Tallies.count(List.<String>of(), Function.identity())).isEmpty();
    }

    @Test
    void theCountsComeBackReadOnly() {
        Map<String, Integer> counts = Tallies.count(List.of("MOO-214"), Function.identity());
        assertThatThrownBy(() -> counts.put("MOO-216", 1)).isInstanceOf(UnsupportedOperationException.class);
    }
}
