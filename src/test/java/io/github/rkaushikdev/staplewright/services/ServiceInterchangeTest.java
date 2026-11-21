package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.ControlKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.CoreRow;
import io.github.rkaushikdev.staplewright.spec.ControlKey;
import io.github.rkaushikdev.staplewright.spec.Core;
import io.github.rkaushikdev.staplewright.spec.CoreFormat;
import io.github.rkaushikdev.staplewright.spec.Crossing;
import io.github.rkaushikdev.staplewright.spec.HousingKind;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceInterchangeTest {

    private static final LocalDate ISSUED = LocalDate.of(2025, 1, 6);
    private static final LocalDate LATER = LocalDate.of(2025, 12, 1);
    private static final Bitting CUT = Bitting.parse("3572461");

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

    private InterchangeService aService() {
        return new InterchangeService(session);
    }

    private InterchangeService withAKey() {
        InterchangeService service = aService();
        service.cutKey("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED);
        return service;
    }

    @Test
    void aControlKeyGoesToARowAndBack() {
        ControlKey key = new ControlKey("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED, "phamm");
        assertThat(InterchangeService.keyFromRow(InterchangeService.keyToRow(key)))
                .isEqualTo(key);
    }

    @Test
    void aCoreGoesToARowAndBack() {
        Core core = new Core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101");
        assertThat(InterchangeService.coreFromRow(InterchangeService.coreToRow(core)))
                .isEqualTo(core);
    }

    @Test
    void aCoreOnTheShelfKeepsAnEmptyOpening() {
        Core core = new Core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThat(InterchangeService.coreToRow(core).getInstalledAt()).isEmpty();
        assertThat(InterchangeService.coreFromRow(InterchangeService.coreToRow(core))
                        .opening())
                .isEmpty();
    }

    @Test
    void aControlKeyIsCutAndReadBack() {
        InterchangeService service = withAKey();
        assertThat(service.key("CK-1").getSection()).isEqualTo("A");
        assertThat(service.key("CK-1").isLive()).isTrue();
    }

    @Test
    void aControlKeyCodeIsNotReused() {
        InterchangeService service = withAKey();
        assertThatThrownBy(() -> service.cutKey("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aControlKeyNobodyCutIsA404() {
        assertThatThrownBy(() -> aService().key("CK-9")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCoreNeedsAControlKeyThatExists() {
        assertThatThrownBy(() -> aService().registerCore("K-1001", CoreFormat.SFIC_A2, "CK-9", HousingKind.MORTISE))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCoreOfTheWrongFormatIsRefused() {
        InterchangeService service = withAKey();
        ConflictError caught = catchThrowableOfType(
                ConflictError.class,
                () -> service.registerCore("K-1001", CoreFormat.SFIC_A4, "CK-1", HousingKind.MORTISE));
        assertThat(caught.facts()).containsEntry("key_format", "small format, A2");
    }

    @Test
    void aCoreOnADoorNobodyHasIsRefused() {
        InterchangeService service = withAKey();
        assertThatThrownBy(() ->
                        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-999"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCoreGoesOnTheRegister() {
        CoreRow row = withAKey().registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThat(row.getSerial()).isEqualTo("K-1001");
        assertThat(row.isInstalled()).isFalse();
    }

    @Test
    void handingAControlKeyOverRecordsWho() {
        assertThat(withAKey().handTo("CK-1", "phamm").getHolder()).isEqualTo("phamm");
    }

    @Test
    void aControlKeyGoesToSomebody() {
        InterchangeService service = withAKey();
        assertThatThrownBy(() -> service.handTo("CK-1", "  ")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aWithdrawnKeyCannotBeHandedOver() {
        InterchangeService service = withAKey();
        service.withdraw("CK-1", LATER);
        assertThatThrownBy(() -> service.handTo("CK-1", "phamm")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyWithCoresCannotBeWithdrawn() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> service.withdraw("CK-1", LATER));
        assertThat(caught.facts()).containsEntry("cores", 1);
    }

    @Test
    void aKeyCannotBeWithdrawnTwice() {
        InterchangeService service = withAKey();
        service.withdraw("CK-1", LATER);
        assertThatThrownBy(() -> service.withdraw("CK-1", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void aCoreGoesIntoADoor() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThat(service.install("K-1001", "MOO-101").getInstalledAt()).contains("MOO-101");
    }

    @Test
    void aHousingOnlyTakesOneCore() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        service.registerCore("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        service.install("K-1001", "MOO-101");
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> service.install("K-1002", "MOO-101"));
        assertThat(caught.facts()).containsEntry("serial", "K-1001");
    }

    @Test
    void anotherHousingOnTheSameDoorIsFine() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        service.registerCore("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.RIM);
        service.install("K-1001", "MOO-101");
        assertThat(service.install("K-1002", "MOO-101").getInstalledAt()).contains("MOO-101");
    }

    @Test
    void installingACoreIntoADoorNobodyHas() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThatThrownBy(() -> service.install("K-1001", "MOO-999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void pullingACoreTakesItOffTheWall() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101");
        assertThat(service.pull("K-1001").getInstalledAt()).isEmpty();
    }

    @Test
    void aCoreAlreadyOffTheWallCannotBePulled() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThatThrownBy(() -> service.pull("K-1001")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aCoreNobodyRegisteredIsA404() {
        assertThatThrownBy(() -> aService().core("K-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void oneControlKeyMeansNoCrossings() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        service.registerCore("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        assertThat(service.crossings()).isEmpty();
    }

    @Test
    void twoControlKeysOnOneHousingKindCross() {
        InterchangeService service = withAKey();
        service.cutKey("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED);
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE);
        service.registerCore("K-1002", CoreFormat.SFIC_A2, "CK-2", HousingKind.MORTISE);
        assertThat(service.crossings()).containsExactly(new Crossing("K-1001", "K-1002"));
    }

    @Test
    void theReportSaysHowManySectionsAreInUse() {
        InterchangeService service = withAKey();
        service.cutKey("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED);
        List<String> lines = service.report();
        assertThat(lines.get(0)).isEqualTo("2 live control keys on 2 sections");
        assertThat(lines).anyMatch(line -> line.contains("more than one control section"));
    }

    @Test
    void theSectionsComeOffTheRegister() {
        assertThat(withAKey().sections()).containsExactly("A");
    }

    @Test
    void theHolderSheetCountsTheKeysOut() {
        InterchangeService service = withAKey();
        service.handTo("CK-1", "phamm");
        assertThat(service.holders().get(0)).isEqualTo("1 control keys are out");
    }

    @Test
    void theRepositoryListsTheLiveKeys() {
        InterchangeService service = withAKey();
        service.cutKey("CK-2", "B", CoreFormat.SFIC_A2, CUT, ISSUED);
        service.withdraw("CK-2", LATER);
        assertThat(new ControlKeyRepository(session).live().stream().map(ControlKeyRow::getCode))
                .containsExactly("CK-1");
    }

    @Test
    void theRepositoryFindsWhoHoldsOne() {
        withAKey().handTo("CK-1", "phamm");
        assertThat(new ControlKeyRepository(session).heldBy("phamm").stream().map(ControlKeyRow::getCode))
                .containsExactly("CK-1");
    }

    @Test
    void theRepositorySplitsInstalledFromShelved() {
        InterchangeService service = withAKey();
        service.registerCore("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101");
        service.registerCore("K-1002", CoreFormat.SFIC_A2, "CK-1", HousingKind.RIM);
        CoreRepository cores = new CoreRepository(session);
        assertThat(cores.installed().stream().map(CoreRow::getSerial)).containsExactly("K-1001");
        assertThat(cores.onTheShelf().stream().map(CoreRow::getSerial)).containsExactly("K-1002");
    }

    @Test
    void aControlKeyWithoutASectionIsRefused() {
        assertThatThrownBy(() -> {
                    session.add(new ControlKeyRow("CK-9", "", "small format, A2", CUT, ISSUED));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aKeyWithdrawnBeforeItWasIssuedIsRefused() {
        assertThatThrownBy(() -> {
                    ControlKeyRow row = new ControlKeyRow("CK-9", "A", "small format, A2", CUT, ISSUED);
                    row.setWithdrawnOn(LocalDate.of(2024, 1, 1));
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCorePulledByNothingIsRefused() {
        assertThatThrownBy(() -> {
                    session.add(new CoreRow("K-1001", "small format, A2", "CK-9", "mortise housing"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }
}
