package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.campus.ZoneMap.spansZones;
import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ZonesTest {

    private static ZoneMap aMap() {
        ZoneMap zones = new ZoneMap(aCampus());
        zones.add(new Zone("ACAD", "academic core", "geography crew"));
        zones.add(new Zone("SVC", "service yards", "night facilities"));
        zones.place("MOO", "ACAD");
        zones.place("VOS", "ACAD");
        zones.place("HEI", "SVC");
        return zones;
    }

    /** A map with one zone and nothing placed in it. */
    private static ZoneMap anAcademicCore() {
        ZoneMap zones = new ZoneMap(aCampus());
        zones.add(new Zone("ACAD", "academic core"));
        return zones;
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "acad", " ACAD"})
    void aZoneCodeIsUpperCaseAndTrimmed(String code) {
        assertThatThrownBy(() -> new Zone(code, "academic core")).isInstanceOf(InputError.class);
    }

    @Test
    void aZoneNeedsAName() {
        assertThatThrownBy(() -> new Zone("ACAD", "  ")).isInstanceOf(InputError.class);
    }

    @Test
    void aBuildingLandsInAZone() {
        ZoneMap zones = aMap();
        assertThat(zones.zoneOf("MOO").map(Zone::code)).contains("ACAD");
        assertThat(zones.buildingsIn("ACAD")).containsExactly("MOO", "VOS");
    }

    @Test
    void aBuildingNobodyPlacedIsInNoZone() {
        assertThat(anAcademicCore().zoneOf("MOO")).isEmpty();
    }

    @Test
    void aBuildingIsInOneZone() {
        ZoneMap zones = aMap();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> zones.place("MOO", "SVC"));
        assertThat(caught.facts()).containsEntry("zone", "ACAD");
    }

    @Test
    void placingABuildingWhereItAlreadyIsIsQuiet() {
        assertThat(aMap().place("MOO", "ACAD")).isEqualTo("ACAD");
    }

    @Test
    void aBuildingCanBeMovedDeliberately() {
        ZoneMap zones = aMap();
        zones.move("MOO", "SVC");
        assertThat(zones.zoneOf("MOO").map(Zone::code)).contains("SVC");
        assertThat(zones.buildingsIn("ACAD")).containsExactly("VOS");
    }

    @Test
    void aBuildingThatIsNotPlacedCannotBeMoved() {
        ZoneMap zones = anAcademicCore();
        assertThatThrownBy(() -> zones.move("MOO", "ACAD")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aBuildingNobodyHasHeardOf() {
        ZoneMap zones = aMap();
        assertThatThrownBy(() -> zones.place("ZZZ", "ACAD")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aZoneNobodyHasHeardOf() {
        ZoneMap zones = aMap();
        assertThatThrownBy(() -> zones.place("MOO", "NOWHERE")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aZoneCannotBeAddedTwice() {
        ZoneMap zones = aMap();
        assertThatThrownBy(() -> zones.add(new Zone("ACAD", "another academic core")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void theDoorsInAZoneAreTheKeyedOnes() {
        List<String> found = aMap().openingsIn("ACAD");
        assertThat(found).doesNotContain("MOO-260");
        assertThat(found).contains("MOO-214");
        assertThat(found).contains("VOS-311");
    }

    @Test
    void aZoneWithNoBuildingsHasNoDoors() {
        ZoneMap zones = aMap();
        zones.add(new Zone("PT", "the point"));
        assertThat(zones.openingsIn("PT")).isEmpty();
        assertThat(zones.emptyZones()).containsExactly("PT");
    }

    @Test
    void theUnplacedBuildingsAreReported() {
        ZoneMap zones = anAcademicCore();
        zones.place("MOO", "ACAD");
        assertThat(zones.unplaced()).containsExactly("HEI", "VOS");
    }

    @Test
    void aFinishedMapLeavesNothingUnplaced() {
        assertThat(aMap().unplaced()).isEmpty();
    }

    @Test
    void theCountsPutTheBiggestZoneFirst() {
        Map<String, Integer> counts = aMap().counts();
        assertThat(counts.keySet()).containsExactly("ACAD", "SVC");
        assertThat(counts.get("ACAD")).isGreaterThan(counts.get("SVC"));
    }

    @Test
    void theRotaComesOffTheZone() {
        assertThat(aMap().rotaFor("MOO")).isEqualTo("geography crew");
        assertThat(aMap().rotaFor("HEI")).isEqualTo("night facilities");
    }

    @Test
    void aBuildingInNoZoneHasNoRota() {
        ZoneMap zones = anAcademicCore();
        assertThatThrownBy(() -> zones.rotaFor("MOO")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSetOfBuildingsInsideOneZoneDoesNotSpan() {
        assertThat(spansZones(aMap(), List.of("MOO", "VOS"))).isFalse();
    }

    @Test
    void aSetReachingTwoZonesSpans() {
        assertThat(spansZones(aMap(), List.of("MOO", "HEI"))).isTrue();
    }

    @Test
    void anUnplacedBuildingDoesNotMakeASpanOnItsOwn() {
        ZoneMap zones = anAcademicCore();
        zones.place("MOO", "ACAD");
        assertThat(spansZones(zones, List.of("MOO", "HEI"))).isFalse();
    }

    @Test
    void theMapIsAContainerInCodeOrder() {
        ZoneMap zones = aMap();
        assertThat(zones.contains("ACAD")).isTrue();
        assertThat(zones.contains("PT")).isFalse();
        assertThat(StreamSupport.stream(zones.spliterator(), false).map(Zone::code))
                .containsExactly("ACAD", "SVC");
        assertThat(zones.size()).isEqualTo(2);
    }
}
