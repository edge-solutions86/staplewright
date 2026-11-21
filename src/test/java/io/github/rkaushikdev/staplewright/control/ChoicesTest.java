package io.github.rkaushikdev.staplewright.control;

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
                        each(Affiliation.class, Affiliation::fromValue, Affiliation::find),
                        each(Standing.class, Standing::fromValue, Standing::find),
                        each(KeyState.class, KeyState::fromValue, KeyState::find),
                        each(EntryKind.class, EntryKind::fromValue, EntryKind::find),
                        each(FindingKind.class, FindingKind::fromValue, FindingKind::find),
                        each(RequestState.class, RequestState::fromValue, RequestState::find),
                        each(ReviewState.class, ReviewState::fromValue, ReviewState::find),
                        each(Answer.class, Answer::fromValue, Answer::find),
                        each(BoxState.class, BoxState::fromValue, BoxState::find),
                        each(OpenedBy.class, OpenedBy::fromValue, OpenedBy::find),
                        each(TransferState.class, TransferState::fromValue, TransferState::find))
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
        InputError caught = catchThrowableOfType(InputError.class, () -> Standing.fromValue("on leave"));
        assertThat(caught.message()).isEqualTo("no such standing");
        assertThat(caught.facts()).containsEntry("value", "on leave");
    }

    @Test
    void aChoiceIsFoundByWhatItIsWrittenAsAndNotByItsName() {
        assertThat(KeyState.find("OUT")).isEmpty();
        assertThat(FindingKind.find("wrong_holder")).contains(FindingKind.WRONG_HOLDER);
        assertThat(Choices.find(Affiliation.class, null)).isEmpty();
    }
}
