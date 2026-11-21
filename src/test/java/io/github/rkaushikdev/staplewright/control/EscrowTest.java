package io.github.rkaushikdev.staplewright.control;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class EscrowTest {

    private static final LocalDate SEALED = LocalDate.of(2025, 1, 6);
    private static final LocalDate WALKED = LocalDate.of(2025, 6, 30);
    private static final LocalDate LATE = LocalDate.of(2025, 12, 1);
    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");
    private static final KeySymbol MASTER = KeySymbol.parse("A");

    private static KeyBox aBox() {
        return aBox("KB-01");
    }

    private static KeyBox aBox(String number) {
        return new KeyBox(number, "MOO", "MOO-100", CHANGE_KEY, "44012", SEALED);
    }

    private static KeyBox aBox(UnaryOperator<KeyBox.Builder> overrides) {
        return aBox("KB-01", overrides);
    }

    private static KeyBox aBox(String number, UnaryOperator<KeyBox.Builder> overrides) {
        return overrides.apply(aBox(number).toBuilder()).build();
    }

    private static BoxRegister aRegister() {
        BoxRegister register = new BoxRegister();
        register.install(aBox());
        return register;
    }

    private static List<String> numbers(Iterable<KeyBox> boxes) {
        return StreamSupport.stream(boxes.spliterator(), false)
                .map(KeyBox::number)
                .toList();
    }

    @Test
    void aPolicyNeedsALevel() {
        assertThatThrownBy(() -> new BoxPolicy().withLowestLevel(0)).isInstanceOf(InputError.class);
    }

    @Test
    void aPolicyNeedsACheckWindow() {
        assertThatThrownBy(() -> new BoxPolicy().withCheckDays(0)).isInstanceOf(InputError.class);
    }

    @Test
    void aResealWindowDoesNotRunBackwards() {
        assertThatThrownBy(() -> new BoxPolicy().withResealDays(-1)).isInstanceOf(InputError.class);
    }

    @Test
    void aChangeKeyIsAllowedInABox() {
        assertThat(new BoxPolicy().allows(CHANGE_KEY)).isTrue();
    }

    @Test
    void aMasterIsNot() {
        assertThat(new BoxPolicy().allows(MASTER)).isFalse();
    }

    @Test
    void aShopThatAllowsMastersCanSaySo() {
        assertThat(new BoxPolicy().withLowestLevel(1).allows(MASTER)).isTrue();
    }

    @Test
    void aBoxNeedsANumber() {
        assertThatThrownBy(() -> aBox("  ")).isInstanceOf(InputError.class);
    }

    @Test
    void aSealedBoxNeedsASeal() {
        assertThatThrownBy(() -> aBox(box -> box.seal(""))).isInstanceOf(InputError.class);
    }

    @Test
    void anEmptyBoxDoesNotNeedOne() {
        assertThat(aBox(box -> box.seal("").state(BoxState.EMPTY)).seal()).isEmpty();
    }

    @Test
    void aBoxGoesUpAndComesBack() {
        BoxRegister register = aRegister();
        assertThat(register.box("KB-01").state()).isSameAs(BoxState.SEALED);
    }

    @Test
    void aBoxHoldingAMasterIsRefused() {
        BoxRegister register = new BoxRegister();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> register.install(aBox(box -> box.symbol(MASTER))));
        assertThat(caught.facts()).containsEntry("symbol", "A");
    }

    @Test
    void aBoxDoesNotGoUpAlreadyOpen() {
        BoxRegister register = new BoxRegister();
        assertThatThrownBy(() -> register.install(aBox(box -> box.state(BoxState.OPENED))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aBoxNumberIsNotReused() {
        BoxRegister register = aRegister();
        assertThatThrownBy(() -> register.install(aBox())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aBoxNobodyInstalledIsA404() {
        assertThatThrownBy(() -> new BoxRegister().box("KB-99")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void readingTheRightSealIsJustACheck() {
        BoxRegister register = aRegister();
        KeyBox found = register.check("KB-01", "44012", WALKED);
        assertThat(found.state()).isSameAs(BoxState.SEALED);
        assertThat(found.checkedOn()).contains(WALKED);
    }

    @Test
    void aSealWithSpaceRoundItStillMatches() {
        assertThat(aRegister().check("KB-01", " 44012 ", WALKED).state()).isSameAs(BoxState.SEALED);
    }

    @Test
    void aSealThatDoesNotMatchMeansItWasOpened() {
        KeyBox found = aRegister().check("KB-01", "44099", WALKED);
        assertThat(found.state()).isSameAs(BoxState.OPENED);
        assertThat(found.openedBy()).containsSame(OpenedBy.UNKNOWN);
    }

    @Test
    void theNoteSaysWhatWasReadAndWhatWasExpected() {
        KeyBox found = aRegister().check("KB-01", "44099", WALKED);
        assertThat(found.note()).isEqualTo("seal read 44099, expected 44012");
    }

    @Test
    void aBoxAlreadyOpenDoesNotGetItsSealRead() {
        BoxRegister register = aRegister();
        register.check("KB-01", "44099", WALKED);
        assertThatThrownBy(() -> register.check("KB-01", "44099", LATE)).isInstanceOf(ConflictError.class);
    }

    @Test
    void somebodyCanSayTheyOpenedIt() {
        KeyBox found = aRegister().reportOpened("KB-01", OpenedBy.FIRE, WALKED, "kitchen fire");
        assertThat(found.state()).isSameAs(BoxState.OPENED);
        assertThat(found.openedBy()).containsSame(OpenedBy.FIRE);
    }

    @Test
    void aBoxCannotBeOpenedTwice() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThatThrownBy(() -> register.reportOpened("KB-01", OpenedBy.SHOP, LATE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aKeyOnlyGoesMissingOutOfAnOpenBox() {
        BoxRegister register = aRegister();
        assertThatThrownBy(() -> register.empty("KB-01", WALKED)).isInstanceOf(ConflictError.class);
    }

    @Test
    void anOpenBoxCanBeFoundEmpty() {
        BoxRegister register = aRegister();
        register.check("KB-01", "44099", WALKED);
        KeyBox found = register.empty("KB-01", LATE, "no key in it");
        assertThat(found.state()).isSameAs(BoxState.EMPTY);
        assertThat(found.holdsAKey()).isFalse();
    }

    @Test
    void resealingPutsItBackToSealed() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        KeyBox found = register.reseal("KB-01", "44120", CHANGE_KEY, LATE);
        assertThat(found.state()).isSameAs(BoxState.SEALED);
        assertThat(found.seal()).isEqualTo("44120");
        assertThat(found.openedOn()).isEmpty();
    }

    @Test
    void resealingASealedBoxIsRefused() {
        BoxRegister register = aRegister();
        assertThatThrownBy(() -> register.reseal("KB-01", "44120", CHANGE_KEY, LATE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aResealNeedsASealNumber() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThatThrownBy(() -> register.reseal("KB-01", " ", CHANGE_KEY, LATE))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aResealWithTheSameNumberIsRefused() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThatThrownBy(() -> register.reseal("KB-01", "44012", CHANGE_KEY, LATE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aResealCannotSlipAMasterIn() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThatThrownBy(() -> register.reseal("KB-01", "44120", MASTER, LATE))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void anEmptyBoxCanBeResealed() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.empty("KB-01", WALKED);
        assertThat(register.reseal("KB-01", "44120", CHANGE_KEY, LATE).state()).isSameAs(BoxState.SEALED);
    }

    @Test
    void takingABoxDownSaysWhy() {
        BoxRegister register = aRegister();
        assertThatThrownBy(() -> register.withdraw("KB-01", LATE, "")).isInstanceOf(InputError.class);
    }

    @Test
    void aWithdrawnBoxIsOffTheWall() {
        BoxRegister register = aRegister();
        KeyBox found = register.withdraw("KB-01", LATE, "building came down");
        assertThat(found.state()).isSameAs(BoxState.WITHDRAWN);
        assertThat(found.isLive()).isFalse();
    }

    @Test
    void aBoxCannotBeTakenDownTwice() {
        BoxRegister register = aRegister();
        register.withdraw("KB-01", LATE, "building came down");
        assertThatThrownBy(() -> register.withdraw("KB-01", LATE, "again")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aBoxIsDueACheckAfterTheWindow() {
        BoxRegister register = aRegister();
        assertThat(register.dueForCheck(LocalDate.of(2025, 7, 6))).isNotEmpty();
    }

    @Test
    void aBoxCheckedRecentlyIsNotDue() {
        BoxRegister register = aRegister();
        register.check("KB-01", "44012", WALKED);
        assertThat(register.dueForCheck(LocalDate.of(2025, 7, 6))).isEmpty();
    }

    @Test
    void aWithdrawnBoxIsNeverDue() {
        BoxRegister register = aRegister();
        register.withdraw("KB-01", WALKED, "gone");
        assertThat(register.dueForCheck(LATE)).isEmpty();
    }

    @Test
    void aBoxOpenLongerThanTheWindowIsOverdue() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(numbers(register.overdueReseal(LocalDate.of(2025, 7, 5)))).containsExactly("KB-01");
    }

    @Test
    void aBoxOpenedYesterdayIsNotOverdueYet() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(register.overdueReseal(LocalDate.of(2025, 7, 1))).isEmpty();
    }

    @Test
    void aSealedBoxIsNeverOverdueAReseal() {
        assertThat(aRegister().overdueReseal(LATE)).isEmpty();
    }

    @Test
    void theOpenBoxesPutTheEmptyOnesFirst() {
        BoxRegister register = aRegister();
        register.install(aBox("KB-02", box -> box.seal("44013")));
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.reportOpened("KB-02", OpenedBy.SHOP, WALKED);
        register.empty("KB-02", WALKED);
        assertThat(numbers(register.openBoxes())).containsExactly("KB-02", "KB-01");
    }

    @Test
    void aSealedCampusHasNoOpenBoxes() {
        assertThat(aRegister().openBoxes()).isEmpty();
    }

    @Test
    void theBoxesOnABuildingComeBackTogether() {
        BoxRegister register = aRegister();
        register.install(aBox("KB-02", box -> box.building("VOS").seal("44013")));
        assertThat(numbers(register.inBuilding("VOS"))).containsExactly("KB-02");
    }

    @Test
    void theBoxesHoldingASymbolAreTheOnesWithAKeyIn() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.empty("KB-01", WALKED);
        assertThat(register.holding(CHANGE_KEY)).isEmpty();
    }

    @Test
    void theExposureCountsOpenBoxesPerBuilding() {
        BoxRegister register = aRegister();
        register.install(aBox("KB-02", box -> box.seal("44013")));
        register.install(aBox("KB-03", box -> box.building("VOS").seal("44014")));
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.reportOpened("KB-02", OpenedBy.FIRE, WALKED);
        register.reportOpened("KB-03", OpenedBy.FIRE, WALKED);
        assertThat(register.exposure()).isEqualTo(Map.of("MOO", 2, "VOS", 1));
    }

    @Test
    void theSymbolsAtRiskAreTheOnesInAnOpenBox() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(register.symbolsAtRisk().stream().map(KeySymbol::render)).containsExactly("AA1");
    }

    @Test
    void anEmptyBoxPutsNoSymbolAtRisk() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.empty("KB-01", WALKED);
        assertThat(register.symbolsAtRisk()).isEmpty();
    }

    @Test
    void theWalkRoundLeadsWithTheOpenBoxes() {
        BoxRegister register = aRegister();
        register.install(aBox("KB-02", box -> box.seal("44013")));
        register.reportOpened("KB-02", OpenedBy.FIRE, WALKED);
        List<KeyBox> found = BoxRegister.boxesNeedingWork(register, LATE);
        assertThat(found.get(0).number()).isEqualTo("KB-02");
    }

    @Test
    void theWalkRoundDoesNotNameABoxTwice() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        assertThat(BoxRegister.boxesNeedingWork(register, LATE)).hasSize(1);
    }

    @Test
    void aBuildingWithNoBoxIsNamed() {
        assertThat(BoxRegister.coverage(aRegister(), List.of("MOO", "VOS", "HEI")))
                .containsExactly("HEI", "VOS");
    }

    @Test
    void aWithdrawnBoxLeavesABuildingUncovered() {
        BoxRegister register = aRegister();
        register.withdraw("KB-01", LATE, "building came down");
        assertThat(BoxRegister.coverage(register, List.of("MOO"))).containsExactly("MOO");
    }

    @Test
    void theRegisterCountsAndIterates() {
        BoxRegister register = aRegister();
        assertThat(register.size()).isEqualTo(1);
        assertThat(register.contains("KB-01")).isTrue();
        assertThat(numbers(register)).containsExactly("KB-01");
    }

    @Test
    void aBoxReadsBackAsOneLine() {
        assertThat(aBox().describe()).isEqualTo("KB-01 MOO MOO-100 AA1 sealed");
    }

    @Test
    void aBoxThatWasLoadedKeepsTheStateItWasIn() {
        BoxRegister register = new BoxRegister();
        register.load(aBox(box -> box.state(BoxState.EMPTY).seal("")));
        assertThat(register.box("KB-01").state()).isSameAs(BoxState.EMPTY);
    }
}
