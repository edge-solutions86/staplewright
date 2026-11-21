package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsFacilitiesTest {

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

    private LockoutRow aCall() {
        return aCall(call -> {});
    }

    private LockoutRow aCall(Consumer<LockoutRow> overrides) {
        LockoutRow row = new LockoutRow("L-001", "MOO-214", "kmaki", CALLED);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private SurveyRow aSurvey() {
        return aSurvey("MOO", WALKED);
    }

    private SurveyRow aSurvey(String buildingCode, LocalDate walkedOn) {
        SurveyRow row = new SurveyRow(buildingCode, walkedOn);
        session.add(row);
        session.flush();
        return row;
    }

    private static SurveyLineRow aLine(SurveyRow survey, String surveyor, int condition, String deficiencies) {
        SurveyLineRow line = SurveyLineRow.of(survey, "MOO-214", surveyor, condition);
        line.setDeficiencies(deficiencies);
        return line;
    }

    @Test
    void theFacilitiesTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("lockouts", "surveys", "survey_lines");
    }

    @Test
    void aCallRoundTrips() {
        LockoutRow row = aCall();
        assertThat(row.getState()).isEqualTo("logged");
        assertThat(row.isOpen()).isTrue();
        assertThat(row.toString()).contains("L-001");
    }

    @Test
    void nobodyAttendedBeforeTheCall() {
        assertThatThrownBy(() -> aCall(call -> call.setAttendedAt(LocalDateTime.of(2025, 4, 8, 13, 0))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anAttendedCallNamesATechnician() {
        assertThatThrownBy(() -> aCall(call -> {
                    call.setState("attended");
                    call.setAttendedAt(ATTENDED);
                }))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anAttendedCallWithATechnicianIsFine() {
        LockoutRow row = aCall(call -> {
            call.setState("attended");
            call.setAttendedAt(ATTENDED);
            call.setTechnician("jkoivu");
        });
        assertThat(row.getTechnician()).isEqualTo("jkoivu");
    }

    @Test
    void aCallNeedsADoorThatExists() {
        assertThatThrownBy(() -> aCall(call -> call.setOpeningNumber("ZZZ-1")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aClosedCallIsNotOpen() {
        LockoutRow row = aCall(call -> call.setState("opened"));
        assertThat(row.isOpen()).isFalse();
    }

    @Test
    void aSurveyCoversABuildingOnADay() {
        SurveyRow row = aSurvey();
        assertThat(row.getLines()).isEmpty();
        assertThat(row.toString()).contains("MOO");
    }

    @Test
    void aBuildingIsSurveyedOnceADay() {
        aSurvey();
        assertThatThrownBy(this::aSurvey).isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameBuildingOnAnotherDayIsFine() {
        aSurvey();
        aSurvey("MOO", LocalDate.of(2025, 5, 13));
        assertThat(session.all(SurveyRow.class)).hasSize(2);
    }

    @Test
    void aSurveyNeedsABuildingThatExists() {
        assertThatThrownBy(() -> aSurvey("ZZZ", WALKED)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void linesHangOffASurvey() {
        SurveyRow survey = aSurvey();
        SurveyLineRow worn = SurveyLineRow.of(survey, "MOO-216", "jkoivu", 3);
        worn.setDeficiencies("worn out,unidentified");
        session.addAll(List.of(SurveyLineRow.of(survey, "MOO-214", "jkoivu", 1), worn));
        session.flush();
        session.refresh(survey);
        assertThat(survey.getLines()).hasSize(2);
    }

    @Test
    void theDeficienciesReadBackAsAList() {
        SurveyRow survey = aSurvey();
        SurveyLineRow row = aLine(survey, "jkoivu", 2, "worn out,unidentified");
        session.add(row);
        session.flush();
        assertThat(row.deficiencyList()).containsExactly("worn out", "unidentified");
    }

    @Test
    void aLineWithNoDeficienciesReadsBackEmpty() {
        SurveyRow survey = aSurvey();
        SurveyLineRow row = SurveyLineRow.of(survey, "MOO-214", "jkoivu", 1);
        session.add(row);
        session.flush();
        assertThat(row.deficiencyList()).isEmpty();
    }

    @Test
    void aDoorIsSurveyedOncePerWalk() {
        SurveyRow survey = aSurvey();
        session.add(SurveyLineRow.of(survey, "MOO-214", "jkoivu", 1));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(SurveyLineRow.of(survey, "MOO-214", "phamm", 2));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aConditionOutsideTheScaleIsRefused() {
        SurveyRow survey = aSurvey();
        assertThatThrownBy(() -> {
                    session.add(SurveyLineRow.of(survey, "MOO-214", "jkoivu", 9));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingASurveyTakesItsLines() {
        SurveyRow survey = aSurvey();
        session.add(SurveyLineRow.of(survey, "MOO-214", "jkoivu", 1));
        session.flush();
        session.delete(survey);
        session.flush();
        assertThat(session.all(SurveyLineRow.class)).isEmpty();
    }
}
