package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ChoicesTest {

    private static <E extends Enum<E> & Valued> Stream<Arguments> each(
            Class<E> kind, Function<String, E> fromValue, Function<String, Optional<E>> find) {
        return Arrays.stream(kind.getEnumConstants()).map(choice -> Arguments.of(choice, fromValue, find));
    }

    static Stream<Arguments> everyChoiceReadsBackFromItsOwnValue() {
        return Stream.of(
                        each(CoreFormat.class, CoreFormat::fromValue, CoreFormat::find),
                        each(HousingKind.class, HousingKind::fromValue, HousingKind::find))
                .flatMap(choices -> choices);
    }

    @ParameterizedTest
    @MethodSource
    void everyChoiceReadsBackFromItsOwnValue(
            Valued choice, Function<String, Object> fromValue, Function<String, Optional<Object>> find) {
        String written = String.valueOf(choice.value());
        assertThat(written).isEqualTo(choice.toString());
        assertThat(fromValue.apply(written)).isSameAs(choice);
        assertThat(find.apply(written)).containsSame(choice);
    }

    @Test
    void textThatNamesNoChoiceIsRefusedWithTheNoun() {
        InputError caught = catchThrowableOfType(InputError.class, () -> CoreFormat.fromValue("SFIC_A2"));
        assertThat(caught.message()).isEqualTo("no such core format");
        assertThat(caught.facts()).containsEntry("value", "SFIC_A2");
        assertThat(HousingKind.find("mortise")).isEmpty();
        assertThat(catchThrowableOfType(InputError.class, () -> HousingKind.fromValue("mortise"))
                        .message())
                .isEqualTo("no such housing kind");
    }
}
