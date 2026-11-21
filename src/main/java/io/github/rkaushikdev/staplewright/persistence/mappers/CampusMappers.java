package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.Facts;
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
import io.github.rkaushikdev.staplewright.persistence.models.AccessGroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.AccessRequirementRow;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.CylinderRow;
import io.github.rkaushikdev.staplewright.persistence.models.GroupRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Buildings, openings, cylinders and the access matrix, both directions.
 *
 * <p>The choices are the only thing worth watching here. A door function or a cylinder format is
 * stored as its own text rather than as a database enumeration, because adding a value to a
 * database enumeration is a migration and adding one to the domain is a line of code, and this shop
 * buys hardware faster than it deploys.
 *
 * <p>So the text in the column is the choice's value, and reading it back is a lookup that fails
 * loudly rather than quietly producing something else.
 */
public final class CampusMappers {

    private CampusMappers() {}

    /** A group row and the numbers of the openings it wants, written in the order the matrix holds them. */
    public record GroupWants(AccessGroupRow group, List<String> openings) {

        public GroupWants {
            openings = List.copyOf(openings);
        }
    }

    private static DoorFunction function(String value) {
        return DoorFunction.find(value)
                .orElseThrow(() -> new InputError("no such door function", Facts.of("function", value)));
    }

    private static CylinderFormat format(String value) {
        return CylinderFormat.find(value)
                .orElseThrow(() -> new InputError("no such cylinder format", Facts.of("format", value)));
    }

    private static Optional<Position> position(Optional<String> value) {
        return value.map(text -> Position.find(text)
                .orElseThrow(() -> new InputError("no such cylinder position", Facts.of("position", text))));
    }

    /** A new building row. */
    public static BuildingRow buildingToRow(Building building) {
        BuildingRow row = new BuildingRow(building.code(), building.name());
        row.setYearBuilt(building.yearBuilt().orElse(null));
        return row;
    }

    /** The building a row holds. */
    public static Building buildingFromRow(BuildingRow row) {
        return new Building(row.getCode(), row.getName(), row.getYearBuilt());
    }

    /** A new opening row, its function stored as the function's value. */
    public static OpeningRow openingToRow(Opening opening) {
        OpeningRow row = new OpeningRow(
                opening.number(),
                opening.building(),
                opening.floor(),
                opening.room(),
                opening.function().value());
        row.setExterior(opening.exterior());
        row.setDescription(opening.description());
        return row;
    }

    /**
     * The opening a row holds.
     *
     * @throws InputError for a function nobody has heard of
     */
    public static Opening openingFromRow(OpeningRow row) {
        return new Opening(
                row.getNumber(),
                row.getBuildingCode(),
                row.getFloor(),
                row.getRoom(),
                function(row.getFunction()),
                row.getDescription(),
                row.isExterior());
    }

    /** The opening schedule the rows hold, buildings first. */
    public static OpeningSchedule scheduleFromRows(List<BuildingRow> buildings, List<OpeningRow> openings) {
        OpeningSchedule schedule = new OpeningSchedule();
        for (BuildingRow building : buildings) {
            schedule.addBuilding(buildingFromRow(building));
        }
        for (OpeningRow opening : openings) {
            schedule.add(openingFromRow(opening));
        }
        return schedule;
    }

    /** A new cylinder row in no keying group. */
    public static CylinderRow cylinderToRow(Cylinder cylinder) {
        CylinderRow row =
                new CylinderRow(cylinder.serial(), cylinder.format().value(), cylinder.section(), cylinder.chambers());
        row.setManufacturer(cylinder.manufacturer());
        row.setSpooled(cylinder.spooled());
        row.setInstalledAt(cylinder.installedAt().orElse(null));
        row.setPosition(cylinder.position().map(Position::value).orElse(null));
        return row;
    }

    /** A new cylinder row pinned to a keying group. */
    public static CylinderRow cylinderToRow(Cylinder cylinder, GroupRow group) {
        CylinderRow row = cylinderToRow(cylinder);
        row.setGroup(group);
        return row;
    }

