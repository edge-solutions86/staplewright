package io.github.rkaushikdev.staplewright.pinning;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.PinningError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.support.Charts;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class CylinderTest {

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private static PinningSpec aSpec() {
        return new PinningSpec(Charts.sixPinChart(), 150, 480);
    }

    private static PinKit aKit() {
        return PinKit.standard(
                "bench",
                IntStream.range(0, 10).map(step -> 150 + 15 * step).boxed().toList(),
                List.of(30, 45, 60, 75, 90),
                IntStream.range(0, 18).map(step -> 105 + 15 * step).boxed().toList(),
                40);
    }

    @Test
    void aCylinderForOneKeyHasNoWafers() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        assertThat(pinning.chambers()).isEqualTo(6);
        assertThat(pinning.waferCount()).isZero();
        assertThat(pinning.masteredChambers()).isEmpty();
        assertThat(pinning.pieceCount()).isEqualTo(12);
    }

    @Test
    void aMasterAndAChangeKeySplitWhereTheyDiffer() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        assertThat(pinning.masteredChambers()).containsExactly(2, 4);
        assertThat(pinning.waferCount()).isEqualTo(2);
        assertThat(pinning.stack(2).wafersMils()).containsExactly(30);
        assertThat(pinning.stack(1).wafersMils()).isEmpty();
    }

    @Test
    void aCylinderReportsTheDepthsEachChamberTakes() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        assertThat(pinning.breakDepths(2)).containsExactly(4, 6);
        assertThat(pinning.breakDepths(1)).containsExactly(3);
    }

    @Test
    void askingAboutAChamberTheCylinderDoesNotHave() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        assertThatThrownBy(() -> pinning.stack(9)).isInstanceOf(PinningError.class);
        assertThatThrownBy(() -> pinning.breakDepths(0)).isInstanceOf(PinningError.class);
    }

    @Test
    void theKeysItWasBuiltForAllOperateIt() {
        List<Bitting> keys = List.of(b("342156"), b("362356"), b("382556"));
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), keys);
        for (Bitting key : keys) {
            assertThat(pinning.operates(key)).isTrue();
        }
    }

    @Test
    void anIncidentalKeyOperatesItToo() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        assertThat(pinning.operates(b("342356"))).isTrue();
        assertThat(pinning.incidentalKeyCount()).isEqualTo(2);
        assertThat(pinning.operatingKeyCount()).isEqualTo(4);
    }

    @Test
    void aKeyThatMissesOneChamberDoesNotOperateIt() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        assertThat(pinning.operates(b("352156"))).isFalse();
    }

    @Test
    void aKeyOfTheWrongLengthDoesNotOperateIt() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        assertThat(pinning.operates(b("3421"))).isFalse();
    }

    @Test
    void aCylinderWithNoKeysIsRefused() {
        assertThatThrownBy(() -> CylinderPinning.pin(aSpec(), List.of())).isInstanceOf(PinningError.class);
    }

    @Test
    void aKeyOfTheWrongLengthCannotBePinnedFor() {
        assertThatThrownBy(() -> CylinderPinning.pin(aSpec(), List.of(b("342156"), b("3421"))))
                .isInstanceOf(PinningError.class);
    }

    @Test
    void spoolingACylinderSpoolsEveryChamber() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")), DriverProfile.SPOOL);
        List<PinSegment> drivers = pinning.parts().stream()
                .filter(part -> part.kind() == SegmentKind.DRIVER)
                .toList();
        assertThat(drivers).hasSize(6);
        assertThat(drivers).allSatisfy(part -> assertThat(part.profile()).isSameAs(DriverProfile.SPOOL));
    }

    @Test
    void thePartsListCoversEveryChamber() {
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156"), b("362356")));
        assertThat(pinning.parts()).hasSize(pinning.pieceCount());
    }

    @Test
    void buildingFromTheKitTakesTheBrassOut() {
        PinKit kit = aKit();
        int before = kit.total();
        CylinderPinning pinning = CylinderPinning.buildFromKit(aSpec(), kit, List.of(b("342156"), b("362356")));
        assertThat(kit.total()).isEqualTo(before - pinning.pieceCount());
    }

    @Test
    void aKitThatCannotFillTheCylinderSaysSo() {
        PinKit kit = PinKit.standard("thin", List.of(195), List.of(30), List.of(285), 2);
        assertThatThrownBy(() -> CylinderPinning.buildFromKit(aSpec(), kit, List.of(b("342156"))))
                .isInstanceOf(CapacityError.class);
        assertThat(kit.total()).isEqualTo(6);
    }

    @Test
    void aCylinderSaysWhetherTheKitCanBuildIt() {
        PinKit kit = aKit();
        CylinderPinning pinning = CylinderPinning.pin(aSpec(), List.of(b("342156")));
        assertThat(pinning.canBeBuiltFrom(kit)).isTrue();
        assertThat(pinning.canBeBuiltFrom(PinKit.standard("empty", List.of(), List.of(), List.of())))
                .isFalse();
    }

    @Test
    void strippingACylinderPutsTheBrassBack() {
        PinKit kit = aKit();
        int before = kit.total();
        CylinderPinning pinning = CylinderPinning.buildFromKit(aSpec(), kit, List.of(b("342156"), b("362356")));
        CylinderPinning.stripToKit(kit, pinning);
        assertThat(kit.total()).isEqualTo(before);
    }
}
