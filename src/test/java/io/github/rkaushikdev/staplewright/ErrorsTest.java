package io.github.rkaushikdev.staplewright;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ErrorsTest {

    @Test
    void messageAloneRendersAsItself() {
        StaplewrightError err = new BittingError("cut four is deeper than the chart goes");
        assertThat(err.getMessage()).isEqualTo("cut four is deeper than the chart goes");
    }

    @Test
    void factsRenderInAStableOrder() {
        StaplewrightError err = new BittingError("out of range", Facts.of("chamber", 4, "depth", 9, "deepest", 7));
        assertThat(err.getMessage()).isEqualTo("out of range (chamber=4, deepest=7, depth=9)");
    }

    @Test
    void asDictCarriesTheCode() {
        StaplewrightError err = new PinningError("stack too tall", Facts.of("chamber", 2, "height", 345));
        Map<String, Object> facts = new LinkedHashMap<>();
        facts.put("chamber", 2);
        facts.put("height", 345);
        assertThat(err.asDict()).isEqualTo(Map.of("code", "pinning", "message", "stack too tall", "facts", facts));
    }

    @Test
    void factsAreCopiedNotShared() {
        Map<String, Object> facts = new HashMap<>(Map.of("chamber", 1));
        StaplewrightError err = new StaplewrightError("something", facts);
        facts.put("chamber", 99);
        assertThat(err.facts()).containsEntry("chamber", 1);
        assertThatThrownBy(() -> err.facts().put("chamber", 99)).isInstanceOf(UnsupportedOperationException.class);
    }

    static Stream<Arguments> eachClassKeepsItsCode() {
        return Stream.of(
                Arguments.of(InputError.class, "input"),
                Arguments.of(SpecificationError.class, "specification"),
                Arguments.of(BittingError.class, "bitting"),
                Arguments.of(KeywayError.class, "keyway"),
                Arguments.of(HierarchyError.class, "hierarchy"),
                Arguments.of(ProgressionError.class, "progression"),
                Arguments.of(PinningError.class, "pinning"),
                Arguments.of(CapacityError.class, "capacity"),
                Arguments.of(NotFoundError.class, "not_found"),
                Arguments.of(ConflictError.class, "conflict"));
    }

    @ParameterizedTest
    @MethodSource
    void eachClassKeepsItsCode(Class<? extends StaplewrightError> kind, String code) throws Exception {
        assertThat(StaplewrightError.codeOf(kind)).isEqualTo(code);
        assertThat(StaplewrightError.class).isAssignableFrom(kind);
        assertThat(kind.getConstructor(String.class).newInstance("no").code()).isEqualTo(code);
    }

    @Test
    void everyCodeIsUniqueAndReachable() {
        Map<String, Class<? extends StaplewrightError>> codes = StaplewrightError.errorCodes();
        assertThat(codes.get("bitting")).isEqualTo(BittingError.class);
        assertThat(codes.get("staplewright_error")).isEqualTo(StaplewrightError.class);
        Set<String> names = codes.values().stream().map(Class::getSimpleName).collect(Collectors.toSet());
        assertThat(codes).hasSize(names.size());
    }

    @Test
    void catchingTheBaseCatchesAllOfThem() throws Exception {
        for (Class<? extends StaplewrightError> kind :
                StaplewrightError.errorCodes().values()) {
            Constructor<? extends StaplewrightError> make = kind.getConstructor(String.class);
            assertThatThrownBy(() -> {
                        throw make.newInstance("no");
                    })
                    .isInstanceOf(StaplewrightError.class);
        }
    }
}
