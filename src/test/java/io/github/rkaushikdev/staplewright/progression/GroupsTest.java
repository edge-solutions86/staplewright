package io.github.rkaushikdev.staplewright.progression;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.support.Systems;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class GroupsTest {

    private static final Map<String, String> CUTS = cuts();

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("B", "242446");
        cuts.put("AA", "622446");
        cuts.put("AB", "662446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        cuts.put("AB1", "662046");
        cuts.put("AB2", "662646");
        return cuts;
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static KeyingGroup group(String name, String... symbols) {
        return new KeyingGroup(name, Stream.of(symbols).map(KeySymbol::parse).toList());
    }

    private static KeyingSchedule aCutSchedule() {
        KeyingSchedule schedule = Systems.smallSchedule();
        CUTS.forEach((symbol, bitting) -> schedule.assign(sym(symbol), Bitting.parse(bitting)));
        return schedule;
    }

    @Test
    void aGroupHoldsTheSymbolsItWasGiven() {
        KeyingGroup keying = group("office 214", "AA1", "AA", "A", "GGM");
        assertThat(keying.size()).isEqualTo(4);
        assertThat(keying.holds(sym("AA"))).isTrue();
        assertThat(keying.holds(sym("AB"))).isFalse();
    }

    @Test
    void aGroupOrdersItsSymbolsForPrinting() {
        KeyingGroup keying = group("office 214", "AA1", "GGM", "A", "AA");
        assertThat(keying.ordered().stream().map(KeySymbol::render).toList()).containsExactly("GGM", "A", "AA", "AA1");
    }

    @Test
    void anEmptyGroupIsRefused() {
        assertThatThrownBy(() -> new KeyingGroup("nothing", List.of())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSymbolListedTwiceIsRefused() {
        assertThatThrownBy(() -> group("twice", "AA1", "AA1")).isInstanceOf(ConflictError.class);
    }

    @Test
    void theTopSymbolIsTheOneEverythingHangsUnder() {
        assertThat(group("g", "AA1", "AA", "A").topSymbol()).isEqualTo(sym("A"));
        assertThat(group("g", "AA1", "AB1").topSymbol()).isEqualTo(sym("A"));
        assertThat(group("g", "AA1", "BB1").topSymbol()).isEqualTo(sym("GGM"));
    }

    @Test
    void aStraightLineOfDescentIsNotCrossKeyed() {
        assertThat(group("office", "AA1", "AA", "A", "GGM").isCrossKeyed()).isFalse();
    }

    @Test
    void oneKeyAloneIsNotCrossKeyed() {
        assertThat(group("closet", "AA1").isCrossKeyed()).isFalse();
    }

    @Test
    void twoChangeKeysUnderOneMasterIsCrossKeying() {
        assertThat(group("shared lab", "AA1", "AA2", "AA").isCrossKeyed()).isTrue();
    }

    @Test
    void keysOnDifferentBranchesAreCrossKeyed() {
        assertThat(group("shared lab", "AA1", "AB1").isCrossKeyed()).isTrue();
    }

    @Test
    void theCutsComeBackInScheduleOrder() {
        KeyingSchedule schedule = aCutSchedule();
        List<Bitting> cuts = Groups.bittingsFor(schedule, group("office", "AA1", "GGM", "AA"));
        assertThat(cuts.stream().map(Bitting::render).toList()).containsExactly("442446", "622446", "620446");
    }

    @Test
    void aKeyThatHasNotBeenCutStopsTheGroup() {
        KeyingSchedule schedule = Systems.smallSchedule();
        NotFoundError caught =
                catchThrowableOfType(NotFoundError.class, () -> Groups.bittingsFor(schedule, group("office", "AA1")));
        assertThat(caught.facts()).containsEntry("symbol", "AA1");
    }

    @Test
    void theSectionsOfAGroupAreReportedOnceEach() {
        KeyingSchedule schedule = aCutSchedule();
        assertThat(Groups.sectionsFor(schedule, group("office", "AA1", "AA", "A", "GGM")))
                .containsExactly("L", "LA", "LC");
    }

    @Test
    void theCylinderTakesTheSectionEveryKeyReaches() {
        KeyingSchedule schedule = aCutSchedule();
        assertThat(Groups.cylinderSection(schedule, group("office", "AA1", "AA", "GGM")))
                .isEqualTo("LC");
    }

    @Test
    void aGroupNoSingleSectionCoversIsRefused() {
        KeyingSchedule schedule = aCutSchedule();
        assertThatThrownBy(() -> Groups.cylinderSection(schedule, group("odd", "AA1", "AB1")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void oneKeyAloneCostsNothingAndLeaksNothing() {
        KeyingSchedule schedule = aCutSchedule();
        GroupCost cost = Groups.costOf(schedule, group("closet", "AA1"));
        assertThat(cost.wafers()).isZero();
        assertThat(cost.operating()).isEqualTo(1);
        assertThat(cost.incidental()).isZero();
        assertThat(cost.isClean()).isTrue();
    }

    @Test
    void aMasterOverAChangeKeyCostsOneWaferASplit() {
        KeyingSchedule schedule = aCutSchedule();
        GroupCost cost = Groups.costOf(schedule, group("office", "AA1", "AA"));
        assertThat(cost.keys()).isEqualTo(2);
        assertThat(cost.wafers()).isEqualTo(1);
        assertThat(cost.incidental()).isZero();
    }

    @Test
    void aFullLineOfDescentLeaksKeys() {
        KeyingSchedule schedule = aCutSchedule();
        GroupCost cost = Groups.costOf(schedule, group("office", "AA1", "AA", "A", "GGM"));
        assertThat(cost.wafers()).isEqualTo(3);
        assertThat(cost.operating()).isEqualTo(8);
        assertThat(cost.incidental()).isEqualTo(4);
    }

    @Test
    void crossKeyingTwoBranchesLeaksMoreThanAStraightLine() {
        KeyingSchedule schedule = aCutSchedule();
        GroupCost straight = Groups.costOf(schedule, group("office", "AA1", "AA"));
        GroupCost crossed = Groups.costOf(schedule, group("lab", "AA1", "AB1"));
        assertThat(crossed.incidental()).isGreaterThan(straight.incidental());
    }

    @Test
    void theWorstGroupsComeBackFirst() {
        KeyingSchedule schedule = aCutSchedule();
        List<KeyingGroup> groups =
                List.of(group("closet", "AA1"), group("office", "AA1", "AA"), group("lab", "AA1", "AB1", "GGM"));
        List<GroupCost> worst = Groups.worstGroups(schedule, groups);
        assertThat(worst.stream().map(GroupCost::group).toList()).containsExactly("lab", "office", "closet");
    }

    @Test
    void theWorstListCanBeCutShort() {
        KeyingSchedule schedule = aCutSchedule();
        List<KeyingGroup> groups = List.of(group("closet", "AA1"), group("lab", "AA1", "AB1", "GGM"));
        assertThat(Groups.worstGroups(schedule, groups, 1)).hasSize(1);
    }
}