    /** The cylinder a row holds, in no keying group. */
    public static Cylinder cylinderFromRow(CylinderRow row) {
        return cylinderFromRow(row, "");
    }

    /**
     * The cylinder a row holds, pinned to the group named.
     *
     * @throws InputError for a format or a position nobody has heard of
     */
    public static Cylinder cylinderFromRow(CylinderRow row, String groupName) {
        return new Cylinder(
                row.getSerial(),
                format(row.getFormat()),
                row.getSection(),
                row.getChambers(),
                row.getManufacturer(),
                row.isSpooled(),
                row.getInstalledAt(),
                position(row.getPosition()),
                groupName);
    }

    /** {@link #registerFromRows(OpeningSchedule, List, Map)} with no group names known. */
    public static HardwareRegister registerFromRows(OpeningSchedule openings, List<CylinderRow> rows) {
        return registerFromRows(openings, rows, Map.of());
    }

    /**
     * Rebuild the register, putting each cylinder back where it was.
     *
     * <p>Cylinders join the register shelved and get installed afterwards, which is the same path a
     * new one takes at the counter, so the register cannot end up holding a state the front door
     * would have refused. A cylinder whose group is not among {@code groupNames} comes back unkeyed.
     *
     * @param groupNames each keying group's name against the key of its row
     */
    public static HardwareRegister registerFromRows(
            OpeningSchedule openings, List<CylinderRow> rows, Map<Integer, String> groupNames) {
        HardwareRegister register = new HardwareRegister(openings);
        for (CylinderRow row : rows) {
            register.add(new Cylinder(
                    row.getSerial(),
                    format(row.getFormat()),
                    row.getSection(),
                    row.getChambers(),
                    row.getManufacturer(),
                    row.isSpooled(),
                    Optional.empty(),
                    Optional.empty(),
                    ""));
            row.getGroupId()
                    .filter(groupNames::containsKey)
                    .ifPresent(id -> register.assignGroup(row.getSerial(), groupNames.get(id)));
            Optional<Position> position = position(row.getPosition());
            Optional<String> installedAt = row.getInstalledAt().filter(at -> !at.isEmpty());
            if (installedAt.isPresent() && position.isPresent()) {
                register.install(row.getSerial(), installedAt.get(), position.get());
            }
        }
        return register;
    }

    /** A new access group row. */
    public static AccessGroupRow accessGroupToRow(AccessGroup group) {
        AccessGroupRow row = new AccessGroupRow(group.name());
        row.setDepartment(group.department());
        row.setDescription(group.description());
        return row;
    }

    /** The access group a row holds. */
    public static AccessGroup accessGroupFromRow(AccessGroupRow row) {
        return new AccessGroup(row.getName(), row.getDescription(), row.getDepartment());
    }

    /**
     * A group row for each group in the matrix and the openings it wants, keys left for the caller,
     * which writes each group and then its requirements with {@link AccessRequirementRow#of}.
     */
    public static List<GroupWants> matrixToRows(AccessMatrix matrix) {
        return matrix.groups().stream()
                .map(group -> new GroupWants(accessGroupToRow(group), matrix.openingsFor(group.name())))
                .toList();
    }

    /** The access matrix the rows hold, every group added before any requirement. */
    public static AccessMatrix matrixFromRows(
            OpeningSchedule openings, List<AccessGroupRow> groups, List<AccessRequirementRow> requirements) {
        AccessMatrix matrix = new AccessMatrix(openings);
        Map<Integer, String> byId = new HashMap<>();
        for (AccessGroupRow group : groups) {
            matrix.addGroup(accessGroupFromRow(group));
            byId.put(group.getId(), group.getName());
        }
        for (AccessRequirementRow wanted : requirements) {
            matrix.require(byId.get(wanted.getGroupId()), wanted.getOpeningNumber());
        }
        return matrix;
    }
}
