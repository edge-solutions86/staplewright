package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class AccessTest {

    private static AccessMatrix aMatrix() {
        AccessMatrix matrix = new AccessMatrix(aCampus());
        matrix.addGroup(new AccessGroup("geography faculty", "", "geography"));
        matrix.addGroup(new AccessGroup("geography office", "", "geography"));
        matrix.addGroup(new AccessGroup("night custodial", "", "facilities"));
        matrix.addGroup(new AccessGroup("lock shop", "", "facilities"));
        matrix.requireAll("geography faculty", List.of("MOO-214", "MOO-216", "MOO-110"));
        matrix.requireAll("geography office", List.of("MOO-214", "MOO-216", "MOO-218"));
        matrix.requireBuilding("night custodial", "MOO");
        matrix.requireAll("lock shop", List.of("HEI-001", "HEI-G12", "MOO-101"));
        return matrix;
    }

    @Test
    void aGroupNeedsAName() {
        assertThatThrownBy(() -> new AccessGroup("  ")).isInstanceOf(InputError.class);
    }

    @Test
    void aGroupCannotBeAddedTwice() {
        AccessMatrix matrix = aMatrix();
        assertThatThrownBy(() -> matrix.addGroup(new AccessGroup("lock shop"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutAGroupNobodyCreated() {
        assertThatThrownBy(() -> aMatrix().group("provost")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aRequirementReadsBothWays() {
        AccessMatrix matrix = aMatrix();
        assertThat(matrix.openingsFor("geography faculty")).contains("MOO-214");
        assertThat(matrix.groupsFor("MOO-214")).contains("geography faculty");
        assertThat(matrix.wants("geography faculty", "MOO-214")).isTrue();
        assertThat(matrix.wants("geography faculty", "MOO-218")).isFalse();
    }

    @Test
    void askingForADoorThatTakesNoKeyIsRefused() {
        AccessMatrix matrix = aMatrix();
        assertThatThrownBy(() -> matrix.require("geography faculty", "MOO-260")).isInstanceOf(ConflictError.class);
    }

    @Test
    void askingForADoorNobodyHasHeardOf() {
        AccessMatrix matrix = aMatrix();
        assertThatThrownBy(() -> matrix.require("geography faculty", "ZZZ-1")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void requiringAWholeBuildingSkipsTheUnkeyedDoors() {
        List<String> wanted = aMatrix().openingsFor("night custodial");
        assertThat(wanted).doesNotContain("MOO-260");
        assertThat(wanted).hasSize(5);
    }

    @Test
    void theSameRequirementTwiceIsOneRequirement() {
        AccessMatrix matrix = aMatrix();
        int before = matrix.openingsFor("geography faculty").size();
        matrix.require("geography faculty", "MOO-214");
        assertThat(matrix.openingsFor("geography faculty")).hasSize(before);
    }

    @Test
    void droppingARequirementTakesItOff() {
        AccessMatrix matrix = aMatrix();
        matrix.drop("geography faculty", "MOO-110");
        assertThat(matrix.openingsFor("geography faculty")).doesNotContain("MOO-110");
    }

    @Test
    void droppingSomethingThatWasNeverAskedForIsQuiet() {
        AccessMatrix matrix = aMatrix();
        matrix.drop("geography faculty", "MOO-218");
        assertThat(matrix.openingsFor("geography faculty")).doesNotContain("MOO-218");
    }

    @Test
    void theSignatureOfADoorIsTheGroupsThatWantIt() {
        assertThat(aMatrix().signature("MOO-214"))
                .containsExactly("geography faculty", "geography office", "night custodial");
    }

    @Test
    void doorsWantedByTheSameGroupsCanBeKeyedAlike() {
        assertThat(aMatrix().keyedAlikeSets()).contains(List.of("MOO-214", "MOO-216"));
    }

    @Test
    void aDoorWantedByADifferentSetIsOnItsOwn() {
        List<List<String>> sets = aMatrix().keyedAlikeSets();
        assertThat(sets).contains(List.of("MOO-218"));
        assertThat(sets).contains(List.of("MOO-110"));
    }

    @Test
    void theBiggestKeyedAlikeSetComesFirst() {
        List<List<String>> sets = aMatrix().keyedAlikeSets();
        assertThat(sets.get(0).size())
                .isGreaterThanOrEqualTo(sets.get(sets.size() - 1).size());
    }

    @Test
    void aDoorNobodyAskedForIsInNoKeyedAlikeSet() {
        AccessMatrix matrix = aMatrix();
        Set<String> flat =
                matrix.keyedAlikeSets().stream().flatMap(List::stream).collect(Collectors.toSet());
        assertThat(flat).doesNotContain("VOS-311");
        assertThat(matrix.unreachableOpenings()).contains("VOS-311");
    }

    @Test
    void aDoorSomebodyAskedForIsReachable() {
        assertThat(aMatrix().unreachableOpenings()).doesNotContain("MOO-214");
    }

    @Test
    void aGroupInsideOneBuildingReachesOneBuilding() {
        assertThat(aMatrix().buildingsReached("geography faculty")).containsExactly("MOO");
    }

    @Test
    void aGroupAcrossTwoBuildingsIsFlagged() {
        AccessMatrix matrix = aMatrix();
        assertThat(matrix.buildingsReached("lock shop")).containsExactly("HEI", "MOO");
        assertThat(matrix.crossBuildingGroups()).containsExactly("lock shop");
    }

    @Test
    void aGroupWithNoDoorsReachesNothing() {
        AccessMatrix matrix = aMatrix();
        matrix.addGroup(new AccessGroup("new hires"));
        assertThat(matrix.buildingsReached("new hires")).isEmpty();
        assertThat(matrix.coverage()).containsEntry("new hires", 0);
    }

    @Test
    void coverageCountsDoorsPerGroupWorstFirst() {
        Map<String, Integer> coverage = aMatrix().coverage();
        List<Integer> counts = new ArrayList<>(coverage.values());
        assertThat(counts)
                .isEqualTo(counts.stream().sorted(Comparator.reverseOrder()).toList());
        assertThat(coverage).containsEntry("night custodial", 5);
    }

    @Test
    void theMatrixIsAContainerOfItsGroups() {
        AccessMatrix matrix = aMatrix();
        assertThat(matrix.contains("lock shop")).isTrue();
        assertThat(matrix.contains("provost")).isFalse();
        assertThat(matrix.size()).isEqualTo(4);
        List<String> walked = StreamSupport.stream(matrix.spliterator(), false)
                .map(AccessGroup::name)
                .toList();
        assertThat(walked)
                .isEqualTo(
                        matrix.groups().stream().map(AccessGroup::name).sorted().toList());
    }
}
