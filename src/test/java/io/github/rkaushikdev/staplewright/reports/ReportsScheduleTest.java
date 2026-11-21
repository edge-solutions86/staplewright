package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Systems.smallSchedule;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReportsScheduleTest {

    private static final Map<String, String> CUTS = cuts();

    private static Map<String, String> cuts() {
        Map<String, String> cuts = new LinkedHashMap<>();
        cuts.put("GGM", "442446");
        cuts.put("A", "642446");
        cuts.put("AA", "622446");
        cuts.put("AA1", "620446");
        cuts.put("AA2", "624446");
        return cuts;
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static KeyingSchedule aCutSchedule() {
        KeyingSchedule schedule = smallSchedule();
        CUTS.forEach((symbol, bitting) -> schedule.assign(sym(symbol), Bitting.parse(bitting)));
        return schedule;
    }

    private static String linesEnd(List<String> lines) {
        return lines.get(lines.size() - 1);
    }

    @Test
    void aLineCarriesTheSymbolTheSectionAndTheCut() {
        KeyingSchedule schedule = aCutSchedule();
        String line = SchedulePages.scheduleLine(schedule.entry(sym("AA1")));
        assertThat(line).contains("AA1");
        assertThat(line).contains("LC");
        assertThat(line).contains("620446");
    }

    @Test
    void anUncutLineSaysOpen() {
        KeyingSchedule schedule = aCutSchedule();
        assertThat(SchedulePages.scheduleLine(schedule.entry(sym("AB1")))).contains("open");
    }

    @Test
    void aLineIsIndentedByItsLevel() {
        KeyingSchedule schedule = aCutSchedule();
        String top = SchedulePages.scheduleLine(schedule.entry(sym("GGM")));
        String change = SchedulePages.scheduleLine(schedule.entry(sym("AA1")));
        assertThat(top).doesNotStartWith(" ");
        assertThat(change).startsWith("      ");
    }

    @Test
    void indentationCanBeTurnedOff() {
        KeyingSchedule schedule = aCutSchedule();
        assertThat(SchedulePages.scheduleLine(schedule.entry(sym("AA1")), false))
                .doesNotStartWith(" ");
    }

    @Test
    void theReportHasAHeadingAndALineASymbol() {
        KeyingSchedule schedule = aCutSchedule();
        List<String> lines = SchedulePages.scheduleReport(schedule, "chester park");
        assertThat(lines.get(0)).isEqualTo("chester park");
        assertThat(lines.get(1)).startsWith("symbol");
        assertThat(lines).hasSize(schedule.size() + 3);
    }

    @Test
    void theReportCountsWhatIsStillOpen() {
        KeyingSchedule schedule = aCutSchedule();
        assertThat(linesEnd(SchedulePages.scheduleReport(schedule))).isEqualTo("9 symbols, 4 of them open");
    }

    @Test
    void aReportWithoutATitleStartsAtTheHeading() {
        assertThat(SchedulePages.scheduleReport(aCutSchedule()).get(0)).startsWith("symbol");
    }

    @Test
    void theBittingListIsSortedByCut() {
        List<String> lines = SchedulePages.bittingList(aCutSchedule());
        List<String> cuts = lines.subList(1, lines.size()).stream()
                .map(line -> Text.words(line).get(0))
                .toList();
        assertThat(cuts).isSorted();
    }

    @Test
    void theBittingListLeavesOutWhatIsNotCut() {
        List<String> lines = SchedulePages.bittingList(aCutSchedule());
        assertThat(lines).hasSize(CUTS.size() + 1);
        assertThat(lines).allMatch(line -> !line.contains("open"));
    }

    @Test
    void theBittingListOfNothingIsJustAHeading() {
        assertThat(SchedulePages.bittingList(smallSchedule())).hasSize(1);
    }

    @Test
    void theOpenReportNamesWhatIsOwed() {
        List<String> lines = SchedulePages.openSymbolsReport(aCutSchedule());
        assertThat(lines.get(0)).isEqualTo("4 symbols drawn and not cut");
        assertThat(lines).anyMatch(line -> line.contains("AB1"));
    }

    @Test
    void aFinishedScheduleSaysSo() {
        KeyingSchedule schedule = smallSchedule();
        int number = 0;
        for (KeySymbol symbol : schedule.symbols()) {
            schedule.assign(symbol, Bitting.of(2, 4, 2, 4, 4, 6).withDepth(1, number));
            number++;
        }
        assertThat(SchedulePages.openSymbolsReport(schedule)).containsExactly("every symbol on this schedule is cut");
    }

    @Test
    void theTreeReportCountsWhatHangsUnderEachKey() {
        List<String> lines = SchedulePages.treeReport(aCutSchedule());
        assertThat(lines.get(0)).startsWith("GGM");
        assertThat(lines.get(0)).contains("8 under it");
        assertThat(lines).anyMatch(line -> Text.strip(line).startsWith("AA1") && line.contains("leaf"));
    }

    @Test
    void theCrossReferenceListsTheDoors() {
        KeyingSchedule schedule = aCutSchedule();
        Map<KeySymbol, List<String>> doors = Map.of(sym("AA1"), List.of("MOO-214", "MOO-216"));
        List<String> lines = SchedulePages.crossReference(schedule, doors);
        assertThat(lines).anyMatch(line -> line.contains("MOO-214, MOO-216"));
    }

    @Test
    void aSymbolWithNoDoorsSaysSo() {
        List<String> lines = SchedulePages.crossReference(aCutSchedule(), Map.of());
        assertThat(lines.subList(1, lines.size())).allMatch(line -> line.contains("nothing on record"));
    }

    @Test
    void linesJoinIntoAPage() {
        String page = Layout.asText(SchedulePages.scheduleReport(aCutSchedule(), "chester park"));
        assertThat(page).startsWith("chester park");
        assertThat(page.chars().filter(at -> at == '\n').count())
                .isEqualTo(SchedulePages.scheduleReport(aCutSchedule(), "chester park")
                                .size()
                        - 1);
    }
}
