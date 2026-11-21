package io.github.rkaushikdev.staplewright.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeywaySectionRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.spec.Exposure;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.KeywaySection;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ServiceKeywaysTest {

    private static final LocalDate TODAY = LocalDate.of(2025, 6, 30);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    static KeywayFamily aFamily() {
        return aFamily("everest 29");
    }

    static KeywayFamily aFamily(String name) {
        KeywayFamily family =
                new KeywayFamily(name, new KeywaySection("C", "Schlage", true, Optional.of("2031-04-01")));
        family.add(new KeywaySection("CE", "Schlage", true, Optional.of("2027-02-14")), "C");
        family.add(new KeywaySection("CF", "Schlage", true, Optional.of("2023-11-30")), "C");
        family.add(new KeywaySection("CEA", "Schlage"), "CE");
        return family;
    }

    private ChartRow aChart() {
        List<ChartRow> held = session.all(ChartRow.class);
        if (!held.isEmpty()) {
            return held.get(0);
        }
        ChartRow row = new ChartRow(
                "Schlage", "C keyway six pin", 6, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 335, 15, 231, 156, 7);
        session.add(row);
        session.flush();
        return row;
    }

    private SystemRow aSystem(String name, String section) {
        return aSystem(name, section, "everest 29");
    }

    private SystemRow aSystem(String name, String section, String family) {
        SystemRow row = new SystemRow(name, aChart(), family, section);
        session.add(row);
        session.flush();
        return row;
    }

    private KeywayService stored() {
        return stored("everest 29");
    }

    private KeywayService stored(String name) {
        KeywayService service = new KeywayService(session);
        service.store(aFamily(name));
        return service;
    }

    @Test
    void aFamilyGoesInAndComesBackAsATree() {
        KeywayFamily family = stored().family("everest 29");
        assertThat(family.codes()).containsExactlyInAnyOrder("C", "CE", "CF", "CEA");
        assertThat(family.parentOf("CEA").orElseThrow().code()).isEqualTo("CE");
    }

    @Test
    void thePatentDatesSurviveTheRoundTrip() {
        assertThat(stored().family("everest 29").section("CE").patentExpires()).contains("2027-02-14");
    }

    @Test
    void aSectionWithNoDateComesBackWithoutOne() {
        assertThat(stored().family("everest 29").section("CEA").patentExpires()).isEmpty();
    }

    @Test
    void storingAFamilyTwiceIsRefused() {
        KeywayService service = stored();
        assertThatThrownBy(() -> service.store(aFamily())).isInstanceOf(ConflictError.class);
    }

    @Test
    void aFamilyNobodyStoredIsA404() {
        assertThatThrownBy(() -> new KeywayService(session).family("nothing")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theFamiliesComeBackInNameOrder() {
        KeywayService service = stored();
        service.store(aFamily("primus"));
        assertThat(service.families()).containsExactly("everest 29", "primus");
    }

    @Test
    void theExposuresComeBackSoonestFirst() {
        assertThat(stored().exposures("everest 29").stream()
                        .map(Exposure::section)
                        .limit(3))
                .containsExactly("CF", "CE", "C");
    }

    @Test
    void theOpenSectionsAreTheExpiredAndTheUnrestricted() {
        assertThat(stored().openToday("everest 29", TODAY)).containsExactly("CF", "CEA");
    }

    @Test
    void theClosingSectionsComeOffTheHorizon() {
        KeywayService service = stored();
        assertThat(service.closing("everest 29", TODAY)).containsExactly("CE");
        assertThat(service.closing("everest 29", TODAY, 10)).containsExactly("CE", "C");
    }

    @Test
    void theNextOneToGoIsNamed() {
        assertThat(stored().nextToGo("everest 29", TODAY).orElseThrow().section())
                .isEqualTo("CE");
    }

    @Test
    void theFamilyReportReadsTopToBottom() {
        assertThat(stored().report("everest 29", TODAY).get(0)).startsWith("everest 29 patent position");
    }

    @Test
    void theSystemsOnASectionComeBackInNameOrder() {
        KeywayService service = stored();
        aSystem("voss hall", "CF");
        aSystem("atrium", "CF");
        assertThat(service.systemsOn("everest 29", "CF")).containsExactly("atrium", "voss hall");
    }

    @Test
    void anArchivedSystemIsNotOnAnything() {
        KeywayService service = stored();
        aSystem("voss hall", "CF").setArchived(true);
        session.flush();
        assertThat(service.systemsOn("everest 29", "CF")).isEmpty();
    }

    @Test
    void theSectionsInUseAreTheOnesSomethingSitsOn() {
        KeywayService service = stored();
        aSystem("voss hall", "CF");
        aSystem("atrium", "C");
        assertThat(service.sectionsInUse("everest 29")).containsExactly("C", "CF");
    }

    @Test
    void aSectionThatWentOpenWithNothingOnItIsNotAtRisk() {
        assertThat(stored().atRisk("everest 29", TODAY)).isEmpty();
    }

    @Test
    void aSectionThatWentOpenWithASystemOnItIsAtRisk() {
        KeywayService service = stored();
        aSystem("voss hall", "CF");
        List<RiskLine> found = service.atRisk("everest 29", TODAY);
        assertThat(found.stream().map(RiskLine::section)).containsExactly("CF");
        assertThat(found.get(0).systems()).containsExactly("voss hall");
    }

    @Test
    void aRiskLineSaysWhenThePatentWent() {
        KeywayService service = stored();
        aSystem("voss hall", "CF");
        assertThat(service.atRisk("everest 29", TODAY).get(0).describe(TODAY))
                .isEqualTo("CF patent ran out 2023-11-30 carrying voss hall");
    }

    @Test
    void aSectionWithYearsLeftPassesTheCheck() {
        KeywayService service = stored();
        assertThatCode(() -> service.checkSection("everest 29", "C", TODAY)).doesNotThrowAnyException();
    }

    @Test
    void aSectionThatGoesInsideTheHorizonIsRefused() {
        KeywayService service = stored();
        ConflictError caught =
                catchThrowableOfType(ConflictError.class, () -> service.checkSection("everest 29", "CE", TODAY));
        assertThat(caught.facts()).containsEntry("expires", "2027-02-14");
    }

    @Test
    void somebodyWhoKnowsCanKeyOnItAnyway() {
        KeywayService service = stored();
        assertThatCode(() -> service.checkSection("everest 29", "CE", TODAY, KeywayService.PLANNING_YEARS, true))
                .doesNotThrowAnyException();
    }

    @Test
    void aSectionNobodyHasIsStillAMissingSection() {
        KeywayService service = stored();
        assertThatThrownBy(() -> service.checkSection("everest 29", "ZZ", TODAY))
                .isInstanceOf(KeywayError.class);
    }

    @Test
    void theShopWideReportSaysWhenNothingIsWrong() {
        assertThat(stored().riskReport(TODAY).get(1)).isEqualTo("nothing the shop keys on has gone open");
    }

    @Test
    void theShopWideReportCountsAcrossFamilies() {
        KeywayService service = stored();
        service.store(aFamily("primus"));
        aSystem("voss hall", "CF");
        aSystem("shops", "CF", "primus");
        assertThat(service.riskReport(TODAY).get(1)).isEqualTo("2 sections in use have gone open");
    }

    @Test
    void theSectionRepositoryListsAFamilyInCodeOrder() {
        stored();
        assertThat(new SectionRepository(session)
                        .inFamily("everest 29").stream().map(KeywaySectionRow::getCode))
                .containsExactly("C", "CE", "CEA", "CF");
    }
}
