package io.github.rkaushikdev.staplewright.spec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class InterchangeTest {

    private static final LocalDate ISSUED = LocalDate.of(2025, 1, 6);
    private static final LocalDate LATER = LocalDate.of(2025, 12, 1);
    private static final Bitting CUT = Bitting.parse("3572461");

    private static ControlKey aKey() {
        return aKey("CK-1");
    }

    private static ControlKey aKey(String code) {
        return aKey(code, "A");
    }

    private static ControlKey aKey(String code, String section) {
        return new ControlKey(code, section, CoreFormat.SFIC_A2, CUT, ISSUED);
    }

    private static Core aCore() {
        return aCore("K-1001");
    }

    private static Core aCore(String serial) {
        return aCore(serial, "CK-1");
    }

    private static Core aCore(String serial, String controlCode) {
        return aCore(serial, controlCode, CoreFormat.SFIC_A2, HousingKind.MORTISE);
    }

    private static Core aCore(String serial, String controlCode, CoreFormat format, HousingKind housing) {
        return new Core(serial, format, controlCode, housing);
    }

    private static ControlRegister aRegister() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        register.addCore(aCore());
        return register;
    }

    @Test
    void aControlKeyNeedsACode() {
        assertThatThrownBy(() -> aKey(" ")).isInstanceOf(InputError.class);
    }

    @Test
    void aControlKeyIsCutOnASection() {
        assertThatThrownBy(() -> aKey("CK-1", "")).isInstanceOf(InputError.class);
    }

    @Test
    void aFreshControlKeyIsLive() {
        assertThat(aKey().isLive()).isTrue();
    }

    @Test
    void aCoreHasASerial() {
        assertThatThrownBy(() -> aCore("")).isInstanceOf(InputError.class);
    }

    @Test
    void aCoreIsPulledBySomething() {
        assertThatThrownBy(() -> aCore("K-1001", " ")).isInstanceOf(InputError.class);
    }

    @Test
    void aCoreInADoorIsInstalled() {
        Core core = new Core("K-1001", CoreFormat.SFIC_A2, "CK-1", HousingKind.MORTISE, "MOO-101");
        assertThat(core.isInstalled()).isTrue();
    }

    @Test
    void aCoreOnTheShelfIsNot() {
        assertThat(aCore().isInstalled()).isFalse();
    }

    @Test
    void aControlKeyPullsItsOwnCores() {
        assertThat(aKey().canPull(aCore())).isTrue();
    }

    @Test
    void aControlKeyDoesNotPullAnotherOnes() {
        assertThat(aKey().canPull(aCore("K-1001", "CK-2"))).isFalse();
    }

    @Test
    void aControlKeyDoesNotPullAnotherFormat() {
        assertThat(aKey().canPull(aCore("K-1001", "CK-1", CoreFormat.SFIC_A4, HousingKind.MORTISE)))
                .isFalse();
    }

    @Test
    void aWithdrawnControlKeyPullsNothing() {
        ControlKey key = new ControlKey("CK-1", "A", CoreFormat.SFIC_A2, CUT, ISSUED, "", Optional.of(LATER));
        assertThat(key.canPull(aCore())).isFalse();
    }

    @Test
    void aControlKeyReadsBackAsOneLine() {
        assertThat(aKey().describe()).isEqualTo("CK-1 small format, A2 held by nobody named, live");
    }

    @Test
    void aCoreNothingPullsIsRefused() {
        ControlRegister register = new ControlRegister();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> register.addCore(aCore()));
        assertThat(caught.facts()).containsEntry("control", "CK-1");
    }

    @Test
    void aCoreOfTheWrongFormatIsRefused() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        ConflictError caught = catchThrowableOfType(
                ConflictError.class,
                () -> register.addCore(aCore("K-1001", "CK-1", CoreFormat.SFIC_A4, HousingKind.MORTISE)));
        assertThat(caught.facts()).containsEntry("key_format", "small format, A2");
    }

    @Test
    void aCoreGoesOnAndComesBack() {
        assertThat(aRegister().core("K-1001").housing()).isSameAs(HousingKind.MORTISE);
    }

    @Test
    void aCoreNobodyRegisteredIsA404() {
        assertThatThrownBy(() -> aRegister().core("K-9999")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aControlKeyNobodyRegisteredIsA404() {
        assertThatThrownBy(() -> aRegister().key("CK-9")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void handingAControlKeyOverRecordsWho() {
        ControlRegister register = aRegister();
        assertThat(register.handTo("CK-1", "phamm").holder()).isEqualTo("phamm");
    }

    @Test
    void aControlKeyGoesToSomebody() {
        ControlRegister register = aRegister();
        assertThatThrownBy(() -> register.handTo("CK-1", " ")).isInstanceOf(InputError.class);
    }

    @Test
    void aWithdrawnKeyCannotBeHandedOver() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        register.withdraw("CK-1", LATER);
        assertThatThrownBy(() -> register.handTo("CK-1", "phamm")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyWithCoresOnItCannotBeWithdrawn() {
        ControlRegister register = aRegister();
        ConflictError caught = catchThrowableOfType(ConflictError.class, () -> register.withdraw("CK-1", LATER));
        assertThat(caught.facts()).containsEntry("cores", 1);
    }

    @Test
    void aKeyWithNoCoresCanBeWithdrawn() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        assertThat(register.withdraw("CK-1", LATER).isLive()).isFalse();
    }

    @Test
    void aKeyCannotBeWithdrawnTwice() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        register.withdraw("CK-1", LATER);
        assertThatThrownBy(() -> register.withdraw("CK-1", LATER)).isInstanceOf(ConflictError.class);
    }

    @Test
    void theCoresOnOneKeyComeBack() {
        ControlRegister register = aRegister();
        register.addCore(aCore("K-1002"));
        assertThat(register.coresPulledBy("CK-1")).hasSize(2);
    }

    @Test
    void theLiveKeysLeaveOutTheWithdrawn() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        register.addKey(aKey("CK-2", "B"));
        register.withdraw("CK-2", LATER);
        assertThat(register.liveKeys().stream().map(ControlKey::code)).containsExactly("CK-1");
    }

    @Test
    void theSectionsInUseAreListed() {
        ControlRegister register = aRegister();
        register.addKey(aKey("CK-2", "B"));
        assertThat(register.sections()).containsExactly("A", "B");
    }

    @Test
    void theHoldersAreCountedMostFirst() {
        ControlRegister register = aRegister();
        register.addKey(aKey("CK-2", "A"));
        register.handTo("CK-1", "phamm");
        register.handTo("CK-2", "phamm");
        assertThat(register.holders()).isEqualTo(Map.of("phamm", 2));
    }

    @Test
    void aKeyNobodySignedForShowsAsNobody() {
        assertThat(aRegister().holders()).isEqualTo(Map.of("nobody named", 1));
    }

    @Test
    void aCoreWhoseKeyWentIsUnaccountedFor() {
        ControlRegister register = new ControlRegister();
        register.addKey(aKey());
        register.addKey(aKey("CK-2", "A"));
        register.addCore(aCore("K-1002", "CK-2"));
        register.withdraw("CK-1", LATER);
        assertThat(register.unaccountedCores().stream().map(Core::serial)).isEmpty();
    }

    @Test
    void theRegisterCountsAndContains() {
        ControlRegister register = aRegister();
        assertThat(register.size()).isEqualTo(1);
        assertThat(register.contains("CK-1")).isTrue();
        assertThat(StreamSupport.stream(register.spliterator(), false).map(ControlKey::code))
                .containsExactly("CK-1");
    }

    @Test
    void twoCoresOnOneKeyAreNotACrossing() {
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002"));
        assertThat(Interchange.swappable(cores)).isEmpty();
    }

    @Test
    void twoCoresOnDifferentKeysThatFitAreACrossing() {
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002", "CK-2"));
        assertThat(Interchange.swappable(cores)).containsExactly(new Crossing("K-1001", "K-1002"));
    }

    @Test
    void coresInDifferentHousingsCannotSwap() {
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002", "CK-2", CoreFormat.SFIC_A2, HousingKind.RIM));
        assertThat(Interchange.swappable(cores)).isEmpty();
    }

    @Test
    void coresOfDifferentFormatsCannotSwap() {
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002", "CK-2", CoreFormat.SFIC_A4, HousingKind.MORTISE));
        assertThat(Interchange.swappable(cores)).isEmpty();
    }

    @Test
    void aPairIsOnlyReportedOnce() {
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002", "CK-2"), aCore("K-1003", "CK-2"));
        assertThat(Interchange.swappable(cores)).hasSize(2);
    }

    @Test
    void oneControlSectionIsTheQuietCase() {
        ControlRegister register = aRegister();
        List<String> lines = Interchange.crossingReport(register, List.of(aCore()));
        assertThat(lines.get(0)).isEqualTo("1 live control keys on 1 sections");
        assertThat(lines.get(lines.size() - 1)).isEqualTo("no core can be moved across a control key");
    }

    @Test
    void twoControlSectionsAreCalledOut() {
        ControlRegister register = aRegister();
        register.addKey(aKey("CK-2", "B"));
        List<String> lines = Interchange.crossingReport(register, List.of(aCore()));
        assertThat(lines).anySatisfy(line -> assertThat(line).contains("more than one control section"));
    }

    @Test
    void theCrossingsAreNamed() {
        ControlRegister register = aRegister();
        register.addKey(aKey("CK-2", "A"));
        register.addCore(aCore("K-1002", "CK-2"));
        List<Core> cores = List.of(aCore("K-1001"), aCore("K-1002", "CK-2"));
        List<String> lines = Interchange.crossingReport(register, cores);
        assertThat(lines).anySatisfy(line -> assertThat(Text.strip(line)).isEqualTo("K-1001 and K-1002"));
    }

    @Test
    void theControlSectionsComeOffTheRegister() {
        assertThat(Interchange.controlSections(aRegister())).containsExactly("A");
    }

    @Test
    void noControlKeyOutSaysSo() {
        assertThat(Interchange.holderSheet(new ControlRegister())).containsExactly("no control key is out");
    }

    @Test
    void theHolderSheetCountsThem() {
        ControlRegister register = aRegister();
        register.handTo("CK-1", "phamm");
        List<String> lines = Interchange.holderSheet(register);
        assertThat(lines.get(0)).isEqualTo("1 control keys are out");
        assertThat(Text.strip(lines.get(1))).startsWith("phamm");
    }
}
