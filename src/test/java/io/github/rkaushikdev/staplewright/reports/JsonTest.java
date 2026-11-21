package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JsonTest {

    private static Map<String, Optional<Object>> record(Object... pairs) {
        Map<String, Optional<Object>> found = new LinkedHashMap<>();
        for (int index = 0; index < pairs.length; index += 2) {
            found.put((String) pairs[index], Optional.ofNullable(pairs[index + 1]));
        }
        return found;
    }

    @Test
    void noRecordsIsAnEmptyList() {
        assertThat(Json.records(List.of())).isEqualTo("[]");
    }

    @Test
    void everyKeyIsOnItsOwnLineInColumnOrder() {
        String found = Json.records(List.of(record("symbol", "AA1", "level", 3, "bitting", null), record()));
        assertThat(found)
                .isEqualTo("[\n  {\n    \"symbol\": \"AA1\",\n    \"level\": 3,\n    \"bitting\": null\n  },\n  {}\n]");
    }

    @Test
    void anythingOutsidePrintableAsciiIsEscaped() {
        String found = Json.records(List.of(record("a", "q\"\\\n\t\u0001\u007fé🔑")));
        assertThat(found).contains("\"q\\\"\\\\\\n\\t\\u0001\\u007f\\u00e9\\ud83d\\udd11\"");
    }

    @Test
    void aValueJsonCannotCarryIsRefused() {
        assertThatThrownBy(() -> Json.records(List.of(record("a", new Object()))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
