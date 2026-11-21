package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class OpeningsTest {

    private static List<String> numbers(Iterable<Opening> openings) {
        return StreamSupport.stream(openings.spliterator(), false)
                .map(Opening::number)
                .toList();
    }

    @Test
    void aBuildingCarriesItsCodeAndItsYear() {
        Building moore = aCampus().building("MOO");
        assertThat(moore.name()).isEqualTo("Moore Hall");
        assertThat(moore.yearBuilt()).contains(1953);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "moo", " MOO"})
    void aBuildingCodeIsUpperCaseAndTrimmed(String code) {
        assertThatThrownBy(() -> new Building(code, "Moore Hall")).isInstanceOf(InputError.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1700, 2200})
    void aBuildingYearHasToBePlausible(int year) {
        assertThatThrownBy(() -> new Building("MOO", "Moore Hall", year)).isInstanceOf(InputError.class);
    }

    @Test
    void aBuildingWithNoYearIsFine() {
        assertThat(new Building("MOO", "Moore Hall").yearBuilt()).isEmpty();
    }

    @Test
    void anOpeningNamesWhereItIs() {
        Opening opening = aCampus().opening("MOO-214");
        assertThat(opening.label()).isEqualTo("MOO 214");
        assertThat(opening.describe()).isEqualTo("MOO-214: MOO floor 2 room 214, office");
    }

    @Test
    void aKeyedFunctionTakesACylinderAndPrivacyDoesNot() {
        OpeningSchedule campus = aCampus();
        assertThat(campus.opening("MOO-214").isKeyed()).isTrue();
        assertThat(campus.opening("HEI-G12").isKeyed()).isTrue();
        assertThat(campus.opening("MOO-260").isKeyed()).isFalse();
    }

    @Test
    void anOpeningNeedsANumberAndABuilding() {
        assertThatThrownBy(() -> new Opening("", "MOO", "1", "101", DoorFunction.OFFICE))
                .isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new Opening("MOO-101", "  ", "1", "101", DoorFunction.OFFICE))
                .isInstanceOf(InputError.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = DoorFunction.class,
            names = {"PRIVACY", "CABINET"})
    void someFunctionsDoNotBelongOnAnExteriorDoor(DoorFunction function) {
        assertThatThrownBy(() -> new Opening("MOO-999", "MOO", "1", "999", function, true))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theSameFunctionInsideIsFine() {
        assertThat(new Opening("MOO-999", "MOO", "1", "999", DoorFunction.PRIVACY).number())
                .isEqualTo("MOO-999");
    }

    @Test
    void openingsComeBackByBuildingInWalkingOrder() {
        assertThat(numbers(aCampus().inBuilding("MOO")))
                .containsExactly("MOO-101", "MOO-110", "MOO-214", "MOO-216", "MOO-218", "MOO-260");
    }

    @Test
    void aFloorIsASliceOfABuilding() {
        assertThat(numbers(aCampus().onFloor("MOO", "2"))).containsExactly("MOO-214", "MOO-216", "MOO-218", "MOO-260");
        assertThat(aCampus().onFloor("MOO", "9")).isEmpty();
    }

    @Test
    void theFloorsOfABuildingAreReportedOnceEach() {
        assertThat(aCampus().floors("MOO")).containsExactly("1", "2");
        assertThat(aCampus().floors("HEI")).containsExactly("1", "B");
    }

    @Test
    void openingsCanBePulledOutByFunction() {
        assertThat(numbers(aCampus().withFunction(DoorFunction.STOREROOM))).containsExactly("HEI-001", "MOO-218");
    }

    @Test
    void theKeyedOpeningsLeaveOutTheOnesThatTakeNoCylinder() {
        OpeningSchedule campus = aCampus();
        Set<String> keyed = campus.keyedOpenings().stream().map(Opening::number).collect(Collectors.toSet());
        assertThat(keyed).doesNotContain("MOO-260");
        assertThat(keyed).hasSize(campus.size() - 1);
    }

    @Test
    void theExteriorOpeningsAreTheOnesFacingOut() {
        assertThat(numbers(aCampus().exteriorOpenings())).containsExactly("MOO-101", "VOS-100");
    }

    @Test
    void aCountByBuildingCoversEveryBuilding() {
        Map<String, Integer> counts = aCampus().countByBuilding();
        assertThat(counts).isEqualTo(Map.of("HEI", 2, "MOO", 6, "VOS", 3));
        assertThat(counts.keySet()).containsExactly("HEI", "MOO", "VOS");
    }

    @Test
    void aBuildingWithNoOpeningsStillCountsZero() {
        OpeningSchedule campus = aCampus();
        campus.addBuilding(new Building("SCI", "Science Annex"));
        assertThat(campus.countByBuilding()).containsEntry("SCI", 0);
    }

    @Test
    void anOpeningInABuildingNobodyAddedIsRefused() {
        OpeningSchedule campus = aCampus();
        assertThatThrownBy(() -> campus.add(new Opening("ZZZ-1", "ZZZ", "1", "1", DoorFunction.OFFICE)))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void twoOpeningsCannotShareANumber() {
        OpeningSchedule campus = aCampus();
        assertThatThrownBy(() -> campus.add(new Opening("MOO-214", "MOO", "2", "214", DoorFunction.OFFICE)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aBuildingCannotBeAddedTwice() {
        OpeningSchedule campus = aCampus();
        assertThatThrownBy(() -> campus.addBuilding(new Building("MOO", "Moore Hall")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingForSomethingThatIsNotThere() {
        OpeningSchedule campus = aCampus();
        assertThatThrownBy(() -> campus.opening("MOO-999")).isInstanceOf(NotFoundError.class);
        assertThatThrownBy(() -> campus.building("ZZZ")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aDoorThatGotBrickedUpComesOffTheList() {
        OpeningSchedule campus = aCampus();
        int before = campus.size();
        campus.remove("MOO-218");
        assertThat(campus.size()).isEqualTo(before - 1);
        assertThat(campus.contains("MOO-218")).isFalse();
    }

    @Test
    void removingADoorTwiceIsRefused() {
        OpeningSchedule campus = aCampus();
        campus.remove("MOO-218");
        assertThatThrownBy(() -> campus.remove("MOO-218")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theScheduleIsAContainerInNumberOrder() {
        OpeningSchedule campus = aCampus();
        assertThat(campus.contains("MOO-214")).isTrue();
        assertThat(campus.contains("MOO-999")).isFalse();
        List<String> numbers = numbers(campus);
        assertThat(numbers).isEqualTo(numbers.stream().sorted().toList());
    }

    @Test
    void anEmptyScheduleHoldsNothing() {
        assertThat(new OpeningSchedule().size()).isZero();
        assertThat(new OpeningSchedule()).isEmpty();
    }
}
