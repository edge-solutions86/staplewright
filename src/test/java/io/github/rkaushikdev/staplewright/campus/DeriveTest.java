package io.github.rkaushikdev.staplewright.campus;

import static io.github.rkaushikdev.staplewright.campus.Derivation.derive;
import static io.github.rkaushikdev.staplewright.campus.Derivation.overGrants;
import static io.github.rkaushikdev.staplewright.campus.Derivation.unsatisfied;
import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeriveTest {

    private static AccessMatrix aMatrix() {
        AccessMatrix matrix = new AccessMatrix(aCampus());
        matrix.addGroup(new AccessGroup("geography faculty"));
        matrix.addGroup(new AccessGroup("geography office"));
        matrix.addGroup(new AccessGroup("night custodial"));
        matrix.requireAll("geography faculty", List.of("MOO-214", "MOO-216", "MOO-110"));
        matrix.requireAll("geography office", List.of("MOO-214", "MOO-216", "MOO-218"));
        matrix.requireBuilding("night custodial", "MOO");
        return matrix;
    }

    private static AccessMatrix aTwoBuildingMatrix() {
        AccessMatrix matrix = aMatrix();
        matrix.addGroup(new AccessGroup("shop"));
        matrix.requireAll("shop", List.of("HEI-001", "HEI-G12", "MOO-101"));
        matrix.addGroup(new AccessGroup("voss faculty"));
        matrix.requireAll("voss faculty", List.of("VOS-311", "VOS-312"));
        return matrix;
    }

    /** The same campus after somebody took two doors off a group. */
    private static AccessMatrix aShrunkenMatrix() {
        AccessMatrix matrix = aMatrix();
        matrix.drop("night custodial", "MOO-110");
        matrix.drop("night custodial", "MOO-216");
        return matrix;
    }

    private static List<String> rendered(List<Grant> grants) {
        return grants.stream().map(grant -> grant.symbol().render()).toList();
    }

    @Test
    void eachBuildingGetsAGrandMaster() {
        Proposal proposal = derive(aTwoBuildingMatrix());
        List<Grant> grand = proposal.grants().stream()
                .filter(grant -> grant.symbol().level() == 1)
                .toList();
        assertThat(rendered(grand)).containsExactly("A", "B", "C");
        assertThat(grand).allSatisfy(grant -> assertThat(grant.reason()).contains("grand master"));
    }

    @Test
    void oneBuildingAloneTakesTheFirstLetter() {
        assertThat(rendered(derive(aMatrix()).masters())).containsExactly("A", "AA", "AB", "AC");
    }

    @Test
    void aGroupCoveringTwoAlikeSetsBecomesAMaster() {
        Map<String, String> reasons = derive(aMatrix()).masters().stream()
                .collect(Collectors.toMap(grant -> grant.symbol().render(), Grant::reason));
        assertThat(reasons.values()).contains("master for night custodial");
    }

    @Test
    void aMasterOpensEverythingTheGroupAskedFor() {
        AccessMatrix matrix = aMatrix();
        Proposal proposal = derive(matrix);
        for (Grant grant : proposal.masters()) {
            if (grant.reason().equals("master for night custodial")) {
                assertThat(grant.openings()).containsAll(matrix.openingsFor("night custodial"));
            }
        }
    }

    @Test
    void everyAlikeSetBecomesOneChangeKey() {
        AccessMatrix matrix = aMatrix();
        Proposal proposal = derive(matrix);
        Set<List<String>> sets = proposal.changeKeys().stream()
                .map(grant -> grant.openings().stream().sorted().toList())
                .collect(Collectors.toSet());
        assertThat(sets).isEqualTo(new HashSet<>(matrix.keyedAlikeSets()));
    }

    @Test
    void aChangeKeyHangsUnderAMasterThatCoversIt() {
        Proposal proposal = derive(aMatrix());
        for (Grant grant : proposal.changeKeys()) {
            Optional<KeySymbol> parent = grant.symbol().parent();
            assertThat(parent).isPresent();
            assertThat(proposal.opens(parent.get())).containsAll(grant.openings());
        }
    }

    @Test
    void theGrandMasterOpensItsWholeBuilding() {
        AccessMatrix matrix = aMatrix();
        Proposal proposal = derive(matrix);
        Set<String> keyed = matrix.openings().inBuilding("MOO").stream()
                .filter(Opening::isKeyed)
                .map(Opening::number)
                .collect(Collectors.toSet());
        Set<String> reachable = new HashSet<>(proposal.opens(KeySymbol.parse("A")));
        assertThat(keyed).containsAll(reachable);
        assertThat(reachable).contains("MOO-214");
    }

    @Test
    void aDoorNobodyAskedForStaysOutOfTheDraft() {
        AccessMatrix matrix = aMatrix();
        Proposal proposal = derive(matrix);
        Set<String> everything = proposal.grants().stream()
                .flatMap(grant -> grant.openings().stream())
                .collect(Collectors.toSet());
        assertThat(everything).doesNotContain("VOS-311");
        assertThat(proposal.notes()).anyMatch(note -> note.contains("VOS-311"));
    }

    @Test
    void aCrossBuildingGroupIsCalledOutRatherThanKeyed() {
        Proposal proposal = derive(aTwoBuildingMatrix());
        assertThat(proposal.notes()).anyMatch(note -> note.contains("shop") && note.contains("top key"));
    }

    @Test
    void theProposalIsOrderedShallowFirst() {
        Proposal proposal = derive(aTwoBuildingMatrix());
        List<Integer> levels =
                proposal.grants().stream().map(grant -> grant.symbol().level()).toList();
        assertThat(levels).isEqualTo(levels.stream().sorted().toList());
    }

    @Test
    void askingTheProposalAboutASymbolItDoesNotHold() {
        Proposal proposal = derive(aMatrix());
        assertThat(proposal.opens(KeySymbol.parse("ZZ"))).isEmpty();
        assertThat(proposal.grantFor(KeySymbol.parse("ZZ"))).isEmpty();
    }

    @Test
    void aDraftAgainstTheMatrixItCameFromHandsOutNothingSpare() {
        AccessMatrix matrix = aMatrix();
        assertThat(overGrants(matrix, derive(matrix))).isEmpty();
    }

    @Test
    void aStaleDraftGivesAGroupMoreThanItNowAsksFor() {
        Proposal proposal = derive(aMatrix());
        Set<String> reported = overGrants(aShrunkenMatrix(), proposal).stream()
                .map(OverGrant::group)
                .collect(Collectors.toSet());
        assertThat(reported).contains("night custodial");
    }

    @Test
    void anOverGrantNamesTheExtraDoors() {
        AccessMatrix matrix = aShrunkenMatrix();
        List<OverGrant> found = overGrants(matrix, derive(aMatrix()));
        assertThat(found).isNotEmpty();
        for (OverGrant item : found) {
            Set<String> wanted = new HashSet<>(matrix.openingsFor(item.group()));
            assertThat(item.extra()).isNotEmpty();
            assertThat(item.extra()).doesNotContainAnyElementsOf(wanted);
        }
    }

    @Test
    void theWorstOverGrantComesFirst() {
        List<OverGrant> found = overGrants(aShrunkenMatrix(), derive(aMatrix()));
        List<Integer> sizes = found.stream().map(item -> item.extra().size()).toList();
        assertThat(sizes)
                .isEqualTo(sizes.stream().sorted(Comparator.reverseOrder()).toList());
    }

    @Test
    void aCrossBuildingGroupIsUnsatisfiedByASingleKey() {
        AccessMatrix matrix = aTwoBuildingMatrix();
        assertThat(unsatisfied(matrix, derive(matrix))).contains("shop");
    }

    @Test
    void aGroupTheDraftCoversIsNotUnsatisfied() {
        AccessMatrix matrix = aMatrix();
        assertThat(unsatisfied(matrix, derive(matrix))).doesNotContain("night custodial");
    }

    @Test
    void aGroupWithNoDoorsIsNeitherSatisfiedNorReported() {
        AccessMatrix matrix = aMatrix();
        matrix.addGroup(new AccessGroup("new hires"));
        Proposal proposal = derive(matrix);
        assertThat(unsatisfied(matrix, proposal)).doesNotContain("new hires");
        assertThat(overGrants(matrix, proposal).stream().map(OverGrant::group)).doesNotContain("new hires");
    }

    @Test
    void anEmptyMatrixDerivesAnEmptyProposal() {
        AccessMatrix matrix = new AccessMatrix(aCampus());
        Proposal proposal = derive(matrix);
        assertThat(proposal.grants()).isEmpty();
        assertThat(proposal.notes()).hasSize(matrix.openings().keyedOpenings().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"geography faculty", "night custodial"})
    void everyNamedGroupReachesOneBuildingHere(String group) {
        assertThat(aMatrix().buildingsReached(group)).hasSize(1);
    }
}
