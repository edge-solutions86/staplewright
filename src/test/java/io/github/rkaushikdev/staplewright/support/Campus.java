package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.campus.Building;
import io.github.rkaushikdev.staplewright.campus.DoorFunction;
import io.github.rkaushikdev.staplewright.campus.Opening;
import io.github.rkaushikdev.staplewright.campus.OpeningSchedule;

/**
 * A slice of the campus the tests can walk without inventing it each time.
 *
 * <p>Three buildings and eleven openings. Moore Hall has an exterior entrance, a classroom, two
 * offices, a storeroom whose room number differs from its opening number, and a washroom with a
 * privacy function that takes no cylinder. Voss Hall has an exterior entrance and two offices.
 * Heikkinen Shops has a basement storeroom and a padlocked gate.
 */
public final class Campus {

    private Campus() {}

    public static OpeningSchedule aCampus() {
        OpeningSchedule schedule = new OpeningSchedule();
        schedule.addBuilding(new Building("MOO", "Moore Hall", 1953));
        schedule.addBuilding(new Building("VOS", "Voss Hall", 1971));
        schedule.addBuilding(new Building("HEI", "Heikkinen Shops", 1938));

        schedule.add(new Opening("MOO-101", "MOO", "1", "101", DoorFunction.ENTRANCE, "north door", true));
        schedule.add(new Opening("MOO-110", "MOO", "1", "110", DoorFunction.CLASSROOM));
        schedule.add(new Opening("MOO-214", "MOO", "2", "214", DoorFunction.OFFICE));
        schedule.add(new Opening("MOO-216", "MOO", "2", "216", DoorFunction.OFFICE));
        schedule.add(new Opening("MOO-218", "MOO", "2", "218B", DoorFunction.STOREROOM, "map room"));
        schedule.add(new Opening("MOO-260", "MOO", "2", "260", DoorFunction.PRIVACY, "staff washroom"));

        schedule.add(new Opening("VOS-100", "VOS", "1", "100", DoorFunction.ENTRANCE, "lake side", true));
        schedule.add(new Opening("VOS-311", "VOS", "3", "311", DoorFunction.OFFICE));
        schedule.add(new Opening("VOS-312", "VOS", "3", "312", DoorFunction.OFFICE));

        schedule.add(new Opening("HEI-001", "HEI", "B", "001", DoorFunction.STOREROOM, "paint cage"));
        schedule.add(new Opening("HEI-G12", "HEI", "1", "G12", DoorFunction.PADLOCK, "salt shed gate"));
        return schedule;
    }
}
