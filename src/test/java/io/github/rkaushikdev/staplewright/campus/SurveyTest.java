package io.github.rkaushikdev.staplewright.campus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class SurveyTest {

    private static final LocalDate WALKED = LocalDate.of(2025, 5, 12);

    private static SurveyLine aLine() {
        return aLine("MOO-214");
    }

    private static SurveyLine aLine(String opening) {
        return new SurveyLine(opening, WALKED, "jkoivu", Condition.GOOD);
    }

    private static SurveyLine aLine(UnaryOperator<SurveyLine.Builder> overrides) {
        return aLine("MOO-214", overrides);
    }

    private static SurveyLine aLine(String opening, UnaryOperator<SurveyLine.Builder> overrides) {
        return overrides.apply(aLine(opening).toBuilder()).build();
    }

    private static Survey aSurvey() {
        Survey survey = new Survey("MOO", WALKED);
        survey.record(aLine("MOO-101", line -> line.condition(Condition.WORN)));
        survey.record(aLine("MOO-214"));
        survey.record(aLine("MOO-216", line -> line.condition(Condition.FAILING)
                .deficiencies(Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED)));
        survey.record(aLine("MOO-218", line -> line.condition(Condition.BROKEN).deficiencies(Deficiency.NO_CYLINDER)));
        return survey;
    }

    private static List<String> openings(Iterable<SurveyLine> lines) {
        return StreamSupport.stream(lines.spliterator(), false)
                .map(SurveyLine::opening)
                .toList();
    }

    @Test
    void aLineNamesTheDoorAndWhoWalkedIt() {
        SurveyLine line = aLine();
        assertThat(line.opening()).isEqualTo("MOO-214");
        assertThat(line.surveyor()).isEqualTo("jkoivu");
        assertThat(line.isSound()).isTrue();
    }

    static Stream<UnaryOperator<SurveyLine.Builder>> aLineWithoutTheEssentialsIsRefused() {
        return Stream.of(line -> line.opening("  "), line -> line.surveyor("  "));
    }

    @ParameterizedTest
    @MethodSource
    void aLineWithoutTheEssentialsIsRefused(UnaryOperator<SurveyLine.Builder> blanked) {
        assertThatThrownBy(() -> aLine(blanked)).isInstanceOf(InputError.class);
    }

    @Test
    void aDeficiencyListedTwiceIsRefused() {
        assertThatThrownBy(() -> aLine(line -> line.deficiencies(Deficiency.WORN_OUT, Deficiency.WORN_OUT)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aWornDoorWithNothingWrongIsStillSound() {
        assertThat(aLine(line -> line.condition(Condition.WORN)).isSound()).isTrue();
    }

    @Test
    void aFailingDoorIsNotSound() {
        assertThat(aLine(line -> line.condition(Condition.FAILING)).isSound()).isFalse();
    }

    @Test
    void aDoorWithADeficiencyIsNotSound() {
        assertThat(aLine(line -> line.deficiencies(Deficiency.UNIDENTIFIED)).isSound())
                .isFalse();
    }

    @Test
    void someDeficienciesHaveToBeDealtWithFirst() {
        assertThat(aLine(line -> line.deficiencies(Deficiency.WORN_OUT)).needsWorkFirst())
                .isTrue();
        assertThat(aLine(line -> line.deficiencies(Deficiency.NO_STRIKE)).needsWorkFirst())
                .isTrue();
        assertThat(aLine(line -> line.deficiencies(Deficiency.UNIDENTIFIED)).needsWorkFirst())
                .isFalse();
    }

    @Test
    void aLineDescribesItself() {
        assertThat(aLine().describe()).isEqualTo("MOO-214 good: nothing to report");
        String found = aLine(line -> line.deficiencies(Deficiency.UNIDENTIFIED)).describe();
        assertThat(found).contains("cannot be identified");
    }

    @Test
    void aSurveyCoversABuilding() {
        assertThatThrownBy(() -> new Survey(" ", WALKED)).isInstanceOf(InputError.class);
    }

    @Test
    void linesComeBackInDoorOrder() {
        assertThat(openings(aSurvey())).containsExactly("MOO-101", "MOO-214", "MOO-216", "MOO-218");
    }

    @Test
    void aDoorIsSurveyedOnce() {
        Survey survey = aSurvey();
        assertThatThrownBy(() -> survey.record(aLine("MOO-214"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSecondLookReplacesTheLine() {
        Survey survey = aSurvey();
        survey.revise("MOO-214", line -> line.condition(Condition.FAILING));
        assertThat(survey.line("MOO-214").condition()).isSameAs(Condition.FAILING);
        assertThat(survey.size()).isEqualTo(4);
    }

    @Test
    void aDoorNobodyWalked() {
        assertThatThrownBy(() -> aSurvey().line("MOO-999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theDoorsWithOneDeficiencyComeBackTogether() {
        assertThat(openings(aSurvey().withDeficiency(Deficiency.UNIDENTIFIED))).containsExactly("MOO-216");
    }

    @Test
    void theBlockingDoorsAreTheOnesThatNeedWorkFirst() {
        assertThat(openings(aSurvey().blocking())).containsExactly("MOO-216", "MOO-218");
    }

    @Test
    void theSoundDoorsAreTheOnesWithNothingWrong() {
        assertThat(openings(aSurvey().sound())).containsExactly("MOO-101", "MOO-214");
    }

    @Test
    void theWorstDoorComesFirst() {
        List<SurveyLine> found = aSurvey().worstFirst();
        assertThat(found.get(0).opening()).isEqualTo("MOO-218");
        assertThat(found.get(found.size() - 1).opening()).isEqualTo("MOO-214");
    }

    @Test
    void theConditionsAreCountedAcrossEveryValue() {
        assertThat(aSurvey().countsByCondition()).isEqualTo(Map.of("good", 1, "worn", 1, "failing", 1, "broken", 1));
    }

    @Test
    void theDeficienciesAreCountedWorstFirst() {
        Map<String, Integer> counts = aSurvey().countsByDeficiency();
        List<Integer> values = new ArrayList<>(counts.values());
        assertThat(values)
                .isEqualTo(values.stream().sorted(Comparator.reverseOrder()).toList());
        assertThat(counts).hasSize(3);
    }

    @Test
    void aSurveyWithNothingWrongCountsNoDeficiencies() {
        Survey survey = new Survey("VOS", WALKED);
        survey.record(aLine("VOS-311"));
        assertThat(survey.countsByDeficiency()).isEmpty();
    }

    @Test
    void theDoorsNobodyWalkedAreReported() {
        assertThat(aSurvey().coverage(List.of("MOO-214", "MOO-260"))).containsExactly("MOO-260");
    }

    @Test
    void aFinishedSurveyCoversEverything() {
        assertThat(aSurvey().coverage(List.of("MOO-101", "MOO-214"))).isEmpty();
    }

    @Test
    void theSurveyorsAreListedOnceEach() {
        Survey survey = aSurvey();
        survey.record(aLine("MOO-260", line -> line.surveyor("phamm")));
        assertThat(survey.surveyors()).containsExactly("jkoivu", "phamm");
    }

    @Test
    void aSurveyIsAContainer() {
        Survey survey = aSurvey();
        assertThat(survey.contains("MOO-214")).isTrue();
        assertThat(survey.contains("MOO-999")).isFalse();
        assertThat(survey.size()).isEqualTo(4);
    }
}
