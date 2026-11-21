package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsCampusTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private void aCampus() {
        BuildingRow moore = new BuildingRow("MOO", "Moore Hall");
        moore.setYearBuilt(1953);
        session.add(moore);
        session.add(new BuildingRow("VOS", "Voss Hall"));
        session.flush();
        OpeningRow entrance = new OpeningRow("MOO-101", "MOO", "1", "101", "entrance");
        entrance.setExterior(true);
        session.addAll(List.of(
                entrance,
                new OpeningRow("MOO-214", "MOO", "2", "214", "office"),
                new OpeningRow("VOS-311", "VOS", "3", "311", "office")));
        session.flush();
    }

    private static CylinderRow aCylinder() {
        return new CylinderRow("C-1001", "mortise", "LC", 6);
    }

    private static CylinderRow aCylinder(Consumer<CylinderRow> overrides) {
        CylinderRow cylinder = aCylinder();
        overrides.accept(cylinder);
        return cylinder;
    }

    private static CylinderRow aCylinder(String serial, String installedAt, String position) {
        CylinderRow cylinder = new CylinderRow(serial, "mortise", "LC", 6);
        cylinder.setInstalledAt(installedAt);
        cylinder.setPosition(position);
        return cylinder;
    }

    @Test
    void theCampusTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames()).contains("buildings", "openings", "cylinders", "access_groups");
    }

    @Test
    void aBuildingHoldsItsOpenings() {
        aCampus();
        session.expireAll();
        BuildingRow moore = session.get(BuildingRow.class, "MOO").orElseThrow();
        assertThat(moore.getOpenings().stream().map(OpeningRow::getNumber).sorted())
                .containsExactly("MOO-101", "MOO-214");
        assertThat(moore.toString()).contains("MOO");
    }

    @Test
    void aYearBeforeTheTownExistedIsRefused() {
        BuildingRow old = new BuildingRow("OLD", "Impossible Hall");
        old.setYearBuilt(1700);
        assertThatThrownBy(() -> {
                    session.add(old);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aBuildingWithNoYearIsFine() {
        session.add(new BuildingRow("NEW", "Annex"));
        session.flush();
        assertThat(session.get(BuildingRow.class, "NEW").orElseThrow().getYearBuilt())
                .isEmpty();
    }

    @Test
    void anOpeningNeedsABuildingThatExists() {
        OpeningRow row = new OpeningRow("ZZZ-1", "ZZZ", "1", "1", "office");
        assertThatThrownBy(() -> {
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRoomAppearsOnceOnAFloor() {
        aCampus();
        OpeningRow row = new OpeningRow("MOO-215", "MOO", "2", "214", "office");
        assertThatThrownBy(() -> {
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void theSameRoomNumberOnAnotherFloorIsFine() {
        aCampus();
        session.add(new OpeningRow("MOO-314", "MOO", "3", "214", "office"));
        session.flush();
        assertThat(session.get(OpeningRow.class, "MOO-314").orElseThrow().getFloor())
                .isEqualTo("3");
    }

    @Test
    void aShelvedCylinderHasNeitherDoorNorPosition() {
        aCampus();
        session.add(aCylinder());
        session.expireAll();
        CylinderRow found = session.get(CylinderRow.class, "C-1001").orElseThrow();
        assertThat(found.isInstalled()).isFalse();
        assertThat(found.getOpening()).isEmpty();
        assertThat(found.toString()).contains("C-1001");
    }

    @Test
    void anInstalledCylinderPointsAtItsDoor() {
        aCampus();
        session.add(aCylinder("C-1001", "MOO-214", "lockset"));
        session.expireAll();
        CylinderRow found = session.get(CylinderRow.class, "C-1001").orElseThrow();
        assertThat(found.isInstalled()).isTrue();
        assertThat(found.getOpening().orElseThrow().getRoom()).isEqualTo("214");
    }

    @Test
    void aDoorListsWhatIsInIt() {
        aCampus();
        session.add(aCylinder("C-1001", "MOO-214", "lockset"));
        session.add(aCylinder("C-1002", "MOO-214", "deadbolt"));
        session.expireAll();
        OpeningRow opening = session.get(OpeningRow.class, "MOO-214").orElseThrow();
        assertThat(opening.getCylinders().stream().map(CylinderRow::getSerial).sorted())
                .containsExactly("C-1001", "C-1002");
    }

    @Test
    void twoCylindersCannotShareAPosition() {
        aCampus();
        session.add(aCylinder("C-1001", "MOO-214", "lockset"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(aCylinder("C-1002", "MOO-214", "lockset"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anyNumberOfCylindersCanBeOnTheShelf() {
        aCampus();
        session.addAll(List.of(
                aCylinder(),
                new CylinderRow("C-1002", "mortise", "LC", 6),
                new CylinderRow("C-1003", "mortise", "LC", 6)));
        session.flush();
        assertThat(session.all(CylinderRow.class)).hasSize(3);
    }

    @Test
    void aCylinderCannotBeHalfInstalled() {
        aCampus();
        assertThatThrownBy(() -> {
                    session.add(aCylinder(cylinder -> cylinder.setInstalledAt("MOO-214")));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aPositionWithoutADoorIsRefused() {
        aCampus();
        assertThatThrownBy(() -> {
                    session.add(aCylinder(cylinder -> cylinder.setPosition("lockset")));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCylinderWithNoChambersIsRefused() {
        aCampus();
        assertThatThrownBy(() -> {
                    session.add(aCylinder(cylinder -> cylinder.setChambers(0)));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aCylinderInADoorNobodyHasHeardOf() {
        aCampus();
        assertThatThrownBy(() -> {
                    session.add(aCylinder("C-1001", "ZZZ-1", "lockset"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void anAccessGroupHoldsItsRequirements() {
        aCampus();
        AccessGroupRow group = new AccessGroupRow("geography faculty");
        group.setDepartment("geography");
        session.add(group);
        session.flush();
        session.addAll(List.of(AccessRequirementRow.of(group, "MOO-214"), AccessRequirementRow.of(group, "MOO-101")));
        session.flush();
        session.refresh(group);
        assertThat(group.getRequirements()).hasSize(2);
        assertThat(group.toString()).contains("geography faculty");
    }

    @Test
    void aGroupAsksForADoorOnce() {
        aCampus();
        AccessGroupRow group = new AccessGroupRow("geography faculty");
        session.add(group);
        session.flush();
        session.add(AccessRequirementRow.of(group, "MOO-214"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(AccessRequirementRow.of(group, "MOO-214"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aGroupNameIsUnique() {
        session.add(new AccessGroupRow("geography faculty"));
        session.flush();
        assertThatThrownBy(() -> {
                    session.add(new AccessGroupRow("geography faculty"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingAGroupTakesItsRequirements() {
        aCampus();
        AccessGroupRow group = new AccessGroupRow("geography faculty");
        session.add(group);
        session.flush();
        session.add(AccessRequirementRow.of(group, "MOO-214"));
        session.flush();
        session.delete(group);
        session.flush();
        assertThat(session.all(AccessRequirementRow.class)).isEmpty();
    }
}
