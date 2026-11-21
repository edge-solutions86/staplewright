package io.github.rkaushikdev.staplewright.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Valued;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class ChoicesTest {

    private static <E extends Enum<E> & Valued> Stream<Arguments> each(
            Class<E> kind, Function<String, E> fromValue, Function<String, Optional<E>> find) {
        return Arrays.stream(kind.getEnumConstants()).map(choice -> Arguments.of(choice, fromValue, find));
    }

    static Stream<Arguments> everyChoiceReadsBackFromItsOwnValue() {
        return Stream.of(
                        each(DoorFunction.class, DoorFunction::fromValue, DoorFunction::find),
                        each(CylinderFormat.class, CylinderFormat::fromValue, CylinderFormat::find),
                        each(Position.class, Position::fromValue, Position::find),
                        each(Identification.class, Identification::fromValue, Identification::find),
                        each(CallState.class, CallState::fromValue, CallState::find),
                        each(Deficiency.class, Deficiency::fromValue, Deficiency::find))
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

    @ParameterizedTest
    @EnumSource(Condition.class)
    void aConditionReadsBackFromItsNumber(Condition condition) {
        assertThat(condition.toString()).isEqualTo(String.valueOf(condition.value()));
        assertThat(Condition.fromValue(condition.value())).isSameAs(condition);
        assertThat(Condition.find(condition.value())).containsSame(condition);
    }

    @Test
    void textThatNamesNoChoiceIsRefusedWithTheNoun() {
        InputError caught = catchThrowableOfType(InputError.class, () -> DoorFunction.fromValue("revolving"));
        assertThat(caught.message()).isEqualTo("no such door function");
        assertThat(caught.facts()).containsEntry("value", "revolving");
        InputError number = catchThrowableOfType(InputError.class, () -> Condition.fromValue(9));
        assertThat(number.message()).isEqualTo("no such condition");
        assertThat(number.facts()).containsEntry("value", 9);
    }

    @Test
    void aChoiceIsFoundByWhatItIsWrittenAsAndNotByItsName() {
        assertThat(Position.find("LOCKSET")).isEmpty();
        assertThat(Identification.find("campus card")).contains(Identification.CAMPUS_CARD);
        assertThat(DoorFunction.find("exit_only")).contains(DoorFunction.EXIT_ONLY);
        assertThat(Condition.find(0)).isEmpty();
        assertThat(Choices.find(CallState.class, null)).isEmpty();
    }
}
