package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.pinning.CylinderPinning;
import io.github.rkaushikdev.staplewright.pinning.DriverProfile;
import io.github.rkaushikdev.staplewright.pinning.PinKit;
import io.github.rkaushikdev.staplewright.pinning.PinSegment;
import io.github.rkaushikdev.staplewright.pinning.PinningSpec;
import io.github.rkaushikdev.staplewright.pinning.SegmentKind;
import io.github.rkaushikdev.staplewright.support.Charts;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServicePinningTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
        DbCampus.build(session);
    }

    @AfterEach
    void close() {
        session.close();
    }

    private PinningService aService() {
        return new PinningService(session, new PinningSpec(Charts.sixPinChart(), 150, 560));
    }

    static PinKit aKit() {
        return PinKit.standard(
                "bench",
                IntStream.range(0, 10).map(step -> 150 + 15 * step).boxed().toList(),
                IntStream.range(1, 10).map(step -> 15 * step).boxed().toList(),
                IntStream.range(0, 80).map(step -> 100 + 5 * step).boxed().toList(),
                40);
    }

    @Test
    void theCutsBehindAGroupComeBackInOrder() {
        List<Bitting> found = aService().bittingsFor("chester park", "moore offices");
        assertThat(found.stream().map(Bitting::render)).containsExactly("442446", "642446", "622446", "620446");
    }

    @Test
    void aGroupWithAnUncutSymbolIsRefused() {
        session.first(
                        "select e from ScheduleEntryRow e where e.symbol = ?1",
                        ScheduleEntryRow.class,
                        KeySymbol.parse("AA1"))
                .orElseThrow()
                .setBitting(null);
        session.flush();
        ConflictError caught = catchThrowableOfType(
                ConflictError.class, () -> aService().bittingsFor("chester park", "moore offices"));
        assertThat(caught.facts()).containsEntry("symbol", "AA1");
    }

    @Test
    void aGroupNobodyDefined() {
        assertThatThrownBy(() -> aService().bittingsFor("chester park", "nothing at all"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSystemNobodyOpened() {
        assertThatThrownBy(() -> aService().bittingsFor("nowhere", "moore offices"))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void pinningAGroupGivesAStackAChamber() {
        CylinderPinning pinning = aService().pin("chester park", "moore offices");
        assertThat(pinning.chambers()).isEqualTo(6);
        assertThat(pinning.waferCount()).isPositive();
    }

    @Test
    void theWafersLandWhereTheKeysDiffer() {
        assertThat(aService().pin("chester park", "moore offices").masteredChambers())
                .containsExactly(1, 2, 3);
    }

    @Test
    void aGroupOfOneKeyNeedsNoWafers() {
        assertThat(aService().pin("chester park", "moore entrance").waferCount())
                .isEqualTo(1);
    }

    @Test
    void spoolingReachesEveryDriver() {
        CylinderPinning pinning = aService().pin("chester park", "moore offices", DriverProfile.SPOOL);
        List<PinSegment> drivers = pinning.parts().stream()
                .filter(part -> part.kind() == SegmentKind.DRIVER)
                .toList();
        assertThat(drivers).isNotEmpty().allMatch(part -> part.profile() == DriverProfile.SPOOL);
    }

    @Test
    void theChartCarriesTheGroupAndTheSerial() {
        List<String> lines = aService().chartFor("chester park", "moore offices", "C-1001");
        assertThat(lines.get(0)).isEqualTo("moore offices  C-1001");
        assertThat(lines).hasSize(6 + 6);
    }

    @Test
    void aChartWithoutASerialStillNamesTheGroup() {
        assertThat(aService().chartFor("chester park", "moore offices").get(0)).isEqualTo("moore offices");
    }

    @Test
    void drawingTakesTheBrassOutOfTheKit() {
        PinKit kit = aKit();
        int before = kit.total();
        PinningService.Drawn drawn = aService().drawFrom(kit, "chester park", "moore offices");
        assertThat(kit.total()).isEqualTo(before - drawn.pinning().pieceCount());
        assertThat(drawn.summary()).isNotEmpty();
    }

    @Test
    void aKitThatCannotFillItTakesNothing() {
        PinKit kit = PinKit.standard("thin", List.of(195), List.of(30), List.of(285), 1);
        int before = kit.total();
        assertThatThrownBy(() -> aService().drawFrom(kit, "chester park", "moore offices"))
                .isInstanceOf(CapacityError.class);
        assertThat(kit.total()).isEqualTo(before);
    }

    @Test
    void theCylindersInAGroupComeBack() {
        assertThat(aService().cylindersFor("moore offices").stream().map(CylinderRow::getSerial))
                .containsExactly("C-1001", "C-1002");
    }

    @Test
    void aGroupWithNoCylindersComesBackEmpty() {
        assertThat(aService().cylindersFor("shops")).isNotEmpty();
    }

    @Test
    void theSymbolsComeBackInScheduleOrder() {
        assertThat(aService().symbolsFor("moore offices").stream().map(KeySymbol::render))
                .containsExactly("GGM", "A", "AA", "AA1");
    }
}
