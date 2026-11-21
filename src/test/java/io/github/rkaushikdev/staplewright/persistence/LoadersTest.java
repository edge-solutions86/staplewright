package io.github.rkaushikdev.staplewright.persistence;

import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadCoverage;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadDeposits;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadDirectory;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadFamily;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadGroupNames;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadGroups;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadLedger;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadOpenings;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadQuarantine;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadRegister;
import static io.github.rkaushikdev.staplewright.persistence.Loaders.loadSchedule;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.KeywayError;
import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.models.RetiredBittingRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import io.github.rkaushikdev.staplewright.rekey.Quarantine;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadersTest {

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

    @Test
    void theOpeningsComeBackWhole() {
        DbCampus.build(session);
        OpeningSchedule schedule = loadOpenings(session);
        assertThat(schedule.size()).isEqualTo(11);
        assertThat(schedule.opening("MOO-101").exterior()).isTrue();
    }

    @Test
    void theRegisterPutsEveryCylinderBack() {
        DbCampus.build(session);
        HardwareRegister register = loadRegister(session);
        assertThat(register.at("MOO-214", Position.LOCKSET).orElseThrow().serial())
                .isEqualTo("C-1001");
        assertThat(register.onTheShelf().stream().map(Cylinder::serial).toList())
                .containsExactly("C-1099");
    }

    @Test
    void theGroupNamesReachTheCylinders() {
        DbCampus.build(session);
        HardwareRegister register = loadRegister(session);
        assertThat(register.cylinder("C-1001").group()).isEqualTo("moore offices");
    }

    @Test
    void theCoverageAnswersWhatAKeyOpens() {
        DbCampus.build(session);
        CoverageMap coverage = loadCoverage(session);
        assertThat(coverage.openingsFor(sym("AA1"))).containsExactly("MOO-214", "MOO-216");
        assertThat(coverage.openingsFor(sym("GGM"))).hasSize(6);
    }

    @Test
    void theGroupsComeBackByName() {
        DbCampus.build(session);
        Map<String, KeyingGroup> groups = loadGroups(session);
        assertThat(groups).containsKey("moore offices");
        assertThat(groups.get("moore offices").size()).isEqualTo(4);
    }

    @Test
    void theGroupNamesComeBackById() {
        DbCampus.build(session);
        Map<Integer, String> names = loadGroupNames(session);
        assertThat(names.values().stream().sorted().toList())
                .isEqualTo(loadGroups(session).keySet().stream().sorted().toList());
    }

    @Test
    void theDirectoryComesBackWhole() {
        DbCampus.build(session);
        Directory directory = loadDirectory(session);
        assertThat(directory.size()).isEqualTo(6);
        assertThat(directory.mayAuthorize("rvirta", "geography", "MOO", TODAY)).isTrue();
    }

    @Test
    void theLedgerComesBackWithItsKeysOut() {
        DbCampus.build(session);
        KeyLedger ledger = loadLedger(session);
        assertThat(ledger.out().stream().map(IssuedKey::tag).toList())
                .containsExactly("41207", "41208", "41209", "41210", "41211");
    }

    @Test
    void theDepositBookComesBackEmptyWhenNothingWasCharged() {
        DbCampus.build(session);
        assertThat(loadDeposits(session).totalHeld()).isZero();
    }

    @Test
    void theScheduleComesBackWithItsCuts() {
        SystemRow system = DbCampus.build(session);
        KeyingSchedule schedule = loadSchedule(session, system);
        assertThat(schedule.bittingOf(sym("AA1")).orElseThrow().render()).isEqualTo("620446");
        assertThat(schedule.size()).isEqualTo(DbCampus.CUTS.size());
    }

    @Test
    void theFamilyComesBackAsATree() {
        DbCampus.build(session);
        KeywayFamily family = loadFamily(session, "Sargent LA");
        assertThat(family.enters("L", "LC")).isTrue();
        assertThat(family.leaves()).containsExactly("LC", "LD", "LE");
    }

    @Test
    void aFamilyNobodyStoredHasNoRoot() {
        DbCampus.build(session);
        assertThatThrownBy(() -> loadFamily(session, "Best A2")).isInstanceOf(KeywayError.class);
    }

    @Test
    void theQuarantineComesBackWithItsReasons() {
        DbCampus.build(session);
        session.add(new RetiredBittingRow(Bitting.parse("342156"), "compromised", TODAY));
        session.flush();
        Quarantine hold = loadQuarantine(session);
        assertThat(hold.size()).isEqualTo(1);
        assertThat(hold.permanent().get(0).bitting().render()).isEqualTo("342156");
    }

    @Test
    void anEmptyQuarantineHoldsNothing() {
        assertThat(loadQuarantine(session).size()).isZero();
    }

    @Test
    void anEmptyDatabaseLoadsEmptyObjects() {
        assertThat(loadOpenings(session).size()).isZero();
        assertThat(loadRegister(session).size()).isZero();
        assertThat(loadDirectory(session).size()).isZero();
        assertThat(loadLedger(session).size()).isZero();
    }
}
