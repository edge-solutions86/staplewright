package io.github.rkaushikdev.staplewright.control;

import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class PeopleTest {

    private static List<String> identifiers(Iterable<Keyholder> people) {
        return StreamSupport.stream(people.spliterator(), false)
                .map(Keyholder::identifier)
                .toList();
    }

    @Test
    void aKeyholderCarriesALabelTheCounterReads() {
        assertThat(aDirectory().person("kmaki").label()).isEqualTo("Kirsi Maki (kmaki)");
    }

    @Test
    void aKeyholderNeedsAnIdentifierAndAName() {
        assertThatThrownBy(() -> new Keyholder("  ", "Kirsi Maki", Affiliation.FACULTY, "geography"))
                .isInstanceOf(InputError.class);
        assertThatThrownBy(() -> new Keyholder("kmaki", " ", Affiliation.FACULTY, "geography"))
                .isInstanceOf(InputError.class);
    }

    @ParameterizedTest
    @EnumSource(
            value = Affiliation.class,
            names = {"CONTRACTOR", "STUDENT"})
    void aTemporaryKeyholderNeedsAnEndDate(Affiliation affiliation) {
        assertThatThrownBy(() -> new Keyholder("temp", "A Person", affiliation, "geography"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aPermanentKeyholderDoesNotNeedOne() {
        assertThat(new Keyholder("kmaki", "Kirsi Maki", Affiliation.FACULTY, "geography").endsOn())
                .isEmpty();
    }

    @Test
    void aSeparatedRecordNeedsTheDateItHappened() {
        assertThatThrownBy(() -> new Keyholder(
                        "kmaki",
                        "Kirsi Maki",
                        Affiliation.FACULTY,
                        "geography",
                        Standing.SEPARATED,
                        Optional.empty(),
                        Optional.empty()))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anActiveKeyholderMayHoldKeys() {
        assertThat(aDirectory().person("kmaki").mayHoldKeys(TODAY)).isTrue();
    }

    @Test
    void aContractThatRanOutStopsTheKeysOnItsOwn() {
        Keyholder person = aDirectory().person("jwexler");
        assertThat(person.standing()).isSameAs(Standing.ACTIVE);
        assertThat(person.lapsedOn(TODAY)).isTrue();
        assertThat(person.mayHoldKeys(TODAY)).isFalse();
    }

    @Test
    void aContractStillRunningIsFine() {
        Keyholder person = aDirectory().person("tkoski");
        assertThat(person.lapsedOn(TODAY)).isFalse();
        assertThat(person.mayHoldKeys(TODAY)).isTrue();
    }

    @Test
    void theDayTheContractEndsIsStillGood() {
        Keyholder person = aDirectory().person("tkoski");
        assertThat(person.mayHoldKeys(LocalDate.of(2025, 5, 16))).isTrue();
        assertThat(person.mayHoldKeys(LocalDate.of(2025, 5, 17))).isFalse();
    }

    @Test
    void separatingSomebodyStopsThemHoldingKeys() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        assertThat(directory.person("kmaki").standing()).isSameAs(Standing.SEPARATED);
        assertThat(directory.person("kmaki").mayHoldKeys(TODAY)).isFalse();
    }

    @Test
    void separatingTwiceIsRefused() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        assertThatThrownBy(() -> directory.separate("kmaki", TODAY)).isInstanceOf(ConflictError.class);
    }

    @Test
    void suspendingStopsTheKeysWithoutClosingTheRecord() {
        Directory directory = aDirectory();
        directory.suspend("kmaki");
        assertThat(directory.person("kmaki").standing()).isSameAs(Standing.SUSPENDED);
        assertThat(directory.person("kmaki").mayHoldKeys(TODAY)).isFalse();
    }

    @Test
    void aSeparatedKeyholderCannotBeSuspended() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        assertThatThrownBy(() -> directory.suspend("kmaki")).isInstanceOf(ConflictError.class);
    }

    @Test
    void reinstatingPutsSomebodyBack() {
        Directory directory = aDirectory();
        directory.separate("kmaki", TODAY);
        directory.reinstate("kmaki");
        Keyholder person = directory.person("kmaki");
        assertThat(person.standing()).isSameAs(Standing.ACTIVE);
        assertThat(person.separatedOn()).isEmpty();
    }

    @Test
    void extendingAContractMovesTheDateOut() {
        Directory directory = aDirectory();
        directory.extend("jwexler", LocalDate.of(2025, 6, 30));
        assertThat(directory.person("jwexler").mayHoldKeys(TODAY)).isTrue();
    }

    @Test
    void anExtensionCannotGoBackward() {
        Directory directory = aDirectory();
        assertThatThrownBy(() -> directory.extend("jwexler", LocalDate.of(2024, 12, 1)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aPermanentRecordHasNoDateToExtend() {
        Directory directory = aDirectory();
        assertThatThrownBy(() -> directory.extend("kmaki", LocalDate.of(2026, 1, 1)))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anIdentifierCannotBeUsedTwice() {
        Directory directory = aDirectory();
        assertThatThrownBy(() -> directory.add(new Keyholder("kmaki", "Someone Else", Affiliation.STAFF, "physics")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutSomebodyWhoIsNotThere() {
        assertThatThrownBy(() -> aDirectory().person("nobody")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aDepartmentListsItsOwnPeople() {
        assertThat(identifiers(aDirectory().inDepartment("geography")))
                .containsExactly("dsalo", "kmaki", "rvirta", "tkoski");
    }

    @Test
    void theActiveListLeavesOutLapsedRecords() {
        List<String> identifiers = identifiers(aDirectory().active(TODAY));
        assertThat(identifiers).doesNotContain("jwexler");
        assertThat(identifiers).contains("kmaki");
    }

    @Test
    void aLapsedRecordNobodyClosedIsReported() {
        assertThat(identifiers(aDirectory().lapsed(TODAY))).containsExactly("jwexler");
    }

    @Test
    void aSeparatedRecordIsNotAlsoReportedAsLapsed() {
        Directory directory = aDirectory();
        directory.separate("jwexler", TODAY);
        assertThat(directory.lapsed(TODAY)).isEmpty();
    }

    @Test
    void anAuthorizerSignsForADepartmentAndItsBuildings() {
        Directory directory = aDirectory();
        assertThat(directory.mayAuthorize("rvirta", "geography", "MOO", TODAY)).isTrue();
        assertThat(directory.mayAuthorize("rvirta", "geography", "VOS", TODAY)).isFalse();
        assertThat(directory.mayAuthorize("rvirta", "physics", "MOO", TODAY)).isFalse();
    }

    @Test
    void anAuthorizerWithNoBuildingsSignsForAllOfThem() {
        Directory directory = aDirectory();
        assertThat(directory.mayAuthorize("phamm", "facilities", "VOS", TODAY)).isTrue();
        assertThat(directory.mayAuthorize("phamm", "facilities", "HEI", TODAY)).isTrue();
    }

    @Test
    void somebodyWhoDoesNotSignCannotSign() {
        assertThat(aDirectory().mayAuthorize("kmaki", "geography", "MOO", TODAY))
                .isFalse();
    }

    @Test
    void aSeparatedAuthorizerStopsSigning() {
        Directory directory = aDirectory();
        directory.separate("rvirta", TODAY);
        assertThat(directory.mayAuthorize("rvirta", "geography", "MOO", TODAY)).isFalse();
    }

    @Test
    void anAuthorizerHasToBeInTheDirectoryFirst() {
        Directory directory = aDirectory();
        assertThatThrownBy(() -> directory.addAuthorizer(new Authorizer("ghost", "geography")))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theSameAuthorizerCannotBeAddedTwice() {
        Directory directory = aDirectory();
        assertThatThrownBy(() -> directory.addAuthorizer(new Authorizer("rvirta", "geography")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutAnAuthorizerWhoDoesNotSign() {
        assertThatThrownBy(() -> aDirectory().authorizer("kmaki")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theDirectoryIsAContainerInIdentifierOrder() {
        Directory directory = aDirectory();
        assertThat(directory.contains("kmaki")).isTrue();
        assertThat(directory.contains("nobody")).isFalse();
        assertThat(directory.size()).isEqualTo(6);
        List<String> identifiers = identifiers(directory);
        assertThat(identifiers).isEqualTo(identifiers.stream().sorted().toList());
    }

    @Test
    void anEmptyDirectoryHoldsNothing() {
        assertThat(new Directory().size()).isZero();
        assertThat(new Directory().authorizers()).isEmpty();
    }
}
