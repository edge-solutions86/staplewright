package io.github.rkaushikdev.staplewright.rekey;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SchedulingTest {

    private static final RateCard RATES = new RateCard();

    private static WorkOrder anOrder() {
        return anOrder(List.of(new String[] {"C-1", "MOO-214"}, new String[] {"C-2", "MOO-216"}));
    }

    private static WorkOrder anOrder(List<String[]> doors) {
        List<OrderLine> lines = new ArrayList<>();
        for (String[] door : doors) {
            lines.add(new OrderLine(door[0], door[1]));
        }
        return new WorkOrder("W-1041", "I-1", lines).withTechnician("jkoivu");
    }

    private static WorkOrder aBigOrder() {
        return anOrder(List.of(
                new String[] {"C-1", "VOS-311"},
                new String[] {"C-2", "MOO-214"},
                new String[] {"C-3", "HEI-001"},
                new String[] {"C-4", "MOO-101"},
                new String[] {"C-5", "VOS-312"}));
    }

    @Test
    void theRouteWalksBuildingThenFloorThenDoor() {
        List<Stop> stops = DayPlan.route(aBigOrder().lines(), aCampus(), RATES);
        assertThat(stops)
                .extracting(Stop::opening)
                .containsExactly("HEI-001", "MOO-101", "MOO-214", "VOS-311", "VOS-312");
    }

    @Test
    void aStopCarriesWhereItIsAndHowLongItTakes() {
        Stop stop = DayPlan.route(anOrder().lines(), aCampus(), RATES).get(0);
        assertThat(stop.building()).isEqualTo("MOO");
        assertThat(stop.minutes()).isEqualTo(RATES.doorMinutesPerCylinder());
        assertThat(stop.describe()).contains("MOO-214");
    }

    @Test
    void aDayAddsUpBenchDoorAndTravel() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES);
        assertThat(plan.benchMinutes()).isEqualTo(RATES.benchMinutesPerCylinder() * 2);
        assertThat(plan.doorMinutes()).isEqualTo(RATES.doorMinutesPerCylinder() * 2);
        assertThat(plan.travelMinutes()).isEqualTo(RATES.travelMinutesPerBuilding());
        assertThat(plan.totalMinutes()).isEqualTo(plan.benchMinutes() + plan.doorMinutes() + plan.travelMinutes());
    }

    @Test
    void aDayAcrossThreeBuildingsPaysThreeTrips() {
        DayPlan plan = DayPlan.planDay(aBigOrder(), aCampus(), RATES);
        assertThat(plan.buildings()).containsExactly("HEI", "MOO", "VOS");
        assertThat(plan.travelMinutes()).isEqualTo(RATES.travelMinutesPerBuilding() * 3);
    }

    @Test
    void aDayIsPlannedForSomebody() {
        WorkOrder nobody = anOrder().withTechnician("");
        assertThatThrownBy(() -> DayPlan.planDay(nobody, aCampus(), RATES)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aTechnicianCanBeNamedOverTheOrder() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES, "phamm");
        assertThat(plan.technician()).isEqualTo("phamm");
    }

    @Test
    void anOrderWithNothingLeftPlansNoDay() {
        List<OrderLine> lines = List.of(
                new OrderLine("C-1", "MOO-214", LineState.INSTALLED),
                new OrderLine("C-2", "MOO-216", LineState.SKIPPED));
        WorkOrder done = anOrder().withLines(lines);
        assertThatThrownBy(() -> DayPlan.planDay(done, aCampus(), RATES)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSettledCylinderIsNotOnTheRoute() {
        List<OrderLine> lines =
                List.of(new OrderLine("C-1", "MOO-214", LineState.INSTALLED), new OrderLine("C-2", "MOO-216"));
        DayPlan plan = DayPlan.planDay(anOrder().withLines(lines), aCampus(), RATES);
        assertThat(plan.stops()).extracting(Stop::serial).containsExactly("C-2");
    }

    @Test
    void thePlanPrintsALineADoor() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES);
        List<String> lines = plan.lines();
        assertThat(lines.get(0)).startsWith("jkoivu: 2 doors");
        assertThat(lines).hasSize(3);
    }

    @Test
    void aShortDayFitsAndALongOneDoesNot() {
        DayPlan plan = DayPlan.planDay(aBigOrder(), aCampus(), RATES);
        assertThat(plan.fitsIn(plan.totalMinutes())).isTrue();
        assertThat(plan.fitsIn(plan.totalMinutes() - 1)).isFalse();
    }

    @Test
    void aNegativeDayIsRefused() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES);
        assertThatThrownBy(() -> plan.fitsIn(-1)).isInstanceOf(InputError.class);
    }

    @Test
    void aDayThatFitsIsNotSplit() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES);
        DaySplit split = plan.splitDay(plan.totalMinutes());
        assertThat(split.first()).isSameAs(plan);
        assertThat(split.second()).isEmpty();
    }

    @Test
    void aLongDaySplitsOnABuildingBoundary() {
        DayPlan plan = DayPlan.planDay(aBigOrder(), aCampus(), RATES);
        DaySplit split = plan.splitDay(120);
        assertThat(split.second()).isPresent();
        Set<String> shared = new HashSet<>(split.first().buildings());
        shared.retainAll(split.second().get().buildings());
        assertThat(shared).isEmpty();
    }

    @Test
    void aSplitKeepsEveryDoor() {
        DayPlan plan = DayPlan.planDay(aBigOrder(), aCampus(), RATES);
        DaySplit split = plan.splitDay(120);
        assertThat(split.first().stops().size() + split.second().get().stops().size())
                .isEqualTo(plan.stops().size());
    }

    @Test
    void aSplitThatCannotFitOneBuildingTakesItAnyway() {
        DayPlan plan = DayPlan.planDay(aBigOrder(), aCampus(), RATES);
        DaySplit split = plan.splitDay(1);
        assertThat(split.first().stops()).isNotEmpty();
        assertThat(split.second()).isPresent();
    }

    @Test
    void aDayOfNothingCannotBeSplit() {
        DayPlan plan = DayPlan.planDay(anOrder(), aCampus(), RATES);
        assertThatThrownBy(() -> plan.splitDay(0)).isInstanceOf(InputError.class);
    }
}
