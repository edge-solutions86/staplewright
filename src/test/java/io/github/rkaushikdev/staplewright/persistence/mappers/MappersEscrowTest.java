package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.applyToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.boxFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.boxToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.closeSeal;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.currentSeal;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.registerFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.sealHistory;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.sealOn;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.sealToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers.symbolOn;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.BoxRegister;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MappersEscrowTest {

    private static final LocalDate SEALED = LocalDate.of(2025, 1, 6);
    private static final LocalDate WALKED = LocalDate.of(2025, 6, 30);
    private static final LocalDate LATE = LocalDate.of(2025, 12, 1);
    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        session.add(new BuildingRow("MOO", "moore hall"));
        session.flush();
        session.add(new OpeningRow("MOO-100", "MOO", "1", "100", "entrance"));
        session.flush();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static KeyBox aBox() {
        return aBox("KB-01", b -> b);
    }

    private static KeyBox aBox(String number) {
        return aBox(number, b -> b);
    }

    private static KeyBox aBox(UnaryOperator<KeyBox.Builder> overrides) {
        return aBox("KB-01", overrides);
    }

    private static KeyBox aBox(String number, UnaryOperator<KeyBox.Builder> overrides) {
        KeyBox box = new KeyBox(number, "MOO", "MOO-100", CHANGE_KEY, "44012", SEALED);
        return overrides.apply(box.toBuilder()).build();
    }

    private static KeyBox anOpenedBox() {
        return aBox(b -> b.state(BoxState.OPENED).openedOn(WALKED).openedBy(OpenedBy.FIRE));
    }

    private static SealRow aSeal() {
        return aSeal("44012");
    }

    private static SealRow aSeal(String seal) {
        return aSeal(seal, CHANGE_KEY, SEALED);
    }

    private static SealRow aSeal(String seal, KeySymbol symbol, LocalDate sealedOn) {
        return new SealRow("KB-01", seal, symbol, sealedOn);
    }

    private static SealRow aBrokenSeal(String seal, LocalDate brokenOn) {
        SealRow row = aSeal(seal);
        row.setBrokenOn(brokenOn);
        return row;
    }

    @Test
    void aBoxGoesToARowAndBack() {
        KeyBox found = boxFromRow(boxToRow(aBox()));
        assertThat(found).isEqualTo(aBox());
    }

    @Test
    void anOpenBoxKeepsWhoOpenedIt() {
        assertThat(boxFromRow(boxToRow(anOpenedBox())).openedBy()).containsSame(OpenedBy.FIRE);
    }

    @Test
    void aBoxNobodyOpenedHasNoOpener() {
        assertThat(boxToRow(aBox()).getOpenedBy()).isEmpty();
    }

    @Test
    void aStateNobodyRecognisesIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setState("melted");
        assertThatThrownBy(() -> boxFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void anOpenerNobodyRecognisesIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setOpenedBy("the caretaker");
        assertThatThrownBy(() -> boxFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aRowMovesToMatchAChangedBox() {
        KeyBoxRow row = boxToRow(aBox());
        applyToRow(row, anOpenedBox());
        assertThat(row.getState()).isEqualTo("opened");
        assertThat(row.getOpenedBy()).isEqualTo("fire service");
    }

    @Test
    void aRowThatWasResealedForgetsTheOpening() {
        KeyBoxRow row = boxToRow(anOpenedBox());
        applyToRow(row, aBox(b -> b.seal("44120").sealedOn(LATE)));
        assertThat(row.getOpenedOn()).isEmpty();
        assertThat(row.getOpenedBy()).isEmpty();
    }

    @Test
    void theSealRowStartsOpen() {
        assertThat(sealToRow(aBox()).isCurrent()).isTrue();
    }

    @Test
    void closingASealWritesTheDayItCameOff() {
        SealRow row = closeSeal(aSeal(), WALKED, "fire service");
        assertThat(row.getBrokenOn()).contains(WALKED);
        assertThat(row.isCurrent()).isFalse();
    }

    @Test
    void aSealCannotBeClosedTwice() {
        SealRow row = closeSeal(aSeal(), WALKED);
        assertThatThrownBy(() -> closeSeal(row, LATE)).isInstanceOf(InputError.class);
    }

    @Test
    void aSealCannotComeOffBeforeItWentOn() {
        assertThatThrownBy(() -> closeSeal(aSeal(), LocalDate.of(2024, 1, 1))).isInstanceOf(InputError.class);
    }

    @Test
    void aClosingNoteReplacesTheOldOne() {
        SealRow seal = aSeal();
        seal.setNote("first written");
        SealRow row = closeSeal(seal, WALKED, "fire", "kitchen fire");
        assertThat(row.getNote()).isEqualTo("kitchen fire");
    }

    @Test
    void theCurrentSealIsTheOneWithNoEnd() {
        List<SealRow> rows = List.of(aBrokenSeal("44012", WALKED), aSeal("44120", CHANGE_KEY, WALKED));
        assertThat(currentSeal(rows).orElseThrow().getSeal()).isEqualTo("44120");
    }

    @Test
    void aBoxWithEverySealClosedHasNoCurrentOne() {
        assertThat(currentSeal(List.of(aBrokenSeal("44012", WALKED)))).isEmpty();
    }

    @Test
    void theHistoryIsOldestFirst() {
        List<SealRow> rows = List.of(aSeal("44120", CHANGE_KEY, WALKED), aBrokenSeal("44012", WALKED));
        assertThat(sealHistory(rows).stream().map(SealRow::getSeal).toList()).containsExactly("44012", "44120");
    }

    @Test
    void theSealOnADayIsTheOneThatWasOnIt() {
        List<SealRow> rows = List.of(aBrokenSeal("44012", WALKED), aSeal("44120", CHANGE_KEY, WALKED));
        assertThat(sealOn(rows, LocalDate.of(2025, 3, 1)).orElseThrow().getSeal())
                .isEqualTo("44012");
    }

    @Test
    void aDayBeforeAnySealHasNone() {
        assertThat(sealOn(List.of(aSeal()), LocalDate.of(2024, 1, 1))).isEmpty();
    }

    @Test
    void theOpenSealCoversEveryDayAfterItWentOn() {
        assertThat(sealOn(List.of(aSeal()), LATE).orElseThrow().getSeal()).isEqualTo("44012");
    }

    @Test
    void whichKeyWasInTheBoxOnADay() {
        List<SealRow> rows = List.of(aBrokenSeal("44012", WALKED), aSeal("44120", KeySymbol.parse("AA2"), WALKED));
        assertThat(symbolOn(rows, LATE).orElseThrow().render()).isEqualTo("AA2");
    }

    @Test
    void noSealOnADayMeansNoKey() {
        assertThat(symbolOn(List.of(aSeal()), LocalDate.of(2024, 1, 1))).isEmpty();
    }

    @Test
    void aDaysHeldCountRunsToTodayWhileItIsOn() {
        assertThat(aSeal().daysHeld(LocalDate.of(2025, 1, 16))).isEqualTo(10);
    }

    @Test
    void aClosedSealCountsToTheDayItCameOff() {
        assertThat(aBrokenSeal("44012", LocalDate.of(2025, 1, 16)).daysHeld(LATE))
                .isEqualTo(10);
    }

    @Test
    void theRegisterComesBackOutOfRows() {
        List<KeyBoxRow> rows = List.of(boxToRow(aBox()), boxToRow(aBox("KB-02", b -> b.opening("MOO-101"))));
        BoxRegister register = registerFromRows(rows);
        assertThat(register.size()).isEqualTo(2);
    }

    @Test
    void theRegisterKeepsABoxInTheStateItReached() {
        KeyBoxRow row = boxToRow(aBox(b -> b.state(BoxState.EMPTY).seal("")));
        assertThat(registerFromRows(List.of(row)).box("KB-01").state()).isSameAs(BoxState.EMPTY);
    }

    @Test
    void theRegisterTakesThePolicyItIsGiven() {
        BoxRegister register = registerFromRows(List.of(), new BoxPolicy().withLowestLevel(1));
        assertThat(register.policy().lowestLevel()).isEqualTo(1);
    }

    @Test
    void aBoxRowGoesInAndComesOut() {
        session.add(boxToRow(aBox()));
        session.flush();
        KeyBoxRow row = session.get(KeyBoxRow.class, "KB-01").orElseThrow();
        assertThat(row.getSymbol().render()).isEqualTo("AA1");
        assertThat(row.isLive()).isTrue();
    }

    @Test
    void aStateTheTableDoesNotKnowIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setState("melted");
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSealedRowWithoutASealIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setSeal("");
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void anOpenRowWithoutADayIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setState("opened");
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCheckBeforeTheSealWentOnIsRefused() {
        KeyBoxRow row = boxToRow(aBox());
        row.setCheckedOn(LocalDate.of(2024, 1, 1));
        session.add(row);
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void twoBoxesCannotSitOnOneOpening() {
        session.add(boxToRow(aBox()));
        session.flush();
        session.add(boxToRow(aBox("KB-02")));
        assertThatThrownBy(session::flush).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSealHangsOffItsBox() {
        session.add(boxToRow(aBox()));
        session.flush();
        session.add(sealToRow(aBox()));
        session.expireAll();
        KeyBoxRow row = session.get(KeyBoxRow.class, "KB-01").orElseThrow();
        assertThat(row.getSeals().stream().map(SealRow::getSeal).toList()).containsExactly("44012");
    }

    @Test
    void deletingABoxTakesItsSealsWithIt() {
        session.add(boxToRow(aBox()));
        session.flush();
        session.add(sealToRow(aBox()));
        session.expireAll();
        session.delete(session.get(KeyBoxRow.class, "KB-01").orElseThrow());
        session.flush();
        assertThat(session.all(SealRow.class)).isEmpty();
    }

    @Test
    void aSealBrokenBeforeItWentOnIsRefused() {
        session.add(boxToRow(aBox()));
        session.flush();
        SealRow row = sealToRow(aBox());
        row.setBrokenOn(LocalDate.of(2024, 1, 1));
        assertThatThrownBy(() -> {
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }
}
