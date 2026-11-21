package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.control.BoxRegister;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class EscrowReportsTest {

    private static final LocalDate SEALED = LocalDate.of(2025, 1, 6);
    private static final LocalDate WALKED = LocalDate.of(2025, 6, 30);
    private static final LocalDate LATE = LocalDate.of(2025, 12, 1);
    private static final List<String> BUILDINGS = List.of("HEI", "MOO", "VOS");
    private static final KeySymbol CHANGE_KEY = KeySymbol.parse("AA1");

    private static KeyBox aBox() {
        return new KeyBox("KB-01", "MOO", "MOO-101", CHANGE_KEY, "44012", SEALED);
    }

    private static BoxRegister aRegister() {
        BoxRegister register = new BoxRegister();
        register.install(aBox());
        register.install(new KeyBox("KB-02", "VOS", "VOS-100", CHANGE_KEY, "44013", SEALED));
        return register;
    }

    private static SealRow aSeal(String seal) {
        return aSeal(seal, SEALED);
    }

    private static SealRow aSeal(String seal, LocalDate sealedOn) {
        return new SealRow("KB-01", seal, CHANGE_KEY, sealedOn);
    }

    @Test
    void theCoverageSheetNamesEveryBuilding() {
        List<String> lines = EscrowPages.coverageSheet(aRegister(), BUILDINGS);
        assertThat(lines.get(0)).startsWith("building");
        assertThat(lines.get(1)).startsWith("HEI");
        assertThat(lines.get(1)).contains("none");
    }

    @Test
    void theCoverageSheetSaysNothingAboutKeys() {
        List<String> lines = EscrowPages.coverageSheet(aRegister(), BUILDINGS);
        assertThat(lines).noneMatch(line -> line.contains("AA1"));
    }

    @Test
    void aWithdrawnBoxLeavesABuildingUncovered() {
        BoxRegister register = aRegister();
        register.withdraw("KB-01", LATE, "gone");
        List<String> lines = EscrowPages.coverageSheet(register, BUILDINGS);
        assertThat(lines).anyMatch(line -> line.startsWith("MOO") && line.contains("none"));
    }

    @Test
    void aSealedCampusHasNoExposure() {
        assertThat(EscrowPages.exposureSheet(aRegister(), LATE)).containsExactly("every box is sealed");
    }

    @Test
    void theExposureSheetCountsTheDays() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        List<String> lines = EscrowPages.exposureSheet(register, LocalDate.of(2025, 7, 10));
        assertThat(lines.get(0)).isEqualTo("1 boxes are not sealed");
        assertThat(lines.get(1)).contains("10 days");
    }

    @Test
    void theExposureSheetNamesTheLooseKeys() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        List<String> lines = EscrowPages.exposureSheet(register, LATE);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("keys loose: AA1");
    }

    @Test
    void anEmptyBoxPutsNoKeyLoose() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        register.empty("KB-01", WALKED);
        List<String> lines = EscrowPages.exposureSheet(register, LATE);
        assertThat(lines.get(1)).contains("nothing in it");
        assertThat(lines).noneMatch(line -> line.startsWith("keys loose"));
    }

    @Test
    void aQuietWeekHasNothingToWalk() {
        assertThat(EscrowPages.walkSheet(aRegister(), LocalDate.of(2025, 1, 20)))
                .containsExactly("nothing to walk this week");
    }

    @Test
    void theWalkSheetLeadsWithTheReseals() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        List<String> lines = EscrowPages.walkSheet(register, LATE);
        assertThat(lines.get(0)).isEqualTo("1 to reseal");
    }

    @Test
    void theWalkSheetCountsTheDaysSinceACheck() {
        List<String> lines = EscrowPages.walkSheet(aRegister(), LocalDate.of(2025, 7, 6));
        assertThat(lines).anyMatch(line -> line.contains("181 days ago"));
    }

    @Test
    void noSealsSaysSo() {
        assertThat(EscrowPages.sealSheet(List.of(), LATE)).containsExactly("no seal was ever recorded on that box");
    }

    @Test
    void theSealSheetShowsTheOpenSealAsOnNow() {
        List<String> lines = EscrowPages.sealSheet(List.of(aSeal("44012")), LATE);
        assertThat(lines.get(1)).contains("on now");
    }

    @Test
    void theSealSheetIsOldestFirst() {
        SealRow broken = aSeal("44012");
        broken.setBrokenOn(WALKED);
        List<SealRow> rows = List.of(aSeal("44120", WALKED), broken);
        List<String> lines = EscrowPages.sealSheet(rows, LATE);
        assertThat(lines.get(1)).startsWith("44012");
    }

    @Test
    void nothingOverdueSaysSo() {
        BoxRegister register = aRegister();
        assertThat(EscrowPages.overdueSheet(register, LATE, register.policy()).get(0))
                .isEqualTo("no box is overdue a reseal");
    }

    @Test
    void aBoxOpenTooLongIsOnTheOverdueSheet() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        List<String> lines = EscrowPages.overdueSheet(register, LocalDate.of(2025, 7, 10), register.policy());
        assertThat(lines.get(0)).isEqualTo("1 boxes are past the 2 day reseal window");
        assertThat(lines.get(1)).contains("open 10 days");
    }

    @Test
    void theStateCountsCoverEveryState() {
        assertThat(EscrowPages.stateCounts(aRegister()).keySet())
                .isEqualTo(Arrays.stream(BoxState.values()).map(BoxState::value).collect(Collectors.toSet()));
    }

    @Test
    void theStateCountsAddUp() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        Map<String, Integer> counts = EscrowPages.stateCounts(register);
        assertThat(counts.get("sealed")).isEqualTo(1);
        assertThat(counts.get("opened")).isEqualTo(1);
    }

    @Test
    void theShopReportReadsTopToBottom() {
        BoxRegister register = aRegister();
        register.reportOpened("KB-01", OpenedBy.FIRE, WALKED);
        List<String> lines = EscrowPages.shopReport(register, BUILDINGS, LATE);
        assertThat(lines.get(0)).isEqualTo("key boxes at 2025-12-01, 2 on record");
        assertThat(lines.get(1)).isEqualTo("1 sealed, 1 opened");
        assertThat(lines).anyMatch(line -> line.startsWith("1 buildings have no box"));
    }

    @Test
    void aFullyCoveredCampusSaysSo() {
        List<String> lines = EscrowPages.shopReport(aRegister(), List.of("MOO", "VOS"), LATE);
        assertThat(lines.get(lines.size() - 1)).isEqualTo("every building has a box");
    }

    @Test
    void aKeyNobodyHoldsSaysSo() {
        assertThat(EscrowPages.keyFound(aRegister(), "ZZ9")).containsExactly("no box holds ZZ9");
    }

    @Test
    void aFoundKeyNamesTheBoxesItBelongsTo() {
        List<String> lines = EscrowPages.keyFound(aRegister(), "AA1");
        assertThat(lines.get(0)).isEqualTo("2 boxes hold AA1");
    }
}
