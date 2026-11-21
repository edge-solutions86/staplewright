package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.deficienciesFromText;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.deficienciesToText;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.lockoutFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.lockoutToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.logFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.surveyFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.surveyLineFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.surveyLineToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.surveyToRows;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.CallState;
import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.Lockout;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.FacilitiesMappers.SurveyRows;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappersFacilitiesTest {

    private static final LocalDateTime CALLED = LocalDateTime.of(2025, 4, 8, 14, 12);
    private static final LocalDateTime ATTENDED = LocalDateTime.of(2025, 4, 8, 14, 31);
    private static final LocalDate WALKED = LocalDate.of(2025, 5, 12);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        session.add(new BuildingRow("MOO", "Moore Hall"));
        session.flush();
        session.addAll(List.of(
                new OpeningRow("MOO-214", "MOO", "2", "214", "office"),
                new OpeningRow("MOO-216", "MOO", "2", "216", "office")));
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static Lockout aCall() {
        return aCall(b -> b);
    }

    private static Lockout aCall(UnaryOperator<Lockout.Builder> overrides) {
        return overrides
                .apply(new Lockout("L-001", "MOO-214", "kmaki", CALLED).toBuilder())
                .build();
    }

    private static SurveyLine aLine(String opening) {
        return aLine(opening, b -> b);
    }

    private static SurveyLine aLine(String opening, UnaryOperator<SurveyLine.Builder> overrides) {
        return overrides
                .apply(new SurveyLine(opening, WALKED, "jkoivu", Condition.GOOD).toBuilder())
                .build();
    }

    /** A survey header nobody has written, for the mappings that need no database. */
    private static SurveyRow aLooseHeader() {
        return new SurveyRow("MOO", WALKED);
    }

    @Test
    void aCallSurvivesARoundTrip() {
        Lockout call = aCall();
        assertThat(lockoutFromRow(lockoutToRow(call))).isEqualTo(call);
    }

    @Test
    void anAttendedCallSurvives() {
        Lockout call =
                aCall(b -> b.state(CallState.ATTENDED).attendedAt(ATTENDED).technician("jkoivu"));
        Lockout back = lockoutFromRow(lockoutToRow(call));
        assertThat(back.responseMinutes()).contains(19);
        assertThat(back.state()).isSameAs(CallState.ATTENDED);
    }

    @Test
    void theIdentificationSurvives() {
        Lockout call = aCall(b -> b.state(CallState.OPENED)
                .attendedAt(ATTENDED)
                .technician("jkoivu")
                .identifiedBy(Identification.CAMPUS_CARD));
        assertThat(lockoutFromRow(lockoutToRow(call)).identifiedBy()).isSameAs(Identification.CAMPUS_CARD);
    }

    @Test
    void anIdentificationNobodyHasHeardOfReadsAsNothing() {
        LockoutRow row = lockoutToRow(aCall());
        row.setIdentifiedBy("by the cut of his jib");
        assertThat(lockoutFromRow(row).identifiedBy()).isSameAs(Identification.NONE);
    }

    @Test
    void aStateNobodyHasHeardOfIsRefused() {
        LockoutRow row = lockoutToRow(aCall());
        row.setState("pondered");
        assertThatThrownBy(() -> lockoutFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aWholeLogComesBackInCallOrder() {
        LockoutLog log = new LockoutLog();
        log.log(aCall(b -> b.number("L-002").calledAt(LocalDateTime.of(2025, 4, 8, 9, 0))));
        log.log(aCall());
        for (Lockout call : log) {
            session.add(lockoutToRow(call));
        }
        session.flush();
        List<LockoutRow> rows = session.all(LockoutRow.class);
        LockoutLog back = logFromRows(rows);
        List<String> numbers = new ArrayList<>();
        back.forEach(call -> numbers.add(call.number()));
        assertThat(numbers).containsExactly("L-002", "L-001");
    }

    @Test
    void theDeficienciesGoOutAsTheirOwnWords() {
        String text = deficienciesToText(List.of(Deficiency.WORN_OUT, Deficiency.UNIDENTIFIED));
        assertThat(text).isEqualTo("hardware needs replacing before it is rekeyed,cylinder cannot be identified");
    }

    @Test
    void theDeficienciesComeBackAsMembers() {
        String text = deficienciesToText(List.of(Deficiency.WORN_OUT, Deficiency.NO_STRIKE));
        assertThat(deficienciesFromText(text)).containsExactly(Deficiency.WORN_OUT, Deficiency.NO_STRIKE);
    }

    @Test
    void aWordThisVersionDoesNotKnowIsDropped() {
        String text = "hardware needs replacing before it is rekeyed,something from 2014";
        assertThat(deficienciesFromText(text)).containsExactly(Deficiency.WORN_OUT);
    }

    @Test
    void aWordTwiceIsKeptOnce() {
        String text = "cylinder cannot be identified,cylinder cannot be identified";
        assertThat(deficienciesFromText(text)).containsExactly(Deficiency.UNIDENTIFIED);
    }

    @Test
    void anEmptyColumnComesBackEmpty() {
        assertThat(deficienciesFromText("")).isEmpty();
    }

    @Test
    void aSurveyLineSurvivesARoundTrip() {
        SurveyLine line = aLine("MOO-214", b -> b.condition(Condition.FAILING).deficiencies(Deficiency.WORN_OUT));
        assertThat(surveyLineFromRow(surveyLineToRow(line, aLooseHeader()), WALKED))
                .isEqualTo(line);
    }

    @Test
    void aWholeSurveySurvivesTheDatabase() {
        Survey survey = new Survey("MOO", WALKED);
        survey.record(aLine("MOO-214"));
        survey.record(aLine("MOO-216", b -> b.condition(Condition.BROKEN)));
        SurveyRows rows = surveyToRows(survey);
        session.add(rows.survey());
        session.flush();
        for (SurveyLineRow row : rows.lines()) {
            session.add(row);
        }
        session.flush();
        SurveyRow stored = session.one(SurveyRow.class);
        Survey back = surveyFromRows(stored);
        assertThat(back.size()).isEqualTo(2);
        assertThat(back.line("MOO-216").condition()).isSameAs(Condition.BROKEN);
    }

    @Test
    void theSurveyHeaderCarriesTheBuildingAndTheDay() {
        Survey survey = new Survey("MOO", WALKED);
        SurveyRows rows = surveyToRows(survey);
        assertThat(rows.survey().getBuildingCode()).isEqualTo("MOO");
        assertThat(rows.survey().getWalkedOn()).isEqualTo(WALKED);
        assertThat(rows.lines()).isEmpty();
    }
}
