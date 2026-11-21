package io.github.rkaushikdev.staplewright.reports;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.NotFoundError;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportsIndexTest {

    @Test
    void everyPageOnTheIndexIsARealFunction() {
        for (Page item : ReportIndex.PAGES) {
            Method found = item.resolve();
            assertThat(Modifier.isPublic(found.getModifiers()) && Modifier.isStatic(found.getModifiers()))
                    .as(item.path())
                    .isTrue();
        }
        // A page naming a method nobody wrote has to be caught, or the loop above proves nothing.
        Page planted = new Page("survey", "payroll_summary", "management", "what everybody is paid");
        assertThatThrownBy(planted::resolve).isInstanceOf(NotFoundError.class);
    }

    @Test
    void everyReportFunctionIsOnTheIndex() {
        for (String module : ReportIndex.MODULES) {
            assertThat(ReportIndex.undocumented(module)).as(module).isEmpty();
        }
        // Taking a real page off the list has to make it show up, or the check is reading nothing.
        List<String> listed = ReportIndex.forModule("survey").stream()
                .map(Page::name)
                .filter(name -> !name.equals("survey_report"))
                .toList();
        assertThat(ReportIndex.undocumented("survey", listed)).containsExactly("survey_report");
    }

    @Test
    void noPageIsListedTwice() {
        List<String> found = ReportIndex.PAGES.stream().map(Page::name).toList();
        assertThat(found).hasSameSizeAs(new HashSet<>(found));
    }

    @Test
    void everyPageNamesAModuleThatExists() {
        for (Page item : ReportIndex.PAGES) {
            assertThat(ReportIndex.MODULES).as(item.name()).contains(item.module());
        }
    }

    @Test
    void everyModulePrintsSomething() {
        for (String module : ReportIndex.MODULES) {
            assertThat(ReportIndex.forModule(module)).as(module).isNotEmpty();
        }
    }

    @Test
    void aPageKnowsWhereItLives() {
        assertThat(ReportIndex.page("survey_report").path()).isEqualTo("staplewright.reports.survey.survey_report");
    }

    @Test
    void aPageNobodyPrintsIsA404() {
        assertThatThrownBy(() -> ReportIndex.page("payroll_summary")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theNamesComeBackSorted() {
        assertThat(ReportIndex.names()).isSorted();
    }

    @Test
    void aReaderGetsTheirOwnPages() {
        Set<String> found =
                ReportIndex.forReader("bench").stream().map(Page::name).collect(Collectors.toSet());
        assertThat(found).contains("cylinder_chart", "bitting_list");
    }

    @Test
    void theReadersAreListedOnceEach() {
        List<String> readers = ReportIndex.readers();
        assertThat(readers).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aPageReadsBackAsOneLine() {
        String line = ReportIndex.page("cylinder_chart").describe();
        assertThat(line).startsWith("cylinder_chart");
        assertThat(line).contains("bench");
    }

    @Test
    void theIndexCountsEveryPage() {
        List<String> lines = ReportIndex.indexSheet();
        assertThat(lines.get(0)).isEqualTo(ReportIndex.PAGES.size() + " reports");
    }

    @Test
    void theIndexGroupsByModule() {
        List<String> lines = ReportIndex.indexSheet();
        assertThat(lines).contains("annual");
        assertThat(lines).contains("survey");
    }

    @Test
    void theIndexCanBeNarrowedToOneModule() {
        List<String> lines = ReportIndex.indexSheet("stock");
        assertThat(lines.get(0)).isEqualTo(ReportIndex.forModule("stock").size() + " reports");
    }

    @Test
    void aModuleThatPrintsNothingSaysSo() {
        assertThat(ReportIndex.indexSheet("payroll")).containsExactly("nothing is printed out of payroll");
    }

    @Test
    void aReaderSheetCountsTheirPages() {
        List<String> lines = ReportIndex.readerSheet("bench");
        assertThat(lines.get(0)).startsWith(ReportIndex.forReader("bench").size() + " reports for bench");
    }

    @Test
    void aReaderNobodyServesSaysSo() {
        assertThat(ReportIndex.readerSheet("the dean"))
                .containsExactly("nobody described as the dean asks for anything");
    }

    @Test
    void theModulesComeBackInOrder() {
        assertThat(ReportIndex.everyModule()).isEqualTo(ReportIndex.MODULES);
        assertThat(ReportIndex.MODULES).isSorted();
    }
}
