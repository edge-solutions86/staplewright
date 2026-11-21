package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceEscrowTest {

    private static final LocalDate SEALED = LocalDate.of(2025, 1, 6);
    private static final LocalDate WALKED = LocalDate.of(2025, 6, 30);
    private static final LocalDate LATE = LocalDate.of(2025, 12, 1);
    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");
    private static final KeySymbol MASTER = KeySymbol.parse("A");

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

    private EscrowService installed() {
        EscrowService service = new EscrowService(session);
        service.install("KB-01", "MOO", "MOO-101", CHANGE_KEY, "44012", SEALED);
        return service;
    }

    private static List<String> numbers(List<KeyBox> boxes) {
        return boxes.stream().map(KeyBox::number).toList();
    }

    @Test
    void aBoxGoesUpAndReadsBack() {
        assertThat(installed().box("KB-01").state()).isEqualTo(BoxState.SEALED);
    }

    @Test
    void aBoxGoingUpOpensItsFirstSeal() {
        assertThat(installed().sealNow("KB-01").orElseThrow().getSeal()).isEqualTo("44012");
    }

    @Test
    void aBoxOnABuildingNobodyHasIsRefused() {
        EscrowService service = new EscrowService(session);
        assertThatThrownBy(() -> service.install("KB-01", "ZZZ", "MOO-101", CHANGE_KEY, "44012", SEALED))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aBoxOnADoorNobodyHasIsRefused() {
        EscrowService service = new EscrowService(session);
        assertThatThrownBy(() -> service.install("KB-01", "MOO", "MOO-999", CHANGE_KEY, "44012", SEALED))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aDoorOnAnotherBuildingIsRefused() {
        EscrowService service = new EscrowService(session);
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> service.install("KB-01", "VOS", "MOO-101", CHANGE_KEY, "44012", SEALED));
        assertThat(caught.facts()).containsEntry("actually", "MOO");
    }

    @Test
    void aSecondBoxOnOneDoorIsRefused() {
        EscrowService service = installed();
        assertThatThrownBy(() -> service.install("KB-02", "MOO", "MOO-101", CHANGE_KEY, "44013", SEALED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aSymbolNobodyCutIsRefused() {
        EscrowService service = new EscrowService(session);
        ConflictError caught = catchThrowableOfType(
                ConflictError.class,
                () -> service.install("KB-01", "MOO", "MOO-101", KeySymbol.parse("ZZ9"), "44012", SEALED));
        assertThat(caught.facts()).containsEntry("symbol", "ZZ9");
    }

    @Test
    void aMasterIsStillTooWideForAWall() {
        EscrowService service = new EscrowService(session);
        assertThatThrownBy(() -> service.install("KB-01", "MOO", "MOO-101", MASTER, "44012", SEALED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aShopThatAllowsMastersCanPutOneUp() {
        EscrowService service = new EscrowService(session, new BoxPolicy().withLowestLevel(1));
        KeyBoxRow row = service.install("KB-01", "MOO", "MOO-101", MASTER, "44012", SEALED);
        assertThat(row.getSymbol().render()).isEqualTo("A");
    }

    @Test
    void aBoxNobodyInstalledIsA404() {
        assertThatThrownBy(() -> new EscrowService(session).box("KB-99")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void readingTheRightSealOnlyRecordsTheWalk() {
        EscrowService service = installed();
        KeyBoxRow row = service.check("KB-01", "44012", WALKED);
        assertThat(row.getState()).isEqualTo("sealed");
        assertThat(row.getCheckedOn()).contains(WALKED);
        assertThat(service.sealNow("KB-01").orElseThrow().isCurrent()).isTrue();
    }

    @Test
    void aSealThatDoesNotMatchOpensTheBox() {
        KeyBoxRow row = installed().check("KB-01", "44099", WALKED);
        assertThat(row.getState()).isEqualTo("opened");
        assertThat(row.getOpenedBy()).isEqualTo("nobody signed for it");
    }

    @Test
    void aMismatchedSealClosesTheSealRow() {
        EscrowService service = installed();
        service.check("KB-01", "44099", WALKED);
        assertThat(service.sealNow("KB-01")).isEmpty();
        assertThat(service.history("KB-01").get(0).getBrokenOn()).contains(WALKED);
    }

    @Test
    void reportingAnOpeningClosesTheSealToo() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED, "kitchen fire");
        SealRow held = service.history("KB-01").get(0);
        assertThat(held.getBrokenOn()).contains(WALKED);
        assertThat(held.getBrokenBy()).isEqualTo("fire service");
    }

    @Test
    void anOpenBoxCanBeFoundEmpty() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(service.empty("KB-01", LATE, "no key in it").getState()).isEqualTo("empty");
    }

    @Test
    void resealingOpensANewSealAndLeavesTheOldOneClosed() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        service.reseal("KB-01", "44120", CHANGE_KEY, LATE);
        List<SealRow> found = service.history("KB-01");
        assertThat(found.stream().map(SealRow::getSeal)).containsExactly("44012", "44120");
        assertThat(found.get(0).getBrokenOn()).contains(WALKED);
        assertThat(found.get(1).isCurrent()).isTrue();
    }

    @Test
    void resealingMovesTheBoxRowBackToSealed() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        KeyBoxRow row = service.reseal("KB-01", "44120", CHANGE_KEY, LATE);
        assertThat(row.getState()).isEqualTo("sealed");
        assertThat(row.getSeal()).isEqualTo("44120");
        assertThat(row.getOpenedOn()).isEmpty();
    }

    @Test
    void resealingOntoASymbolNobodyCutIsRefused() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThatThrownBy(() -> service.reseal("KB-01", "44120", KeySymbol.parse("ZZ9"), LATE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void takingABoxDownClosesItsSeal() {
        EscrowService service = installed();
        assertThat(service.withdraw("KB-01", LATE, "building came down").getState())
                .isEqualTo("withdrawn");
        assertThat(service.sealNow("KB-01")).isEmpty();
    }

    @Test
    void theKeyInTheBoxOnADayComesOffTheHistory() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        service.reseal("KB-01", "44120", KeySymbol.parse("AA2"), LATE);
        assertThat(service.keyOn("KB-01", LocalDate.of(2025, 3, 1))
                        .orElseThrow()
                        .render())
                .isEqualTo("AA1");
        assertThat(service.keyOn("KB-01", LocalDate.of(2025, 12, 2))
                        .orElseThrow()
                        .render())
                .isEqualTo("AA2");
    }

    @Test
    void aDayBeforeTheBoxWentUpHasNoKey() {
        assertThat(installed().keyOn("KB-01", LocalDate.of(2024, 1, 1))).isEmpty();
    }

    @Test
    void theOpenBoxesComeOffTheRegister() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(numbers(service.openBoxes())).containsExactly("KB-01");
    }

    @Test
    void aBoxIsDueACheckAfterTheWindow() {
        assertThat(numbers(installed().dueForCheck(LocalDate.of(2025, 7, 6)))).containsExactly("KB-01");
    }

    @Test
    void aBoxOpenTooLongIsOverdueAReseal() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(numbers(service.overdueReseal(LocalDate.of(2025, 7, 5)))).containsExactly("KB-01");
    }

    @Test
    void theWalkRoundLeadsWithTheOpenBoxes() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(service.walkRound(LATE).get(0).number()).isEqualTo("KB-01");
    }

    @Test
    void aBuildingWithNoBoxIsNamed() {
        assertThat(installed().uncovered()).contains("VOS");
    }

    @Test
    void aBuildingWithABoxIsNot() {
        assertThat(installed().uncovered()).doesNotContain("MOO");
    }

    @Test
    void theExposureCountsOpenBoxesPerBuilding() {
        EscrowService service = installed();
        service.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(service.exposure()).isEqualTo(Map.of("MOO", 1));
    }

    @Test
    void theRepositoryListsABuilding() {
        installed();
        assertThat(new BoxRepository(session).inBuilding("MOO").stream().map(KeyBoxRow::getNumber))
                .containsExactly("KB-01");
    }

    @Test
    void theRepositoryLeavesOutTheWithdrawn() {
        installed().withdraw("KB-01", LATE, "gone");
        assertThat(new BoxRepository(session).live()).isEmpty();
    }

    @Test
    void theRepositoryFindsABoxByItsDoor() {
        installed();
        assertThat(new BoxRepository(session).onOpening("MOO-101").orElseThrow().getNumber())
                .isEqualTo("KB-01");
    }

    @Test
    void aDoorWithNoBoxFindsNothing() {
        installed();
        assertThat(new BoxRepository(session).onOpening("MOO-110")).isEmpty();
    }
}
