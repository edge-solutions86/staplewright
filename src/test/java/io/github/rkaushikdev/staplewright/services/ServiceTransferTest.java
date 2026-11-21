package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.Transfer;
import io.github.rkaushikdev.staplewright.control.TransferPolicy;
import io.github.rkaushikdev.staplewright.control.TransferState;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.Stray;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceTransferTest {

    private static final LocalDate ISSUED = LocalDate.of(2025, 1, 6);
    private static final LocalDate CLAIMED = LocalDate.of(2025, 3, 4);
    private static final LocalDate SOON = LocalDate.of(2025, 3, 10);
    private static final LocalDate LATE = LocalDate.of(2025, 5, 1);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        for (String name : List.of("kmaki", "rlaakso", "jkoivu")) {
            session.add(person(name, name, "staff", "active"));
        }
        KeyholderRow left = person("ovirta", "o virta", "staff", "separated");
        left.setSeparatedOn(LocalDate.of(2025, 2, 1));
        session.add(left);
        KeyholderRow lapsed = person("pnurmi", "p nurmi", "student", "active");
        lapsed.setEndsOn(LocalDate.of(2025, 2, 1));
        session.add(lapsed);
        session.flush();
        session.addAll(List.of(key("41207", "AA1", "out"), key("41211", "A", "out"), key("41299", "AA2", "returned")));
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static KeyholderRow person(String identifier, String name, String affiliation, String standing) {
        KeyholderRow row = new KeyholderRow(identifier, name, affiliation, "grounds");
        row.setStanding(standing);
        return row;
    }

    private static IssuedKeyRow key(String tag, String symbol, String state) {
        IssuedKeyRow row = new IssuedKeyRow(tag, symbol, "kmaki", ISSUED);
        row.setDueOn(LocalDate.of(2026, 1, 6));
        row.setState(state);
        return row;
    }

    private TransferService aService() {
        return aService(new TransferPolicy());
    }

    private TransferService aService(TransferPolicy policy) {
        TransferService service = new TransferService(session, policy);
        service.openSeries();
        return service;
    }

    private TransferService claimed() {
        TransferService service = aService();
        service.claim("41207", "rlaakso", CLAIMED);
        return service;
    }

    private static List<String> numbers(List<TransferRow> rows) {
        return rows.stream().map(TransferRow::getNumber).toList();
    }

    @Test
    void aClaimGetsANumber() {
        TransferService service = aService();
        TransferRow row = service.claim("41207", "rlaakso", CLAIMED);
        assertThat(row.getNumber()).isEqualTo("T-00001");
        assertThat(row.getState()).isEqualTo("claimed");
    }

    @Test
    void aClaimTakesTheSymbolOffTheKey() {
        TransferService service = aService();
        assertThat(service.claim("41207", "rlaakso", CLAIMED).getSymbol()).isEqualTo("AA1");
    }

    @Test
    void aClaimNamesWhoeverTheLedgerSaysHadIt() {
        TransferService service = aService();
        assertThat(service.claim("41207", "rlaakso", CLAIMED).getPassedBy()).isEqualTo("kmaki");
    }

    @Test
    void aKeyNobodyIssuedIsA404() {
        TransferService service = aService();
        assertThatThrownBy(() -> service.claim("99999", "rlaakso", CLAIMED)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aKeyThatCameBackCannotBeHandedOn() {
        TransferService service = aService();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.claim("41299", "rlaakso", CLAIMED));
        assertThat(caught.facts().get("state")).isEqualTo("returned");
    }

    @Test
    void aMasterDoesNotMoveBetweenPeople() {
        TransferService service = aService();
        assertThatThrownBy(() -> service.claim("41211", "rlaakso", CLAIMED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aShopThatAllowsItCanHandOnAMaster() {
        TransferService service = aService(new TransferPolicy().withAllowMasterTransfer(true));
        assertThat(service.claim("41211", "rlaakso", CLAIMED).getSymbol()).isEqualTo("A");
    }

    @Test
    void aHandoverToSomebodyNobodyHasIsA404() {
        TransferService service = aService();
        assertThatThrownBy(() -> service.claim("41207", "nobody", CLAIMED)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aHandoverToSomebodyWhoLeftIsRefused() {
        TransferService service = aService();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.claim("41207", "ovirta", CLAIMED));
        assertThat(caught.facts().get("standing")).isEqualTo("separated");
    }

    @Test
    void aHandoverToALapsedAppointmentIsRefused() {
        TransferService service = aService();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.claim("41207", "pnurmi", CLAIMED));
        assertThat(caught.facts().get("ended")).isEqualTo("2025-02-01");
    }

    @Test
    void oneKeyCannotBeClaimedTwice() {
        TransferService service = claimed();
        assertThatThrownBy(() -> service.claim("41207", "jkoivu", CLAIMED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aClaimDoesNotMoveTheKey() {
        TransferService service = claimed();
        assertThat(service.holderOf("41207", LATE)).isEqualTo("kmaki");
    }

    @Test
    void confirmingMovesTheKey() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON, "picked it up");
        assertThat(service.holderOf("41207", LATE)).isEqualTo("rlaakso");
    }

    @Test
    void theKeyRowIsNeverTouched() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON);
        assertThat(session.get(IssuedKeyRow.class, "41207").orElseThrow().getKeyholder())
                .isEqualTo("kmaki");
    }

    @Test
    void refusingLeavesTheKeyWhereItWas() {
        TransferService service = claimed();
        service.refuse("T-00001", SOON, "never got it");
        assertThat(service.holderOf("41207", LATE)).isEqualTo("kmaki");
    }

    @Test
    void cancellingLeavesTheKeyWhereItWas() {
        TransferService service = claimed();
        service.cancel("T-00001", SOON);
        assertThat(service.holderOf("41207", LATE)).isEqualTo("kmaki");
    }

    @Test
    void aHandoverNobodyClaimedIsA404() {
        TransferService service = aService();
        assertThatThrownBy(() -> service.confirm("T-99999", SOON)).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aClaimInsideItsWindowCannotLapse() {
        TransferService service = claimed();
        assertThatThrownBy(() -> service.lapse("T-00001", SOON)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aClaimPastItsWindowLapses() {
        TransferService service = claimed();
        assertThat(service.lapse("T-00001", LATE).getState()).isEqualTo("lapsed");
    }

    @Test
    void theSweepClosesEveryLateClaim() {
        TransferService service = claimed();
        List<TransferRow> found = service.lapseOverdue(LATE);
        assertThat(numbers(found)).containsExactly("T-00001");
        assertThat(service.row("T-00001").getState()).isEqualTo("lapsed");
    }

    @Test
    void theSweepLeavesTheRecentClaimsAlone() {
        TransferService service = claimed();
        assertThat(service.lapseOverdue(SOON)).isEmpty();
    }

    @Test
    void confirmingToSomebodyWhoHasSinceLeftIsRefused() {
        TransferService service = claimed();
        KeyholderRow person = session.get(KeyholderRow.class, "rlaakso").orElseThrow();
        person.setStanding("separated");
        person.setSeparatedOn(LocalDate.of(2025, 3, 5));
        session.flush();
        assertThatThrownBy(() -> service.confirm("T-00001", SOON)).isInstanceOf(ConflictError.class);
    }

    @Test
    void whatAPersonActuallyHolds() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON);
        assertThat(service.holdings("rlaakso", LATE)).containsExactly("41207");
        assertThat(service.holdings("kmaki", LATE)).containsExactly("41211");
    }

    @Test
    void theStraysNameTheLedgerAndTheTruth() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON);
        assertThat(service.strays(LATE)).containsExactly(new Stray("41207", "kmaki", "rlaakso"));
    }

    @Test
    void aShopWithNoHandoversHasNoStrays() {
        assertThat(aService().strays(LATE)).isEmpty();
    }

    @Test
    void theExposureCountsWhoIsSittingOnKeys() {
        TransferService service = claimed();
        assertThat(service.exposure(LATE)).isEqualTo(Map.of("rlaakso", 1));
    }

    @Test
    void nothingLateIsNoExposure() {
        assertThat(claimed().exposure(SOON)).isEmpty();
    }

    @Test
    void theLateClaimsAreNamed() {
        TransferService service = claimed();
        assertThat(service.late(LATE).stream().map(Transfer::number)).containsExactly("T-00001");
    }

    @Test
    void theChainFollowsAKeyThroughHands() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON);
        service.claim("41207", "jkoivu", SOON);
        service.confirm("T-00002", LATE);
        assertThat(service.chainFor("41207").stream().map(Transfer::passedTo)).containsExactly("rlaakso", "jkoivu");
    }

    @Test
    void whatAPersonStillOwesASignatureOn() {
        TransferService service = claimed();
        assertThat(numbers(service.waitingOn("rlaakso"))).containsExactly("T-00001");
    }

    @Test
    void theTransfersInAStateComeBack() {
        TransferService service = claimed();
        List<TransferRow> found = service.inState(TransferState.CLAIMED);
        assertThat(numbers(found)).containsExactly("T-00001");
    }

    @Test
    void theLedgerViewIgnoresTheHandovers() {
        TransferService service = claimed();
        service.confirm("T-00001", SOON);
        assertThat(TransferService.keysOutTo(session, "kmaki")).containsExactly("41207", "41211");
    }

    @Test
    void aPersonWithNothingOutHasNothing() {
        assertThat(TransferService.keysOutTo(session, "jkoivu")).isEmpty();
    }
}
