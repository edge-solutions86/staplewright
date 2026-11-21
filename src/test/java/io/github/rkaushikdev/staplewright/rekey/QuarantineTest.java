package io.github.rkaushikdev.staplewright.rekey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QuarantineTest {

    private static final LocalDate RETIRED_ON = LocalDate.of(2018, 6, 14);
    private static final LocalDate TODAY = LocalDate.of(2025, 4, 8);

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static Quarantine aQuarantine() {
        Quarantine hold = new Quarantine();
        hold.retire(b("342156"), RetirementReason.COMPROMISED, RETIRED_ON, "AA1");
        hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON, "AA2");
        hold.retire(b("382556"), RetirementReason.SUPERSEDED, LocalDate.of(2024, 1, 5), "AB1");
        hold.retire(b("302156"), RetirementReason.DAMAGED, RETIRED_ON);
        return hold;
    }

    private static List<String> cuts(Iterable<RetiredBitting> entries) {
        List<String> found = new ArrayList<>();
        entries.forEach(entry -> found.add(entry.bitting().render()));
        return found;
    }

    @Test
    void aRetiredCutIsRefused() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("342156"))).isTrue();
        assertThat(hold.contains(b("342156"))).isTrue();
        assertThat(hold.isRetired(b("999999"))).isFalse();
    }

    @Test
    void aCutNobodyRetiredIsNotInTheQuarantine() {
        assertThat(aQuarantine().contains(b("999999"))).isFalse();
        assertThat(aQuarantine().contains("342156")).isFalse();
    }

    @Test
    void aCompromisedCutNeverComesBack() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("342156"), LocalDate.of(2099, 1, 1))).isTrue();
        assertThatThrownBy(() -> hold.release(b("342156"))).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSupersededCutComesBackAfterTheWindow() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2024, 6, 13))).isTrue();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2025, 6, 14))).isFalse();
    }

    @Test
    void theDayBeforeTheWindowClosesIsStillRetired() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2025, 6, 13))).isTrue();
    }

    @Test
    void aShopWithAShorterWindowReleasesSooner() {
        Quarantine hold = new Quarantine(3);
        hold.retire(b("362356"), RetirementReason.SUPERSEDED, RETIRED_ON);
        assertThat(hold.isRetired(b("362356"), TODAY)).isFalse();
    }

    @Test
    void aWindowOfNothingIsRefused() {
        assertThatThrownBy(() -> new Quarantine(0)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void askingWithoutADateTreatsEverythingAsRetired() {
        Quarantine hold = aQuarantine();
        assertThat(hold.isRetired(b("362356"))).isTrue();
    }

    @Test
    void aSupersededCutCanBeReleasedByHand() {
        Quarantine hold = aQuarantine();
        RetiredBitting released = hold.release(b("362356"));
        assertThat(released.symbol()).isEqualTo("AA2");
        assertThat(hold.contains(b("362356"))).isFalse();
    }

    @Test
    void releasingSomethingThatWasNeverRetired() {
        assertThatThrownBy(() -> aQuarantine().release(b("999999"))).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aCutCannotBeRetiredTwiceForTheSameKindOfReason() {
        Quarantine hold = aQuarantine();
        assertThatThrownBy(() -> hold.retire(b("362356"), RetirementReason.SUPERSEDED, TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aCompromiseOverwritesASofterRetirement() {
        Quarantine hold = aQuarantine();
        hold.retire(b("362356"), RetirementReason.COMPROMISED, TODAY, "", "turned up on ebay");
        assertThat(hold.entry(b("362356")).isPermanent()).isTrue();
        assertThat(hold.isRetired(b("362356"), LocalDate.of(2099, 1, 1))).isTrue();
    }

    @Test
    void aCompromisedCutCannotBeSoftened() {
        Quarantine hold = aQuarantine();
        assertThatThrownBy(() -> hold.retire(b("342156"), RetirementReason.SUPERSEDED, TODAY))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingAboutACutThatIsNotRetired() {
        assertThatThrownBy(() -> aQuarantine().entry(b("999999"))).isInstanceOf(NotFoundError.class);
    }

    @Test
    void filteringAnArrayDropsTheRetiredCuts() {
        Quarantine hold = aQuarantine();
        List<Bitting> candidates = List.of(b("342156"), b("362356"), b("442156"));
        assertThat(hold.available(candidates)).containsExactly(b("442156"));
    }

    @Test
    void filteringWithADateLetsTheOldOnesThrough() {
        Quarantine hold = aQuarantine();
        List<Bitting> candidates = List.of(b("342156"), b("362356"), b("442156"));
        assertThat(hold.available(candidates, LocalDate.of(2026, 1, 1))).containsExactly(b("362356"), b("442156"));
    }

    @Test
    void thePermanentListIsOnlyTheCompromisedOnes() {
        assertThat(cuts(aQuarantine().permanent())).containsExactly("342156");
    }

    @Test
    void theReleasableListIsWhatHasSatOutItsWindow() {
        assertThat(cuts(aQuarantine().releasable(LocalDate.of(2026, 1, 1)))).containsExactly("302156", "362356");
    }

    @Test
    void aRecentRetirementIsNotReleasable() {
        assertThat(aQuarantine().releasable(TODAY)).isEmpty();
    }

    @Test
    void theReasonsAreCounted() {
        Map<String, Integer> counts = aQuarantine().byReason();
        assertThat(counts).isEqualTo(Map.of("compromised", 1, "damaged", 1, "superseded", 2));
    }

    @Test
    void theQuarantineCountsAndIteratesInCutOrder() {
        Quarantine hold = aQuarantine();
        assertThat(hold.size()).isEqualTo(4);
        assertThat(cuts(hold)).containsExactly("302156", "342156", "362356", "382556");
    }
}
