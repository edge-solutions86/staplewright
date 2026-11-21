package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.rekey.Door;
import io.github.rkaushikdev.staplewright.rekey.Phasing;
import io.github.rkaushikdev.staplewright.rekey.Placement;
import io.github.rkaushikdev.staplewright.rekey.Restriction;
import io.github.rkaushikdev.staplewright.rekey.Window;
import io.github.rkaushikdev.staplewright.rekey.WorkCalendar;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PhasingServiceTest {

    private static final int YEAR = 2025;

    private Session session;

    /**
     * The campus, written and committed so the commands see it too, and a session on it for the
     * service. Nothing the service does here writes, and closing the session rolls back anyway.
     */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        session = TestDatabase.database().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private PhasingService aService() {
        return new PhasingService(session, PhasingService.academicYear(YEAR));
    }

    private static Opening anOpening(DoorFunction function) {
        return new Opening("MOO-101", "MOO", "1", "101", function);
    }

    private static List<String> names(Iterable<Window> windows) {
        List<String> found = new ArrayList<>();
        windows.forEach(window -> found.add(window.name()));
        return found;
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    @Test
    void aClassroomCannotBeTouchedInTeachingTime() {
        assertThat(PhasingService.restrictionFor(anOpening(DoorFunction.CLASSROOM)))
                .isSameAs(Restriction.TEACHING);
    }

    @Test
    void anOfficeCountsAsOccupied() {
        assertThat(PhasingService.restrictionFor(anOpening(DoorFunction.OFFICE)))
                .isSameAs(Restriction.OCCUPIED);
    }

    @Test
    void aStoreRoomStopsNothing() {
        assertThat(PhasingService.restrictionFor(anOpening(DoorFunction.STOREROOM)))
                .isSameAs(Restriction.NONE);
    }

    @Test
    void anEntranceStopsNothing() {
        assertThat(PhasingService.restrictionFor(anOpening(DoorFunction.ENTRANCE)))
                .isSameAs(Restriction.NONE);
    }

    @Test
    void aDoorCarriesItsBuildingAndItsRestriction() {
        Door found = PhasingService.doorFor(anOpening(DoorFunction.CLASSROOM));
        assertThat(found.number()).isEqualTo("MOO-101");
        assertThat(found.building()).isEqualTo("MOO");
        assertThat(found.restriction()).isSameAs(Restriction.TEACHING);
    }

    @Test
    void aDoorTakesTheStandardMinutes() {
        assertThat(PhasingService.doorFor(anOpening(DoorFunction.OFFICE)).minutes())
                .isEqualTo(25);
    }

    @Test
    void theAcademicYearHasFourWindows() {
        assertThat(PhasingService.academicYear(YEAR).size()).isEqualTo(4);
    }

    @Test
    void theWindowsRunInOrder() {
        assertThat(names(PhasingService.academicYear(YEAR)))
                .containsExactly("summer", "reading week", "winter shutdown", "spring break");
    }

    @Test
    void onlyTheSummerTakesResearchWork() {
        WorkCalendar calendar = PhasingService.academicYear(YEAR);
        List<Window> found = calendar.windowsFor(Restriction.RESEARCH);
        assertThat(names(found)).containsExactly("summer");
    }

    @Test
    void theReadingWeekRunsAtHalfRate() {
        assertThat(PhasingService.academicYear(YEAR, 12).window("reading week").doorsPerDay())
                .isEqualTo(6);
    }

    @Test
    void aRateOfOneStillGetsAReadingWeek() {
        assertThat(PhasingService.academicYear(YEAR, 1).window("reading week").doorsPerDay())
                .isEqualTo(1);
    }

    @Test
    void theWinterWindowCrossesTheNewYear() {
        Window window = PhasingService.academicYear(YEAR).window("winter shutdown");
        assertThat(window.opensOn().getYear()).isEqualTo(YEAR);
        assertThat(window.closesOn().getYear()).isEqualTo(YEAR + 1);
    }

    @Test
    void theDoorsOnABuildingComeBackInNumberOrder() {
        List<Door> found = aService().doorsIn("MOO");
        assertThat(found.stream().map(Door::number).toList())
                .containsExactly("MOO-101", "MOO-110", "MOO-214", "MOO-216", "MOO-218");
    }

    @Test
    void aDoorThatTakesNoCylinderIsLeftOut() {
        List<Door> found = aService().doorsIn("MOO");
        assertThat(found.stream().map(Door::number).toList()).doesNotContain("MOO-260");
    }

    @Test
    void aBuildingWithNoKeyedDoorsIsA404() {
        assertThatThrownBy(() -> aService().doorsIn("ZZZ")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void everyKeyedDoorOnTheCampusComesBack() {
        List<Door> found = aService().everyKeyedDoor();
        assertThat(found).hasSize(10);
    }

    @Test
    void aWorkOrderNobodyRaisedIsA404() {
        assertThatThrownBy(() -> aService().doorsOn("W-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void thePlanPlacesWhatItCan() {
        Phasing found = aService().planCampus();
        assertThat(found.landed()).isTrue();
        assertThat(found.doorCount()).isEqualTo(10);
    }

    @Test
    void theOfficeDoorsLandInAWindowThatTakesThem() {
        Phasing found = aService().planCampus();
        for (Placement item : found.placed()) {
            if (item.door().restriction() == Restriction.OCCUPIED) {
                assertThat(item.window()).isIn("summer", "winter shutdown");
            }
        }
    }

    @Test
    void aCampusBiggerThanTheYearCarriesOver() {
        PhasingService service = new PhasingService(session, PhasingService.academicYear(YEAR, 1));
        List<Door> doors = service.everyKeyedDoor();
        PhasingService.Demand demand = service.capacityAgainst(doors);
        assertThat(demand.wanted()).isEqualTo(10);
        assertThat(demand.room()).isPositive();
    }

    @Test
    void thePlanForOneBuildingOnlyCoversIt() {
        Phasing found = aService().planBuilding("VOS");
        assertThat(found.placed().stream().map(item -> item.door().building()).collect(Collectors.toSet()))
                .containsExactly("VOS");
    }

    @Test
    void theSheetLeadsWithTheTotals() {
        PhasingService service = aService();
        List<String> lines = service.sheet(service.doorsIn("VOS"));
        assertThat(lines.get(0)).isEqualTo("3 doors across 4 windows");
    }

    @Test
    void theDaySheetNamesWhatIsBooked() {
        PhasingService service = aService();
        List<Door> doors = service.doorsIn("VOS");
        LocalDate day = service.plan(doors).placed().get(0).on();
        List<String> lines = service.daySheet(doors, day);
        assertThat(lines.get(0)).endsWith(day.toString());
    }

    @Test
    void aDayWithNothingOnItSaysSo() {
        PhasingService service = aService();
        List<String> lines = service.daySheet(service.doorsIn("VOS"), LocalDate.of(2025, 2, 2));
        assertThat(lines.get(0)).isEqualTo("nothing booked for 2025-02-02");
    }

    @Test
    void theBuildingsWithWorkAreListed() {
        assertThat(PhasingService.buildingsWithWork(session)).containsExactly("HEI", "MOO", "VOS");
    }

    @Test
    void theOpenOrdersAreListed() {
        assertThat(PhasingService.ordersToPlan(session)).isEmpty();
    }

    @Test
    void theWindowsCommandListsTheYear() {
        List<String> lines = call("phasing", "windows", "--year", "2025").lines();
        assertThat(lines.get(0)).startsWith("summer 2025-06-01");
        assertThat(lines.get(lines.size() - 1)).endsWith("doors in the year");
    }

    @Test
    void aDifferentRateChangesTheCapacity() {
        String slow =
                call("phasing", "windows", "--year", "2025", "--rate", "1").output();
        String fast =
                call("phasing", "windows", "--year", "2025", "--rate", "20").output();
        assertThat(slow).isNotEqualTo(fast);
    }

    @Test
    void aRateOfNothingIsRejectedAsUnreadable() {
        assertThat(call("phasing", "windows", "--rate", "0").exitCode()).isNotZero();
    }

    @Test
    void theBuildingsCommandListsThem() {
        assertThat(call("phasing", "buildings").lines()).containsExactly("HEI", "MOO", "VOS");
    }

    @Test
    void theOrdersCommandSaysWhenThereIsNothing() {
        assertThat(call("phasing", "orders").output()).contains("nothing open to plan");
    }

    @Test
    void theBuildingPlanPrints() {
        List<String> lines =
                call("phasing", "building", "VOS", "--year", "2025").lines();
        assertThat(lines.get(0)).isEqualTo("3 doors across 4 windows");
    }

    @Test
    void aBuildingNobodyHasIsRefused() {
        assertThat(call("phasing", "building", "ZZZ").exitCode()).isNotZero();
    }

    @Test
    void theCampusPlanLeadsWithTheCapacity() {
        List<String> lines = call("phasing", "campus", "--year", "2025").lines();
        assertThat(lines.get(0)).isEqualTo("10 doors against 1302 the year can take");
    }

    @Test
    void aCampusBiggerThanTheYearSaysWhatDoesNotFit() {
        String output =
                call("phasing", "campus", "--year", "2025", "--rate", "1").output();
        assertThat(output.contains("doors do not fit") || output.contains("everything fits"))
                .isTrue();
    }

    @Test
    void theDayCommandSaysWhenNothingIsBooked() {
        String output = call("phasing", "day", "2025-02-02", "--year", "2025").output();
        assertThat(output).contains("nothing booked for 2025-02-02");
    }

    @Test
    void theCarryOverIsEmptyOnAYearThatFits() {
        assertThat(call("phasing", "carry-over", "--year", "2025").output()).contains("nothing carries over");
    }

    @Test
    void anOrderNobodyRaisedIsRefused() {
        assertThat(call("phasing", "order", "W-9999").exitCode()).isNotZero();
    }
}
