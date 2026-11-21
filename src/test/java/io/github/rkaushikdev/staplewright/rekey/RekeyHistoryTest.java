package io.github.rkaushikdev.staplewright.rekey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RekeyHistoryTest {

    private static final LocalDate WHEN = LocalDate.of(2020, 3, 4);

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static RekeyEvent anEvent() {
        return anEvent("C-1001", WHEN);
    }

    private static RekeyEvent anEvent(String serial, LocalDate when) {
        return anEvent(serial, when, List.of(sym("AA1"), sym("AA")));
    }

    private static RekeyEvent anEvent(String serial, LocalDate when, List<KeySymbol> toSymbols) {
        return anEvent(serial, when, toSymbols, "jkoivu");
    }

    private static RekeyEvent anEvent(String serial, LocalDate when, String technician) {
        return anEvent(serial, when, List.of(sym("AA1"), sym("AA")), technician);
    }

    private static RekeyEvent anEvent(String serial, LocalDate when, List<KeySymbol> toSymbols, String technician) {
        return new RekeyEvent(serial, when, toSymbols, "", technician, "");
    }

    /** A history with each cylinder repinned once a year from 2015, as many years as its count says. */
    private static RekeyHistory aHistory(RepinCount... counts) {
        RekeyHistory history = new RekeyHistory();
        for (RepinCount count : counts) {
            for (int offset = 0; offset < count.repins(); offset++) {
                history.record(anEvent(count.serial(), LocalDate.of(2015 + offset, 6, 1)));
            }
        }
        return history;
    }

    private static RepinCount repins(String serial, int years) {
        return new RepinCount(serial, years);
    }

    @Test
    void anEventNamesACylinderAndWhatItCameBackAs() {
        RekeyEvent event = anEvent();
        assertThat(event.serial()).isEqualTo("C-1001");
        assertThat(event.wafers()).isEqualTo(1);
        assertThat(event.describe()).contains("AA1");
    }

    @Test
    void anEventWithoutACylinderIsRefused() {
        assertThatThrownBy(() -> anEvent("  ", WHEN)).isInstanceOf(InputError.class);
    }

    @Test
    void aCylinderComesBackKeyedToSomething() {
        assertThatThrownBy(() -> anEvent("C-1001", WHEN, List.of())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSymbolTwiceOnOneCylinderIsRefused() {
        assertThatThrownBy(() -> anEvent("C-1001", WHEN, List.of(sym("AA1"), sym("AA1"))))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void oneSymbolCarriesNoWafers() {
        assertThat(anEvent("C-1001", WHEN, List.of(sym("AA1"))).wafers()).isZero();
    }

    @Test
    void theEventsForACylinderComeBackOldestFirst() {
        RekeyHistory history = new RekeyHistory();
        history.record(anEvent("C-1001", LocalDate.of(2022, 1, 1)));
        history.record(anEvent("C-1001", LocalDate.of(2019, 1, 1)));
        List<RekeyEvent> found = history.eventsFor("C-1001");
        assertThat(found).extracting(event -> event.happenedOn().getYear()).containsExactly(2019, 2022);
    }

    @Test
    void aCylinderNobodyTouchedHasNoHistory() {
        RekeyHistory history = aHistory(repins("C-1001", 2));
        assertThat(history.eventsFor("C-9999")).isEmpty();
        assertThat(history.timesRekeyed("C-9999")).isZero();
        assertThat(history.lastRekeyed("C-9999")).isEmpty();
    }

    @Test
    void theCountAndTheLastDateReadOffTheEvents() {
        RekeyHistory history = aHistory(repins("C-1001", 3));
        assertThat(history.timesRekeyed("C-1001")).isEqualTo(3);
        assertThat(history.lastRekeyed("C-1001")).contains(LocalDate.of(2017, 6, 1));
    }

    @Test
    void whatItIsKeyedToNowIsItsLastEvent() {
        RekeyHistory history = new RekeyHistory();
        history.record(anEvent("C-1001", LocalDate.of(2019, 1, 1), List.of(sym("AA1"))));
        history.record(anEvent("C-1001", LocalDate.of(2022, 1, 1), List.of(sym("AB1"), sym("AB"))));
        assertThat(history.currentSymbols("C-1001"))
                .extracting(KeySymbol::render)
                .containsExactly("AB1", "AB");
    }

    @Test
    void aCylinderWithNoHistoryIsKeyedToNothingOnRecord() {
        assertThat(new RekeyHistory().currentSymbols("C-1001")).isEmpty();
    }

    @Test
    void theIntervalsAreTheGapsBetweenRepins() {
        RekeyHistory history = new RekeyHistory();
        history.record(anEvent("C-1001", LocalDate.of(2020, 1, 1)));
        history.record(anEvent("C-1001", LocalDate.of(2020, 3, 1)));
        history.record(anEvent("C-1001", LocalDate.of(2021, 3, 1)));
        assertThat(history.intervals("C-1001")).containsExactly(60, 365);
        assertThat(history.averageInterval("C-1001")).contains(212);
    }

    @Test
    void oneRepinHasNoInterval() {
        RekeyHistory history = aHistory(repins("C-1001", 1));
        assertThat(history.intervals("C-1001")).isEmpty();
        assertThat(history.averageInterval("C-1001")).isEmpty();
    }

    @Test
    void aCylinderPastTheThresholdIsReported() {
        RekeyHistory history = aHistory(repins("C-1001", 10), repins("C-1002", 3));
        List<RepinCount> found = history.wornOut();
        assertThat(found).extracting(RepinCount::serial).containsExactly("C-1001");
        assertThat(found.get(0).repins()).isEqualTo(10);
    }

    @Test
    void aShopThatReplacesSoonerSaysSo() {
        RekeyHistory history = new RekeyHistory(2);
        history.recordAll(
                List.of(anEvent("C-1002", LocalDate.of(2020, 1, 1)), anEvent("C-1002", LocalDate.of(2021, 1, 1))));
        assertThat(history.wornOut()).extracting(RepinCount::serial).containsExactly("C-1002");
    }

    @Test
    void aThresholdOfNothingIsRefused() {
        assertThatThrownBy(() -> new RekeyHistory(0)).isInstanceOf(InputError.class);
    }

    @Test
    void theCylindersWithNoRecordAreNamed() {
        RekeyHistory history = aHistory(repins("C-1001", 2));
        assertThat(history.neverRekeyed(List.of("C-1001", "C-1002", "C-1003"))).containsExactly("C-1002", "C-1003");
    }

    @Test
    void theBusiestCylindersComeFirst() {
        RekeyHistory history = aHistory(repins("C-1001", 4), repins("C-1002", 7), repins("C-1003", 1));
        assertThat(history.busiest(2)).extracting(RepinCount::serial).containsExactly("C-1002", "C-1001");
    }

    @Test
    void oneTechniciansWorkComesBackTogether() {
        RekeyHistory history = new RekeyHistory();
        history.record(anEvent("C-1001", LocalDate.of(2020, 1, 1), "jkoivu"));
        history.record(anEvent("C-1002", LocalDate.of(2020, 2, 1), "phamm"));
        assertThat(history.byTechnician("phamm")).extracting(RekeyEvent::serial).containsExactly("C-1002");
    }

    @Test
    void everythingSinceADateComesBackInOrder() {
        RekeyHistory history = aHistory(repins("C-1001", 4));
        List<RekeyEvent> found = history.since(LocalDate.of(2017, 1, 1));
        assertThat(found).extracting(event -> event.happenedOn().getYear()).containsExactly(2017, 2018);
    }

    @Test
    void theHistoryPrintsForTheBackOfAWorkOrder() {
        RekeyHistory history = aHistory(repins("C-1001", 2));
        List<String> lines = history.lines("C-1001");
        assertThat(lines.get(0)).isEqualTo("C-1001: 2 repins");
        assertThat(lines).hasSize(3);
    }

    @Test
    void aCylinderWithNothingOnRecordSaysSo() {
        assertThat(new RekeyHistory().lines("C-1001")).containsExactly("C-1001: nothing on record");
    }

    @Test
    void theHistoryCountsAndIteratesEverything() {
        RekeyHistory history = aHistory(repins("C-1001", 2), repins("C-1002", 3));
        assertThat(history.size()).isEqualTo(5);
        List<RekeyEvent> walked = new ArrayList<>();
        history.forEach(walked::add);
        assertThat(walked).hasSize(5);
    }
}
