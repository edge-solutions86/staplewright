package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.HierarchyError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceSystemsTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 4, 1);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static Bitting b(String text) {
        return Bitting.parse(text);
    }

    private SystemService aService() {
        ChartRow chart = DbCampus.chartAndFamily(session);
        SystemService service = new SystemService(session);
        service.openSystem("chester park", chart.getId(), "Sargent LA", "L", 2, Optional.of(WHEN));
        return service;
    }

    private SystemService aDrawnSystem() {
        SystemService service = aService();
        service.draw("chester park", sym("A"), "LA");
        service.draw("chester park", sym("AA"), "LC");
        service.draw("chester park", sym("AA1"));
        service.draw("chester park", sym("AA2"));
        return service;
    }

    @Test
    void aNewSystemHoldsOnlyTheTopKey() {
        KeyingSchedule schedule = aService().schedule("chester park");
        assertThat(schedule.size()).isEqualTo(1);
        assertThat(schedule.entry(sym("GGM")).section()).isEqualTo("L");
    }

    @Test
    void aSystemCannotBeOpenedTwice() {
        SystemService service = aService();
        int chart = service.system("chester park").getChartId();
        assertThatThrownBy(() -> service.openSystem("chester park", chart, "Sargent LA", "L"))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void askingForASystemThatIsNotThere() {
        SystemService service = aService();
        assertThatThrownBy(() -> service.system("nothing at all")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void drawingASymbolPutsItOnTheSchedule() {
        KeyingSchedule schedule = aDrawnSystem().schedule("chester park");
        assertThat(schedule.symbols().stream().map(KeySymbol::render)).containsExactly("GGM", "A", "AA", "AA1", "AA2");
        assertThat(schedule.entry(sym("AA1")).section()).isEqualTo("LC");
    }

    @Test
    void aSymbolWithNoParentIsRefused() {
        SystemService service = aService();
        assertThatThrownBy(() -> service.draw("chester park", sym("AA1"))).isInstanceOf(HierarchyError.class);
    }

    @Test
    void aSectionTheKeyAboveCannotEnterIsRefused() {
        SystemService service = aService();
        service.draw("chester park", sym("A"), "LA");
        assertThatThrownBy(() -> service.draw("chester park", sym("AA"), "LE")).isInstanceOf(HierarchyError.class);
    }

    @Test
    void cuttingASymbolWritesTheCut() {
        SystemService service = aDrawnSystem();
        service.cut("chester park", sym("AA1"), b("620446"));
        assertThat(service.schedule("chester park").bittingOf(sym("AA1"))).contains(b("620446"));
    }

    @Test
    void aCutTheRulesRefuseNeverLands() {
        SystemService service = aDrawnSystem();
        assertThatThrownBy(() -> service.cut("chester park", sym("AA1"), b("192156")))
                .isInstanceOf(ConflictError.class);
        assertThat(service.schedule("chester park").bittingOf(sym("AA1"))).isEmpty();
    }

    @Test
    void aCutAnotherSymbolAlreadyHasIsRefused() {
        SystemService service = aDrawnSystem();
        service.cut("chester park", sym("AA1"), b("620446"));
        assertThatThrownBy(() -> service.cut("chester park", sym("AA2"), b("620446")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void aQuarantinedCutIsRefusedHoweverGoodItLooks() {
        SystemService service = aDrawnSystem();
        session.add(new RetiredBittingRow(b("620446"), "compromised", WHEN));
        session.flush();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.cut("chester park", sym("AA1"), b("620446")));
        assertThat(caught.facts()).containsEntry("reason", "compromised");
    }

    @Test
    void aCutRetiredForASofterReasonIsStillRefused() {
        SystemService service = aDrawnSystem();
        session.add(new RetiredBittingRow(b("620446"), "superseded", WHEN));
        session.flush();
        assertThatThrownBy(() -> service.cut("chester park", sym("AA1"), b("620446")))
                .isInstanceOf(ConflictError.class);
    }

    @Test
    void uncuttingPutsTheSymbolBackOnTheOpenList() {
        SystemService service = aDrawnSystem();
        service.cut("chester park", sym("AA1"), b("620446"));
        service.uncut("chester park", sym("AA1"));
        assertThat(service.schedule("chester park").bittingOf(sym("AA1"))).isEmpty();
    }

    @Test
    void cuttingASymbolThatWasNeverDrawn() {
        SystemService service = aService();
        assertThatThrownBy(() -> service.cut("chester park", sym("AA1"), b("620446")))
                .isInstanceOf(NotFoundError.class);
    }

    @Test
    void theReviewFindsNothingWrongWithACleanSystem() {
        SystemService service = aDrawnSystem();
        service.cut("chester park", sym("A"), b("642446"));
        service.cut("chester park", sym("AA"), b("622446"));
        service.cut("chester park", sym("AA1"), b("620446"));
        service.cut("chester park", sym("AA2"), b("624446"));
        service.cut("chester park", sym("GGM"), b("442446"));
        assertThat(service.review("chester park")).isEmpty();
    }

    @Test
    void theReviewReportsTheUncutSymbols() {
        assertThat(aDrawnSystem().review("chester park").stream()
                        .map(Finding::code)
                        .collect(Collectors.toSet()))
                .containsExactly("uncut");
    }

    @Test
    void theFamilyComesBackAsATree() {
        KeywayFamily family = aService().family("Sargent LA");
        assertThat(family.enters("L", "LC")).isTrue();
        assertThat(family.leaves()).containsExactly("LC", "LD", "LE");
    }

    @Test
    void askingForAFamilyNobodyStored() {
        SystemService service = aService();
        assertThatThrownBy(() -> service.family("Best A2")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void aSystemCanBeArchivedOnce() {
        SystemService service = aService();
        assertThat(service.archive("chester park").isArchived()).isTrue();
        assertThatThrownBy(() -> service.archive("chester park")).isInstanceOf(ConflictError.class);
    }

    @Test
    void aSystemWithNoGroupsReportsNone() {
        assertThat(aService().groups("chester park")).isEmpty();
    }
}
