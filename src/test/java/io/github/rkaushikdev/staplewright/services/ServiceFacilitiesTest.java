package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.LockoutLog;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyLineRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceFacilitiesTest {

    private static final LocalDateTime CALLED = LocalDateTime.of(2025, 4, 8, 14, 12);
    private static final LocalDateTime ATTENDED = LocalDateTime.of(2025, 4, 8, 14, 31);
    private static final LocalDateTime CLOSED = LocalDateTime.of(2025, 4, 8, 14, 35);
    private static final LocalDate WALKED = LocalDate.of(2025, 5, 12);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private LockoutService aLockoutService() {
        LockoutService service = new LockoutService(session);
        service.openSeries();
        return service;
    }

    private static LockoutRow aLoggedCall(LockoutService service) {
        return aLoggedCall(service, "MOO-214");
    }

    private static LockoutRow aLoggedCall(LockoutService service, String opening) {
        return service.log(opening, "kmaki", CALLED);
    }

    @Test
    void aCallGetsTheNextNumber() {
        LockoutService service = aLockoutService();
        assertThat(aLoggedCall(service).getNumber()).isEqualTo("L-00001");
        assertThat(aLoggedCall(service).getNumber()).isEqualTo("L-00002");
    }

    @Test
    void aCallStartsLogged() {
        LockoutService service = aLockoutService();
        assertThat(aLoggedCall(service).getState()).isEqualTo("logged");
    }

    @Test
    void aCallNamesSomebody() {
        LockoutService service = aLockoutService();
        assertThatThrownBy(() -> service.log("MOO-214", "  ", CALLED)).isInstanceOf(InputError.class);
    }

    @Test
    void aCallAboutADoorNobodyHas() {
        LockoutService service = aLockoutService();
        assertThatThrownBy(() -> service.log("ZZZ-1", "kmaki", CALLED)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void attendingRecordsWhoAndWhen() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        LockoutRow row = service.attend(number, "jkoivu", ATTENDED);
        assertThat(row.getState()).isEqualTo("attended");
        assertThat(row.getTechnician()).isEqualTo("jkoivu");
    }

    @Test
    void somebodyHasToAttend() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        assertThatThrownBy(() -> service.attend(number, " ", ATTENDED)).isInstanceOf(InputError.class);
    }

    @Test
    void nobodyAttendsBeforeTheCall() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        assertThatThrownBy(() -> service.attend(number, "jkoivu", LocalDateTime.of(2025, 4, 8, 13, 0)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aDoorCannotBeOpenedBeforeAnybodyAttends() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        assertThatThrownBy(() -> service.openDoor(number, Identification.CAMPUS_CARD, CLOSED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void openingRecordsTheIdentification() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        service.attend(number, "jkoivu", ATTENDED);
        LockoutRow row = service.openDoor(number, Identification.ROSTER, CLOSED);
        assertThat(row.getState()).isEqualTo("opened");
        assertThat(row.getIdentifiedBy()).isEqualTo(Identification.ROSTER.value());
    }

    @Test
    void anUnidentifiedPersonDoesNotGetLetIn() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        service.attend(number, "jkoivu", ATTENDED);
        assertThatThrownBy(() -> service.openDoor(number, Identification.NONE, CLOSED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aRefusalSaysWhy() {
        LockoutService service = aLockoutService();
        String number = aLoggedCall(service).getNumber();
        service.attend(number, "jkoivu", ATTENDED);
        assertThatThrownBy(() -> service.refuse(number, CLOSED, "  ")).isInstanceOf(InputError.class);
        assertThat(service.refuse(number, CLOSED, "not on the roster").getState())
                .isEqualTo("refused");
    }

    @Test
    void aCallThatIsNotThere() {
        LockoutService service = aLockoutService();
        assertThatThrownBy(() -> service.attend("L-99999", "jkoivu", ATTENDED)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theLogBookReadsBack() {
        LockoutService service = aLockoutService();
        aLoggedCall(service);
        aLoggedCall(service, "MOO-216");
        LockoutLog book = service.logBook();
        assertThat(book.size()).isEqualTo(2);
        assertThat(book.countsByOpening()).containsEntry("MOO-214", 1);
    }

    @Test
    void aSurveyStartsOnceADay() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        assertThatThrownBy(() -> service.start("MOO", WALKED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void theSameBuildingOnAnotherDayIsANewSurvey() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        service.start("MOO", LocalDate.of(2025, 5, 13));
        assertThat(service.survey("MOO", LocalDate.of(2025, 5, 13))).isNotNull();
    }

    @Test
    void aLineIsRecordedAgainstTheWalk() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        SurveyLineRow row = service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.WORN);
        assertThat(row.getCondition()).isEqualTo(2);
        assertThat(service.survey("MOO", WALKED).line("MOO-214").condition()).isSameAs(Condition.WORN);
    }

    @Test
    void aSecondLookCorrectsTheLine() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.WORN);
        service.record("MOO", WALKED, "MOO-214", "phamm", Condition.BROKEN);
        Survey survey = service.survey("MOO", WALKED);
        assertThat(survey.size()).isEqualTo(1);
        assertThat(survey.line("MOO-214").condition()).isSameAs(Condition.BROKEN);
        assertThat(survey.line("MOO-214").surveyor()).isEqualTo("phamm");
    }

    @Test
    void theDeficienciesSurviveACorrection() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.FAILING, List.of(Deficiency.WORN_OUT));
        SurveyLine line = service.survey("MOO", WALKED).line("MOO-214");
        assertThat(line.deficiencies()).containsExactly(Deficiency.WORN_OUT);
        assertThat(line.needsWorkFirst()).isTrue();
    }

    @Test
    void aLineOnAWalkThatNeverHappened() {
        SurveyService service = new SurveyService(session);
        assertThatThrownBy(() -> service.record("MOO", WALKED, "MOO-214", "jkoivu", Condition.GOOD))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theLatestSurveyIsTheMostRecentWalk() {
        SurveyService service = new SurveyService(session);
        service.start("MOO", WALKED);
        service.start("MOO", LocalDate.of(2025, 9, 1));
        service.record("MOO", LocalDate.of(2025, 9, 1), "MOO-214", "jkoivu", Condition.BROKEN);
        Survey latest = service.latest("MOO").orElseThrow();
        assertThat(latest.walkedOn()).isEqualTo(LocalDate.of(2025, 9, 1));
        assertThat(latest.size()).isEqualTo(1);
    }

    @Test
    void aBuildingNobodyWalkedHasNoLatest() {
        Optional<Survey> latest = new SurveyService(session).latest("VOS");
        assertThat(latest).isEmpty();
    }
}
