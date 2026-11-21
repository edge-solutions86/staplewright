package io.github.rkaushikdev.staplewright.rekey;

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
import org.junit.jupiter.params.provider.MethodSource;

class ChoicesTest {

    private static <E extends Enum<E> & Valued> Stream<Arguments> each(
            Class<E> kind, Function<String, E> fromValue, Function<String, Optional<E>> find) {
        return Arrays.stream(kind.getEnumConstants()).map(choice -> Arguments.of(choice, fromValue, find));
    }

    static Stream<Arguments> everyChoiceReadsBackFromItsOwnValue() {
        return Stream.of(
                        each(Cause.class, Cause::fromValue, Cause::find),
                        each(OrderState.class, OrderState::fromValue, OrderState::find),
                        each(LineState.class, LineState::fromValue, LineState::find),
                        each(RetirementReason.class, RetirementReason::fromValue, RetirementReason::find),
                        each(WindowKind.class, WindowKind::fromValue, WindowKind::find),
                        each(Restriction.class, Restriction::fromValue, Restriction::find))
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
        InputError caught = catchThrowableOfType(InputError.class, () -> Cause.fromValue("misplaced"));
        assertThat(caught.message()).isEqualTo("no such cause");
        assertThat(caught.facts()).containsEntry("value", "misplaced");
    }

    @Test
    void aChoiceIsFoundByWhatItIsWrittenAsAndNotByItsName() {
        assertThat(Cause.find("STOLEN")).isEmpty();
        assertThat(Cause.find("stolen")).contains(Cause.STOLEN);
        assertThat(Choices.find(Cause.class, null)).isEmpty();
    }

    @Test
    void onlyAnInstalledOrSkippedLineIsSettled() {
        assertThat(LineState.SETTLED).containsExactlyInAnyOrder(LineState.INSTALLED, LineState.SKIPPED);
        assertThat(LineState.PINNED.isSettled()).isFalse();
    }

    @Test
    void onlyACompromisedCutIsRetiredForGood() {
        assertThat(RetirementReason.PERMANENT).containsExactly(RetirementReason.COMPROMISED);
        assertThat(RetirementReason.SUPERSEDED.isPermanent()).isFalse();
    }

    @Test
    void aRestrictionIsReadBackFromTheWordsThePlanPrints() {
        assertThat(Restriction.fromValue("exams running")).isSameAs(Restriction.EXAMS);
        assertThat(Restriction.find("EXAMS")).isEmpty();
    }

    @Test
    void aGradeAndAReachPrintAsTheirNumberAndReadAsTheirWord() {
        assertThat(Severity.CRITICAL).hasToString("4");
        assertThat(Severity.CRITICAL.label()).isEqualTo("critical");
        assertThat(Scope.NOTHING).hasToString("0");
        assertThat(Scope.BRANCH.label()).isEqualTo("branch");
    }
}
