package io.github.rkaushikdev.staplewright.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ChartRow;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.NonUniqueResultException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RepositoriesTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 3, 4);
    private static final LocalDate DUE = LocalDate.of(2025, 5, 1);
    private static final LocalDate LATER = LocalDate.of(2025, 6, 2);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private ChartRow aChart() {
        return aChart("LA six pin", "Sargent");
    }

    private ChartRow aChart(String family, String manufacturer) {
        ChartRow row =
                new ChartRow(manufacturer, family, 6, List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9), 335, 15, 231, 156, 7);
        session.add(row);
        session.flush();
        return row;
    }

    private void aCampus() {
        session.addAll(List.of(new BuildingRow("MOO", "Moore Hall"), new BuildingRow("VOS", "Voss Hall")));
        session.flush();
        OpeningRow entrance = new OpeningRow("MOO-101", "MOO", "1", "101", "entrance");
        entrance.setExterior(true);
        session.addAll(List.of(
                entrance,
                new OpeningRow("MOO-214", "MOO", "2", "214", "office"),
                new OpeningRow("VOS-311", "VOS", "3", "311", "office")));
        session.flush();
    }

    private KeyholderRow aPerson(String identifier) {
        return aPerson(identifier, person -> {});
    }

    private KeyholderRow aPerson(String identifier, Consumer<KeyholderRow> overrides) {
        String name = Character.toUpperCase(identifier.charAt(0)) + identifier.substring(1);
        KeyholderRow row = new KeyholderRow(identifier, name, "faculty", "geography");
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private static IssuedKeyRow aKey(String tag, String symbol, String keyholder, int depositCents) {
        IssuedKeyRow key = new IssuedKeyRow(tag, symbol, keyholder, WHEN);
        key.setDepositCents(depositCents);
        return key;
    }

    private static KeyRequestRow aRequest(String number, String symbol, String reason) {
        KeyRequestRow request = new KeyRequestRow(number, "kmaki", symbol, "MOO", WHEN);
        request.setReason(reason);
        return request;
    }

    @Test
    void theBaseFindsAddsCountsAndDeletes() {
        ChartRepository charts = new ChartRepository(session);
        assertThat(charts.count()).isZero();
        ChartRow row = aChart();
        assertThat(charts.count()).isOne();
        assertThat(charts.exists(row.getId())).isTrue();
        assertThat(charts.get(row.getId()).getFamily()).isEqualTo("LA six pin");
        charts.delete(row);
        session.flush();
        assertThat(charts.count()).isZero();
    }

    @Test
    void askingForSomethingThatIsNotThere() {
        ChartRepository charts = new ChartRepository(session);
        assertThat(charts.find(999)).isEmpty();
        NotFoundError caught = catchThrowableOfType(NotFoundError.class, () -> charts.get(999));
        assertThat(caught.message()).contains("chart");
    }

    @Test
    void chartsComeBackByFamilyAndManufacturer() {
        aChart();
        aChart("60 five pin", "Corbin");
        ChartRepository charts = new ChartRepository(session);
        assertThat(charts.byFamily("Sargent", "LA six pin").orElseThrow().getChambers())
                .isEqualTo(6);
        assertThat(charts.byFamily("Sargent", "nothing")).isEmpty();
        assertThat(charts.forManufacturer("Corbin").stream().map(ChartRow::getFamily))
                .containsExactly("60 five pin");
    }

    @Test
    void systemsComeBackLiveAndByName() {
        ChartRow chart = aChart();
        SystemRow archived = new SystemRow("old library", chart, "Sargent LA", "L");
        archived.setArchived(true);
        session.addAll(List.of(new SystemRow("chester park", chart, "Sargent LA", "L"), archived));
        session.flush();
        SystemRepository systems = new SystemRepository(session);
        assertThat(systems.live().stream().map(SystemRow::getName)).containsExactly("chester park");
        assertThat(systems.byName("old library").orElseThrow().isArchived()).isTrue();
        assertThat(systems.byName("nothing at all")).isEmpty();
    }

    @Test
    void aSystemsEntriesAndUncutSymbols() {
        ChartRow chart = aChart();
        SystemRow system = new SystemRow("chester park", chart, "Sargent LA", "L");
        session.add(system);
        session.flush();
        session.addAll(List.of(
                ScheduleEntryRow.of(system, KeySymbol.parse("AA1"), "LC", Bitting.parse("342156")),
                ScheduleEntryRow.of(system, KeySymbol.parse("AA2"), "LC")));
        session.flush();
        SystemRepository systems = new SystemRepository(session);
        assertThat(systems.entries(system.getId())).hasSize(2);
        assertThat(systems.uncutSymbols(system.getId()).stream()
                        .map(row -> row.getSymbol().render()))
                .containsExactly("AA2");
    }

    @Test
    void openingsComeBackByBuildingAndByExposure() {
        aCampus();
        OpeningRepository openings = new OpeningRepository(session);
        assertThat(openings.inBuilding("MOO").stream().map(OpeningRow::getNumber))
                .containsExactly("MOO-101", "MOO-214");
        assertThat(openings.exterior().stream().map(OpeningRow::getNumber)).containsExactly("MOO-101");
        assertThat(openings.buildings().stream().map(BuildingRow::getCode)).containsExactly("MOO", "VOS");
    }

    @Test
    void cylindersComeBackByWhereTheyAre() {
        aCampus();
        CylinderRow installed = new CylinderRow("C-1001", "mortise", "LC", 6);
        installed.setInstalledAt("MOO-214");
        installed.setPosition("lockset");
        session.addAll(List.of(installed, new CylinderRow("C-1099", "mortise", "LC", 6)));
        session.flush();
        CylinderRepository cylinders = new CylinderRepository(session);
        assertThat(cylinders.onOpening("MOO-214").stream().map(CylinderRow::getSerial))
                .containsExactly("C-1001");
        assertThat(cylinders.onTheShelf().stream().map(CylinderRow::getSerial)).containsExactly("C-1099");
        assertThat(cylinders.unkeyed().stream().map(CylinderRow::getSerial)).containsExactly("C-1001");
        assertThat(cylinders.inGroup(1)).isEmpty();
    }

    @Test
    void keyholdersComeBackByDepartmentAndByLapse() {
        aPerson("kmaki");
        aPerson("phamm", person -> person.setDepartment("facilities"));
        aPerson("jwexler", person -> {
            person.setAffiliation("contractor");
            person.setEndsOn(LocalDate.of(2025, 1, 31));
        });
        KeyholderRepository people = new KeyholderRepository(session);
        assertThat(people.inDepartment("geography").stream().map(KeyholderRow::getIdentifier))
                .containsExactly("jwexler", "kmaki");
        assertThat(people.lapsed(WHEN).stream().map(KeyholderRow::getIdentifier))
                .containsExactly("jwexler");
        assertThat(people.lapsed(LocalDate.of(2025, 1, 1))).isEmpty();
    }

    @Test
    void keysComeBackByStateHolderSymbolAndLateness() {
        aPerson("kmaki");
        aPerson("dsalo");
        IssuedKeyRow due = aKey("41207", "AA1", "kmaki", 2500);
        due.setDueOn(DUE);
        IssuedKeyRow returned = aKey("41209", "AA", "dsalo", 5000);
        returned.setState("returned");
        returned.setClosedOn(LATER);
        session.addAll(List.of(due, aKey("41208", "AA1", "dsalo", 2500), returned));
        session.flush();
        KeyRepository keys = new KeyRepository(session);
        assertThat(keys.out().stream().map(IssuedKeyRow::getTag)).containsExactly("41207", "41208");
        assertThat(keys.heldBy("dsalo").stream().map(IssuedKeyRow::getTag)).containsExactly("41208");
        assertThat(keys.cutTo("AA1").stream().map(IssuedKeyRow::getTag)).containsExactly("41207", "41208");
        assertThat(keys.overdue(LATER).stream().map(IssuedKeyRow::getTag)).containsExactly("41207");
        assertThat(keys.overdue(WHEN)).isEmpty();
        assertThat(keys.depositsHeld()).isEqualTo(5000);
    }

    @Test
    void depositsHeldIsNothingWhenNoKeyIsOut() {
        assertThat(new KeyRepository(session).depositsHeld()).isZero();
    }

    @Test
    void requestsComeBackByStateAndByPerson() {
        aPerson("kmaki");
        KeyRequestRow submitted = aRequest("R-001", "AA1", "moved in");
        submitted.setState("submitted");
        session.addAll(List.of(submitted, aRequest("R-002", "AA", "needs the floor")));
        session.flush();
        RequestRepository requests = new RequestRepository(session);
        assertThat(requests.inState("submitted").stream().map(KeyRequestRow::getNumber))
                .containsExactly("R-001");
        assertThat(requests.forKeyholder("kmaki")).hasSize(2);
    }

    @Test
    void incidentsComeBackByDateAndSeverity() {
        IncidentRow serious = new IncidentRow("I-2", "41209", "AA", "lost", LATER);
        serious.setSeverity("serious");
        session.addAll(List.of(new IncidentRow("I-1", "41207", "AA1", "lost", WHEN), serious));
        session.flush();
        IncidentRepository incidents = new IncidentRepository(session);
        assertThat(incidents.since(LATER).stream().map(IncidentRow::getNumber)).containsExactly("I-2");
        assertThat(incidents.bySeverity("serious").stream().map(IncidentRow::getNumber))
                .containsExactly("I-2");
    }

    @Test
    void workOrdersComeBackOpenByPersonAndByDay() {
        WorkOrderRow scheduled = new WorkOrderRow("W-1");
        scheduled.setState("scheduled");
        scheduled.setTechnician("jkoivu");
        scheduled.setScheduledFor(LATER);
        WorkOrderRow done = new WorkOrderRow("W-2");
        done.setState("done");
        done.setTechnician("jkoivu");
        done.setClosedOn(LATER);
        session.addAll(List.of(scheduled, done));
        session.flush();
        WorkOrderRepository orders = new WorkOrderRepository(session);
        assertThat(orders.openOrders().stream().map(WorkOrderRow::getNumber)).containsExactly("W-1");
        assertThat(orders.forTechnician("jkoivu")).hasSize(2);
        assertThat(orders.dueOn(LATER).stream().map(WorkOrderRow::getNumber)).containsExactly("W-1");
    }

    @Test
    void retiredCutsComeBackByCutAndByPermanence() {
        session.addAll(List.of(
                new RetiredBittingRow(Bitting.parse("342156"), "compromised", WHEN),
                new RetiredBittingRow(Bitting.parse("362356"), "superseded", WHEN)));
        session.flush();
        QuarantineRepository quarantine = new QuarantineRepository(session);
        assertThat(quarantine.byBitting("342156").orElseThrow().getReason()).isEqualTo("compromised");
        assertThat(quarantine.byBitting("999999")).isEmpty();
        assertThat(quarantine.permanent().stream().map(row -> row.getBitting().render()))
                .containsExactly("342156");
    }

    @Test
    void addAllPutsABatchInAtOnce() {
        ChartRepository charts = new ChartRepository(session);
        charts.addAll(List.of());
        assertThat(charts.count()).isZero();
    }

    @Test
    void theUsualOrderIsTheOneTheRepositoryNames() {
        aChart("60 five pin", "Sargent");
        aChart("LA six pin", "Sargent");
        assertThat(new ChartRepository(session).all().stream().map(ChartRow::getFamily))
                .containsExactly("60 five pin", "LA six pin");
    }

    @Test
    void aCallerCanSortItSomeOtherWay() {
        aChart("60 five pin", "Sargent");
        aChart("LA six pin", "Sargent");
        ChartRepository charts = new ChartRepository(session);
        List<ChartRow> found = charts.sortedBy(Sort.descending("family"));
        assertThat(found.stream().map(ChartRow::getFamily)).containsExactly("LA six pin", "60 five pin");
    }

    @Test
    void narrowingAndSortingHappenTogether() {
        aChart("60 five pin", "Corbin");
        aChart("LA six pin", "Sargent");
        aChart("A2 seven pin", "Sargent");
        List<ChartRow> found = new ChartRepository(session).rows(Where.equal("manufacturer", "Sargent"));
        assertThat(found.stream().map(ChartRow::getFamily)).containsExactly("A2 seven pin", "LA six pin");
    }

    @Test
    void askingForOneRowThatIsNotThereGivesNothing() {
        assertThat(new ChartRepository(session).one(Where.equal("family", "nothing")))
                .isEmpty();
    }

    @Test
    void askingForOneRowWhenThereAreTwoIsAnError() {
        aChart("LA six pin", "Sargent");
        aChart("LA six pin", "Corbin");
        ChartRepository charts = new ChartRepository(session);
        assertThatThrownBy(() -> charts.one(Where.equal("family", "LA six pin")))
                .isInstanceOf(NonUniqueResultException.class);
    }

    @Test
    void aRepositoryCanReachAnotherTable() {
        session.addAll(List.of(new BuildingRow("VOS", "vosberg hall"), new BuildingRow("MOO", "moore")));
        session.flush();
        List<BuildingRow> found = new OpeningRepository(session).buildings();
        assertThat(found.stream().map(BuildingRow::getCode)).containsExactly("MOO", "VOS");
    }

    @Test
    void anUnsortedReachAcrossGivesBackWhatItFinds() {
        ChartRepository charts = new ChartRepository(session);
        assertThat(charts.rowsOf(BuildingRow.class)).isEmpty();
    }

    @Test
    void requiringARowThatIsThereHandsItBack() {
        aChart("LA six pin", "Sargent");
        ChartRepository charts = new ChartRepository(session);
        ChartRow row = charts.rows().get(0);
        assertThat(charts.require(row.getId())).isSameAs(row);
    }

    @Test
    void requiringARowThatIsNotThereSaysTheNoun() {
        NotFoundError caught =
                catchThrowableOfType(NotFoundError.class, () -> new ChartRepository(session).require(99));
        assertThat(caught.getMessage()).startsWith("no such chart");
        assertThat(caught.facts().get("key")).isEqualTo("99");
    }

    @Test
    void aCallerCanNameTheRefusalItsOwnWay() {
        NotFoundError caught = catchThrowableOfType(NotFoundError.class, () -> new ChartRepository(session)
                .require(99, "no such family", Facts.of("family", "Everest")));
        assertThat(caught.facts()).isEqualTo(Map.of("family", "Everest"));
        assertThat(caught.getMessage()).contains("no such family");
    }

    @Test
    void aCallerCanChangeOnlyTheMessage() {
        NotFoundError caught = catchThrowableOfType(
                NotFoundError.class, () -> new ChartRepository(session).require(99, "nobody stocks that"));
        assertThat(caught.facts().get("key")).isEqualTo("99");
    }

    @Test
    void getStillWorksTheWayItDid() {
        assertThatThrownBy(() -> new ChartRepository(session).get(99)).isInstanceOf(NotFoundError.class);
    }
}
