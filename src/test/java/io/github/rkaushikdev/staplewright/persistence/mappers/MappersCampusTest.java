package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.accessGroupFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.accessGroupToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.buildingFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.buildingToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.cylinderFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.cylinderToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.matrixFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.matrixToRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.openingFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.openingToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.registerFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.scheduleFromRows;
import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static io.github.rkaushikdev.staplewright.support.Keyed.aRegister;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.campus.AccessGroup;
import io.github.rkaushikdev.staplewright.campus.AccessMatrix;
import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.Cylinder;
import io.github.rkaushikdev.staplewright.campus.CylinderFormat;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.HardwareRegister;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;
import io.github.rkaushikdev.staplewright.campus.Position;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.CampusMappers.GroupWants;
import io.github.rkaushikdev.staplewright.persistence.models.AccessGroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.AccessRequirementRow;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MappersCampusTest {

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private void storeCampus() {
        OpeningSchedule schedule = aCampus();
        for (Building building : schedule.buildings()) {
            session.add(buildingToRow(building));
        }
        session.flush();
        for (Opening opening : schedule) {
            session.add(openingToRow(opening));
        }
        session.flush();
    }

    @Test
    void aBuildingSurvivesARoundTrip() {
        Building building = new Building("MOO", "Moore Hall", 1953);
        assertThat(buildingFromRow(buildingToRow(building))).isEqualTo(building);
    }

    @Test
    void aBuildingWithNoYearSurvives() {
        Building building = new Building("NEW", "Annex");
        assertThat(buildingFromRow(buildingToRow(building)).yearBuilt()).isEmpty();
    }

    @Test
    void anOpeningSurvivesARoundTrip() {
        Opening opening = aCampus().opening("MOO-101");
        assertThat(openingFromRow(openingToRow(opening))).isEqualTo(opening);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MOO-101", "MOO-218", "MOO-260", "HEI-G12"})
    void everyKindOfOpeningSurvives(String number) {
        Opening opening = aCampus().opening(number);
        assertThat(openingFromRow(openingToRow(opening))).isEqualTo(opening);
    }

    @Test
    void aWholeCampusComesBack() {
        storeCampus();
        List<BuildingRow> buildings = session.all(BuildingRow.class);
        List<OpeningRow> openings = session.all(OpeningRow.class);
        OpeningSchedule back = scheduleFromRows(buildings, openings);
        assertThat(back.size()).isEqualTo(aCampus().size());
        assertThat(back.opening("MOO-101").exterior()).isTrue();
        assertThat(back.opening("MOO-260").function()).isSameAs(DoorFunction.PRIVACY);
    }

    @Test
    void aFunctionNobodyHasHeardOfIsRefused() {
        OpeningRow row = openingToRow(aCampus().opening("MOO-214"));
        row.setFunction("hovercraft");
        assertThatThrownBy(() -> openingFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aShelvedCylinderSurvivesARoundTrip() {
        Cylinder cylinder = new Cylinder("C-1001", CylinderFormat.MORTISE, "LC", 6, "Sargent");
        assertThat(cylinderFromRow(cylinderToRow(cylinder))).isEqualTo(cylinder);
    }

    @Test
    void anInstalledCylinderSurvivesARoundTrip() {
        HardwareRegister register = aRegister();
        Cylinder cylinder = register.cylinder("C-1001");
        assertThat(cylinderFromRow(cylinderToRow(cylinder)).installedAt()).contains("MOO-214");
        assertThat(cylinderFromRow(cylinderToRow(cylinder)).position()).containsSame(Position.LOCKSET);
    }

    @Test
    void aFormatNobodyHasHeardOfIsRefused() {
        CylinderRow row = cylinderToRow(new Cylinder("C-1", CylinderFormat.MORTISE, "LC", 6));
        row.setFormat("wafer tumbler");
        assertThatThrownBy(() -> cylinderFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aPositionNobodyHasHeardOfIsRefused() {
        HardwareRegister register = aRegister();
        CylinderRow row = cylinderToRow(register.cylinder("C-1001"));
        row.setPosition("somewhere");
        assertThatThrownBy(() -> cylinderFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aWholeRegisterComesBack() {
        storeCampus();
        HardwareRegister register = aRegister();
        for (Cylinder cylinder : register) {
            session.add(cylinderToRow(cylinder));
        }
        session.flush();
        List<CylinderRow> rows = session.all(CylinderRow.class);
        List<BuildingRow> buildings = session.all(BuildingRow.class);
        List<OpeningRow> openings = session.all(OpeningRow.class);
        HardwareRegister back = registerFromRows(scheduleFromRows(buildings, openings), rows);
        assertThat(back.size()).isEqualTo(register.size());
        assertThat(back.at("MOO-214", Position.LOCKSET).orElseThrow().serial()).isEqualTo("C-1001");
        assertThat(back.onTheShelf().stream().map(Cylinder::serial).toList()).containsExactly("C-1099");
    }

    @Test
    void theGroupNamesComeBackWhenTheyAreGiven() {
        HardwareRegister register = aRegister();
        List<CylinderRow> rows =
                List.of(cylinderToRow(register.cylinder("C-1001"), session.reference(GroupRow.class, 7)));
        HardwareRegister back = registerFromRows(aCampus(), rows, Map.of(7, "moore offices"));
        assertThat(back.cylinder("C-1001").group()).isEqualTo("moore offices");
    }

    @Test
    void aGroupNobodyNamedLeavesTheCylinderUnkeyed() {
        HardwareRegister register = aRegister();
        List<CylinderRow> rows =
                List.of(cylinderToRow(register.cylinder("C-1001"), session.reference(GroupRow.class, 99)));
        HardwareRegister back = registerFromRows(aCampus(), rows, Map.of());
        assertThat(back.cylinder("C-1001").group()).isEmpty();
    }

    @Test
    void anAccessGroupSurvivesARoundTrip() {
        AccessGroup group = new AccessGroup("geography faculty", "everybody who teaches", "geography");
        assertThat(accessGroupFromRow(accessGroupToRow(group))).isEqualTo(group);
    }

    @Test
    void aMatrixSurvivesTheDatabase() {
        storeCampus();
        AccessMatrix matrix = new AccessMatrix(aCampus());
        matrix.addGroup(new AccessGroup("geography faculty", "", "geography"));
        matrix.requireAll("geography faculty", List.of("MOO-214", "MOO-216"));
        for (GroupWants wants : matrixToRows(matrix)) {
            session.add(wants.group());
            session.flush();
            for (String number : wants.openings()) {
                session.add(AccessRequirementRow.of(wants.group(), number));
            }
        }
        session.flush();
        List<AccessGroupRow> groups = session.all(AccessGroupRow.class);
        List<AccessRequirementRow> wanted = session.all(AccessRequirementRow.class);
        AccessMatrix back = matrixFromRows(aCampus(), groups, wanted);
        assertThat(back.openingsFor("geography faculty")).containsExactly("MOO-214", "MOO-216");
    }

    @Test
    void aGroupWithNoDoorsSurvives() {
        storeCampus();
        AccessMatrix matrix = new AccessMatrix(aCampus());
        matrix.addGroup(new AccessGroup("new hires"));
        List<GroupWants> rows = matrixToRows(matrix);
        assertThat(rows.get(0).openings()).isEmpty();
    }
}
