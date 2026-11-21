package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Charts.sevenPinCoreChart;
import static io.github.rkaushikdev.staplewright.support.Charts.sixPinChart;
import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.pinning.CorePinning;
import io.github.rkaushikdev.staplewright.pinning.CoreSpec;
import io.github.rkaushikdev.staplewright.pinning.CoreStack;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import io.github.rkaushikdev.staplewright.pinning.PinSegment;
import io.github.rkaushikdev.staplewright.pinning.PinStack;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ReportsPinningTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static PinningSpec aSpec() {
        return new PinningSpec(sixPinChart(), 150, 480);
    }

    private static CoreSpec aCoreSpec() {
        return new CoreSpec(new PinningSpec(sevenPinCoreChart(), 100, 600, 25, 75), 50);
    }

    @Test
    void aRowReadsBottomPinThenWafersThenDriver() {
        String row = PinningPages.stackRow(PinStack.build(aSpec(), 2, List.of(2, 4, 8)));
        assertThat(Text.words(row)).containsExactly("180", "30", "60", "210");
    }

    @Test
    void aRowOfOnePinStillHasTwoPieces() {
        assertThat(Text.words(PinningPages.stackRow(PinStack.build(aSpec(), 1, List.of(3)))))
                .containsExactly("195", "285");
    }

    @Test
    void everyPieceIsRightAlignedInItsOwnColumn() {
        String row = PinningPages.stackRow(PinStack.build(aSpec(), 2, List.of(2, 4, 8)));
        assertThat(row).hasSize(20);
        assertThat(row).startsWith("  180");
    }

    @Test
    void aCoreRowBracketsTheControlWafer() {
        String row = PinningPages.stackRow(CoreStack.build(aCoreSpec(), 1, List.of(2, 6), 6));
        assertThat(Text.words(row)).containsExactly("150", "100", "[50]", "300");
    }

    @Test
    void theBodyNumbersEveryChamberDownTheSide() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        List<String> body = PinningPages.chartBody(pinning.stacks());
        assertThat(body).hasSize(6);
        assertThat(body.get(0)).startsWith(" 1");
        assertThat(body.get(5)).startsWith(" 6");
    }

    @Test
    void theCylinderSheetCarriesItsKeysAndItsCost() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        List<String> lines = PinningPages.cylinderChart(pinning, "moore hall 214");
        assertThat(lines.get(0)).isEqualTo("moore hall 214");
        assertThat(lines.get(1)).contains("key 342156");
        assertThat(lines.get(3)).contains("2 wafers");
        assertThat(lines).hasSize(4 + 6);
    }

    @Test
    void aSheetWithoutATitleStartsAtTheKeys() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        assertThat(Text.strip(PinningPages.cylinderChart(pinning).get(0))).isEqualTo("key 342156");
    }

    @Test
    void theCoreSheetCallsOutTheControlKey() {
        CorePinning core = CorePinning.pin(aCoreSpec(), List.of(b("2462462")), b("6666666"));
        List<String> lines = PinningPages.coreChart(core, "voss hall 3rd floor");
        assertThat(lines.get(0)).isEqualTo("voss hall 3rd floor");
        assertThat(Text.strip(lines.get(2))).isEqualTo("control 6666666");
        assertThat(lines.subList(3, lines.size())).allMatch(line -> line.contains("["));
    }

    @Test
    void thePartsSummaryCountsEveryBinOnce() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        List<String> summary = PinningPages.partsSummary(pinning.parts());
        assertThat(summary)
                .hasSize(pinning.parts().stream()
                        .map(PinSegment::binCode)
                        .collect(Collectors.toSet())
                        .size());
        assertThat(summary).isSorted();
    }

    @Test
    void thePartsSummaryAddsUpToThePieceCount() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        int counted = PinningPages.partsSummary(pinning.parts()).stream()
                .mapToInt(line -> Integer.parseInt(Text.words(line).get(1)))
                .sum();
        assertThat(counted).isEqualTo(pinning.pieceCount());
    }

    @Test
    void theSummaryOfNothingIsNothing() {
        assertThat(PinningPages.partsSummary(List.of())).isEmpty();
    }

    @Test
    void linesJoinIntoAPage() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        String page = Layout.asText(PinningPages.cylinderChart(pinning, "moore hall 214"));
        assertThat(page.chars().filter(at -> at == '\n').count())
                .isEqualTo(PinningPages.cylinderChart(pinning, "moore hall 214").size() - 1);
        assertThat(page).startsWith("moore hall 214");
    }
}
