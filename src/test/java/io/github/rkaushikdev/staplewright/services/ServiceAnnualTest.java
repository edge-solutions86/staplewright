package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.AnnualReturnRow;
import io.github.rkaushikdev.staplewright.reports.AnnualReturn;
import io.github.rkaushikdev.staplewright.reports.Tally;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceAnnualTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);

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

    private AnnualService aService() {
        return new AnnualService(session);
    }

    private AnnualReturnRow filed(int year) {
        return filed(year, 4, 2, 0, 3);
    }

    private AnnualReturnRow filed(int year, int keysOut, int mastersOut, int unaccounted, int keyholders) {
        AnnualReturnRow row =
                new AnnualReturnRow(year, LocalDate.of(year, 6, 30), keysOut, mastersOut, unaccounted, keyholders);
        session.add(row);
        session.flush();
        return row;
    }

    @Test
    void aRowAndAReturnConvertBothWays() {
        AnnualReturn found = new AnnualReturn(2025, 5, 3, 0, 4);
        assertThat(AnnualService.toReturn(AnnualService.toRow(found, TODAY))).isEqualTo(found);
    }

    @Test
    void aRowCarriesWhoFiledIt() {
        AnnualReturnRow row =
                AnnualService.toRow(new AnnualReturn(2025, 5, 3, 0, 4), TODAY, "rvirta", "after the audit");
        assertThat(row.getFiledBy()).isEqualTo("rvirta");
        assertThat(row.getNote()).isEqualTo("after the audit");
    }

    @Test
    void aRowAveragesItsOwnKeys() {
        assertThat(AnnualService.toRow(new AnnualReturn(2025, 6, 3, 0, 3), TODAY)
                        .keysPerHolder())
                .isEqualTo(2.0);
    }

    @Test
    void aRowWithNobodyHoldingAnythingAveragesNothing() {
        assertThat(AnnualService.toRow(new AnnualReturn(2025, 0, 0, 0, 0), TODAY)
                        .keysPerHolder())
                .isEqualTo(0.0);
    }

    @Test
    void measuringDoesNotWriteAnything() {
        AnnualService service = aService();
        service.measure(TODAY);
        assertThat(service.returns().all()).isEmpty();
    }

    @Test
    void theMeasurementCountsWhatIsOut() {
        AnnualReturn found = aService().measure(TODAY);
        assertThat(found.keysOut()).isEqualTo(5);
        assertThat(found.mastersOut()).isEqualTo(3);
    }

    @Test
    void filingWritesTheNumbersDown() {
        AnnualService service = aService();
        AnnualReturnRow row = service.fileReturn(TODAY, "rvirta");
        assertThat(row.getYear()).isEqualTo(2025);
        assertThat(row.getKeysOut()).isEqualTo(5);
        assertThat(row.getFiledBy()).isEqualTo("rvirta");
    }

    @Test
    void aYearCannotBeFiledTwice() {
        AnnualService service = aService();
        service.fileReturn(TODAY);
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> service.fileReturn(TODAY));
        assertThat(caught.facts().get("year")).isEqualTo(2025);
    }

    @Test
    void aYearNobodyFiledIsA404() {
        AnnualService service = aService();
        assertThatThrownBy(() -> service.filed(2019)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aFiledYearComesBack() {
        AnnualService service = aService();
        service.fileReturn(TODAY);
        assertThat(service.filed(2025).getKeysOut()).isEqualTo(5);
    }

    @Test
    void withdrawingNeedsAReason() {
        AnnualService service = aService();
        service.fileReturn(TODAY);
        assertThatThrownBy(() -> service.withdraw(2025, "")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aWithdrawnReturnKeepsItsRow() {
        AnnualService service = aService();
        service.fileReturn(TODAY);
        AnnualReturnRow row = service.withdraw(2025, "counted the shops twice");
        assertThat(row.getNote()).startsWith("withdrawn:");
        assertThat(service.filed(2025)).isSameAs(row);
    }

    @Test
    void theFirstYearHasNothingBeforeIt() {
        assertThat(aService().previous(2025)).isEmpty();
    }

    @Test
    void thePreviousYearIsTheOneBefore() {
        filed(2023);
        filed(2024);
        AnnualReturn found = aService().previous(2025).orElseThrow();
        assertThat(found.year()).isEqualTo(2024);
    }

    @Test
    void aLaterYearIsNotThePreviousOne() {
        filed(2026);
        assertThat(aService().previous(2025)).isEmpty();
    }

    @Test
    void theHistoryComesBackOldestFirst() {
        filed(2024);
        filed(2023);
        assertThat(aService().history().stream().map(AnnualReturn::year)).containsExactly(2023, 2024);
    }

    @Test
    void theSheetReadsTopToBottom() {
        List<String> lines = aService().sheet(TODAY);
        assertThat(lines.get(0)).isEqualTo("key control return for 2025");
        assertThat(lines).anyMatch(line -> line.startsWith("3 master keys are out"));
    }

    @Test
    void theSheetComparesAgainstAFiledYear() {
        filed(2024, 4, 2, 0, 3);
        List<String> lines = aService().sheet(TODAY);
        assertThat(lines).anyMatch(line -> line.equals("against 2024"));
    }

    @Test
    void theMastersTableComesOffTheService() {
        List<String> lines = aService().masters();
        assertThat(lines.get(0)).isEqualTo("3 master keys are out");
    }

    @Test
    void theUnaccountedTableComesOffTheService() {
        assertThat(aService().unaccounted(TODAY).get(0)).startsWith("every key out");
    }

    @Test
    void theConcentrationComesOffTheService() {
        assertThat(aService().concentration().get(0)).isEqualTo(new Tally("phamm", 2));
    }

    @Test
    void theTrendSaysWhenNothingIsFiled() {
        assertThat(aService().trend()).containsExactly("no returns have been filed");
    }

    @Test
    void theTrendHasARowPerFiledYear() {
        filed(2023);
        filed(2024);
        assertThat(aService().trend()).hasSize(3);
    }

    @Test
    void theMovementIsAvailableWithoutFiling() {
        filed(2024, 4, 2, 0, 3);
        List<String> lines = aService().againstLastYear(TODAY);
        assertThat(lines.get(1)).contains("up 1");
    }

    @Test
    void theRepositoryListsFromAYear() {
        filed(2022);
        filed(2024);
        List<AnnualReturnRow> found = new ReturnRepository(session).since(2023);
        assertThat(found.stream().map(AnnualReturnRow::getYear)).containsExactly(2024);
    }

    @Test
    void theLatestFiledYearComesBack() {
        filed(2022);
        filed(2024);
        assertThat(new ReturnRepository(session).latest().orElseThrow().getYear())
                .isEqualTo(2024);
    }

    @Test
    void noReturnsMeansNoLatest() {
        assertThat(new ReturnRepository(session).latest()).isEmpty();
    }

    @Test
    void aYearBeforeAnybodyWasAliveIsRefused() {
        assertThatThrownBy(() -> filed(1066)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void moreMastersThanKeysIsRefused() {
        assertThatThrownBy(() -> filed(2025, 2, 3, 0, 3)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void moreUnaccountedThanKeysIsRefused() {
        assertThatThrownBy(() -> filed(2025, 2, 2, 3, 3)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aNegativeCountIsRefused() {
        assertThatThrownBy(() -> filed(2025, 4, 2, 0, -1)).isInstanceOf(PersistenceException.class);
    }

    @Test
    void oneYearCannotBeFiledTwiceInTheTable() {
        filed(2025);
        assertThatThrownBy(() -> filed(2025)).isInstanceOf(PersistenceException.class);
    }
}
