"""A slice of the campus the tests can walk without inventing it each time."""

from hasplib.campus.openings import Building, Function, Opening, OpeningSchedule


def a_campus() -> OpeningSchedule:
    schedule = OpeningSchedule()
    schedule.add_building(Building("MOO", "Moore Hall", 1953))
    schedule.add_building(Building("VOS", "Voss Hall", 1971))
    schedule.add_building(Building("HEI", "Heikkinen Shops", 1938))

    schedule.add(
        Opening("MOO-101", "MOO", "1", "101", Function.ENTRANCE, "north door", exterior=True)
    )
    schedule.add(Opening("MOO-110", "MOO", "1", "110", Function.CLASSROOM))
    schedule.add(Opening("MOO-214", "MOO", "2", "214", Function.OFFICE))
    schedule.add(Opening("MOO-216", "MOO", "2", "216", Function.OFFICE))
    schedule.add(Opening("MOO-218", "MOO", "2", "218B", Function.STOREROOM, "map room"))
    schedule.add(Opening("MOO-260", "MOO", "2", "260", Function.PRIVACY, "staff washroom"))

    schedule.add(
        Opening("VOS-100", "VOS", "1", "100", Function.ENTRANCE, "lake side", exterior=True)
    )
    schedule.add(Opening("VOS-311", "VOS", "3", "311", Function.OFFICE))
    schedule.add(Opening("VOS-312", "VOS", "3", "312", Function.OFFICE))

    schedule.add(Opening("HEI-001", "HEI", "B", "001", Function.STOREROOM, "paint cage"))
    schedule.add(Opening("HEI-G12", "HEI", "1", "G12", Function.PADLOCK, "salt shed gate"))
    return schedule
