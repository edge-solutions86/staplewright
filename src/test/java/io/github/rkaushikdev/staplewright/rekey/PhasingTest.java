package io.github.rkaushikdev.staplewright.rekey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PhasingTest {

    private static final Window SUMMER =
            new Window("summer", WindowKind.VACATION, LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 6), 4);
    private static final Window AUTUMN =
            new Window("autumn term", WindowKind.TERM, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 5), 4);
    private static final Window WINTER = new Window(
            "winter shutdown", WindowKind.SHUTDOWN, LocalDate.of(2025, 12, 22), LocalDate.of(2025, 12, 24), 4);

    private static WorkCalendar aCalendar() {
        WorkCalendar calendar = new WorkCalendar();
        calendar.add(SUMMER, List.of(Restriction.OCCUPIED, Restriction.TEACHING));
        calendar.add(AUTUMN, List.of());
        calendar.add(WINTER, List.of(Restriction.TEACHING));
        return calendar;
    }

    private static Door aDoor(String number) {
        return aDoor(number, Restriction.NONE);
    }

    private static Door aDoor(String number, Restriction restriction) {
        return new Door(number, number.split("-", -1)[0], restriction);
    }

    private static List<Door> mooreDoors(int from, int to) {
        return IntStream.range(from, to).mapToObj(n -> aDoor("MOO-" + n)).toList();
    }

    @Test
    void aWindowNeedsAName() {
        assertThatThrownBy(() -> new Window(" ", WindowKind.TERM, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aWindowClosesAfterItOpens() {
        assertThatThrownBy(() -> new Window("bad", WindowKind.TERM, LocalDate.of(2025, 1, 5), LocalDate.of(2025, 1, 1)))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aWindowDoesSomeWork() {
        assertThatThrownBy(() ->
                        new Window("idle", WindowKind.TERM, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 1, 2), 0))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aWindowCountsItsDaysInclusively() {
        assertThat(SUMMER.days()).isEqualTo(5);
    }

    @Test
    void theCapacityIsDaysTimesTheRate() {
        assertThat(SUMMER.capacity()).isEqualTo(20);
    }

    @Test
    void aWindowKnowsWhichDaysItCovers() {
        assertThat(SUMMER.covers(LocalDate.of(2025, 6, 4))).isTrue();
        assertThat(SUMMER.covers(LocalDate.of(2025, 6, 7))).isFalse();
    }

    @Test
    void twoWindowsThatDoNotTouchDoNotOverlap() {
        assertThat(SUMMER.overlaps(AUTUMN)).isFalse();
    }

    @Test
    void aWindowInsideAnotherOverlapsIt() {
        Window inside = new Window("inside", WindowKind.WEEKEND, LocalDate.of(2025, 6, 3), LocalDate.of(2025, 6, 4));
        assertThat(SUMMER.overlaps(inside)).isTrue();
    }

    @Test
    void theFirstDoorsLandOnTheOpeningDay() {
        assertThat(SUMMER.dayFor(0)).isEqualTo(LocalDate.of(2025, 6, 2));
        assertThat(SUMMER.dayFor(3)).isEqualTo(LocalDate.of(2025, 6, 2));
    }

    @Test
    void theNextDoorRollsOntoTheNextDay() {
        assertThat(SUMMER.dayFor(4)).isEqualTo(LocalDate.of(2025, 6, 3));
    }

    @Test
    void aPositionBeforeTheStartIsRefused() {
        assertThatThrownBy(() -> SUMMER.dayFor(-1)).isInstanceOf(InputError.class);
    }

    @Test
    void aWindowReadsBackAsOneLine() {
        assertThat(SUMMER.describe()).isEqualTo("summer 2025-06-02 to 2025-06-06, 20 doors");
    }

    @Test
    void aDoorTakesSomeMinutes() {
        assertThatThrownBy(() -> new Door("MOO-101", "MOO", Restriction.NONE, 0))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aDoorWithNothingStoppingItIsFree() {
        assertThat(aDoor("MOO-101").isFree()).isTrue();
    }

    @Test
    void aCalendarRefusesAWindowThatRunsOverAnother() {
        WorkCalendar calendar = aCalendar();
        Window clash = new Window("clash", WindowKind.WEEKEND, LocalDate.of(2025, 6, 4), LocalDate.of(2025, 6, 8));
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> calendar.add(clash));
        assertThat(caught.facts().get("against")).isEqualTo("summer");
    }

    @Test
    void aWindowNobodyAddedIsA404() {
        assertThatThrownBy(() -> new WorkCalendar().window("spring")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void everyWindowTakesUnrestrictedWork() {
        WorkCalendar calendar = aCalendar();
        assertThat(calendar.allows("autumn term", Restriction.NONE)).isTrue();
    }

    @Test
    void aWindowOnlyTakesWhatItWasToldTo() {
        WorkCalendar calendar = aCalendar();
        assertThat(calendar.allows("summer", Restriction.OCCUPIED)).isTrue();
        assertThat(calendar.allows("autumn term", Restriction.OCCUPIED)).isFalse();
    }

    @Test
    void theWindowsComeBackInDateOrder() {
        List<String> names = new ArrayList<>();
        aCalendar().forEach(window -> names.add(window.name()));
        assertThat(names).containsExactly("summer", "autumn term", "winter shutdown");
    }

    @Test
    void theWindowsOpenToARestrictionAreNamed() {
        WorkCalendar calendar = aCalendar();
        List<Window> found = calendar.windowsFor(Restriction.TEACHING);
        assertThat(found).extracting(Window::name).containsExactly("summer", "winter shutdown");
    }

    @Test
    void theTotalCapacityAddsTheWindowsUp() {
        assertThat(aCalendar().totalCapacity()).isEqualTo(20 + 20 + 12);
    }

    @Test
    void aCalendarCountsAndContains() {
        WorkCalendar calendar = aCalendar();
        assertThat(calendar.size()).isEqualTo(3);
        assertThat(calendar.contains("summer")).isTrue();
    }

    @Test
    void aFreeDoorLandsInTheFirstWindow() {
        Phasing found = Phasing.phase(aCalendar(), List.of(aDoor("MOO-101")));
        assertThat(found.placed().get(0).window()).isEqualTo("summer");
        assertThat(found.placed().get(0).on()).isEqualTo(LocalDate.of(2025, 6, 2));
    }

    @Test
    void aRestrictedDoorSkipsTheWindowThatWillNotTakeIt() {
        Phasing found = Phasing.phase(aCalendar(), List.of(aDoor("MOO-101", Restriction.OCCUPIED)));
        assertThat(found.placed().get(0).window()).isEqualTo("summer");
    }

    @Test
    void aDoorNothingWillTakeDoesNotLand() {
        Phasing found = Phasing.phase(aCalendar(), List.of(aDoor("MOO-101", Restriction.EXAMS)));
        assertThat(found.placed()).isEmpty();
        assertThat(found.unplaced().get(0).reason()).isEqualTo("no window takes work that is exams running");
    }

    @Test
    void aFullCalendarSaysTheWindowsAreFull() {
        WorkCalendar calendar = new WorkCalendar();
        calendar.add(new Window("tiny", WindowKind.WEEKEND, LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 2), 1));
        Phasing found = Phasing.phase(calendar, List.of(aDoor("MOO-101"), aDoor("MOO-110")));
        assertThat(found.placed()).hasSize(1);
        assertThat(found.unplaced().get(0).reason()).isEqualTo("every window that would take it is full");
    }

    @Test
    void theHardestDoorsArePlacedFirst() {
        WorkCalendar calendar = new WorkCalendar();
        calendar.add(
                new Window("only", WindowKind.VACATION, LocalDate.of(2025, 6, 2), LocalDate.of(2025, 6, 2), 1),
                List.of(Restriction.OCCUPIED));
        calendar.add(new Window("open", WindowKind.TERM, LocalDate.of(2025, 9, 1), LocalDate.of(2025, 9, 1), 1));
        List<Door> doors = List.of(aDoor("MOO-101"), aDoor("VOS-100", Restriction.OCCUPIED));
        Phasing found = Phasing.phase(calendar, doors);
        Map<String, String> landed = new LinkedHashMap<>();
        found.placed().forEach(item -> landed.put(item.door().number(), item.window()));
        assertThat(landed).containsEntry("VOS-100", "only");
        assertThat(landed).containsEntry("MOO-101", "open");
    }

    @Test
    void thePlacementsComeBackInDayOrder() {
        Phasing found = Phasing.phase(aCalendar(), mooreDoors(101, 111));
        List<LocalDate> days = found.placed().stream().map(Placement::on).toList();
        assertThat(days).isEqualTo(days.stream().sorted().toList());
    }

    @Test
    void theDoorsInOneWindowComeBackTogether() {
        Phasing found = Phasing.phase(aCalendar(), mooreDoors(101, 130));
        assertThat(found.inWindow("summer")).hasSize(20);
    }

    @Test
    void theDoorsOnOneDayComeBackTogether() {
        Phasing found = Phasing.phase(aCalendar(), mooreDoors(101, 111));
        assertThat(found.onDay(LocalDate.of(2025, 6, 2))).hasSize(4);
    }

    @Test
    void theLoadCoversEveryWindowEvenTheEmptyOnes() {
        Phasing found = Phasing.phase(aCalendar(), List.of(aDoor("MOO-101")));
        assertThat(found.loadByWindow()).isEqualTo(Map.of("summer", 1, "autumn term", 0, "winter shutdown", 0));
    }

    @Test
    void theSpareCapacityIsWhatIsLeft() {
        Phasing found = Phasing.phase(aCalendar(), List.of(aDoor("MOO-101")));
        assertThat(found.spareCapacity().get("summer")).isEqualTo(19);
    }

    @Test
    void aPlanWhereEverythingFitsHasLanded() {
        assertThat(Phasing.phase(aCalendar(), List.of(aDoor("MOO-101"))).landed())
                .isTrue();
    }

    @Test
    void theReasonsAreCountedMostCommonFirst() {
        List<Door> doors = List.of(aDoor("MOO-101", Restriction.EXAMS), aDoor("MOO-110", Restriction.EXAMS));
        Phasing found = Phasing.phase(aCalendar(), doors);
        assertThat(found.reasons()).isEqualTo(Map.of("no window takes work that is exams running", 2));
    }

    @Test
    void theDoorCountCoversBothHalves() {
        List<Door> doors = List.of(aDoor("MOO-101"), aDoor("MOO-110", Restriction.EXAMS));
        assertThat(Phasing.phase(aCalendar(), doors).doorCount()).isEqualTo(2);
    }

    @Test
    void theYearPlanLeadsWithTheTotals() {
        List<String> lines = Phasing.yearPlan(aCalendar(), List.of(aDoor("MOO-101")));
        assertThat(lines.get(0)).isEqualTo("1 doors across 3 windows");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("everything fits this year");
    }

    @Test
    void theYearPlanNamesTheLoadPerWindow() {
        List<String> lines = Phasing.yearPlan(aCalendar(), List.of(aDoor("MOO-101")));
        assertThat(lines.get(1)).contains("summer");
        assertThat(lines.get(1)).contains("1 placed");
    }

    @Test
    void theYearPlanCountsWhatDoesNotFit() {
        List<Door> doors = List.of(aDoor("MOO-101", Restriction.EXAMS));
        List<String> lines = Phasing.yearPlan(aCalendar(), doors);
        assertThat(lines.get(lines.size() - 2)).isEqualTo("1 doors do not fit");
    }

    @Test
    void whatDidNotFitCarriesOver() {
        List<Door> doors = List.of(aDoor("MOO-101"), aDoor("MOO-110", Restriction.EXAMS));
        Phasing found = Phasing.phase(aCalendar(), doors);
        assertThat(found.carryOver()).extracting(Door::number).containsExactly("MOO-110");
    }

    @Test
    void aPlanThatFitsCarriesNothingOver() {
        assertThat(Phasing.phase(aCalendar(), List.of(aDoor("MOO-101"))).carryOver())
                .isEmpty();
    }
}
