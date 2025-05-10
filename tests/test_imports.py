from campus import a_campus
from hasplib.campus.openings import Building, Function, OpeningSchedule
from hasplib.reports.imports import (
    apply_openings,
    as_csv,
    describe,
    missing_columns,
    read_openings,
)

HEADER = ["number", "building", "floor", "room", "function", "exterior", "description"]


def a_file(*rows) -> str:
    return as_csv([HEADER, *rows])


def a_row(number="NEW-1", building="MOO", floor="3", room="301", function="office", **extra):
    return [
        number,
        building,
        floor,
        room,
        function,
        extra.get("exterior", "no"),
        extra.get("description", ""),
    ]


def an_empty_campus() -> OpeningSchedule:
    schedule = OpeningSchedule()
    schedule.add_building(Building("MOO", "Moore Hall"))
    return schedule


def test_a_clean_file_reads_every_row():
    found = read_openings(a_file(a_row(), a_row("NEW-2", room="302")))
    assert found.is_clean
    assert found.count == 2
    assert found.summary() == "2 rows read, 0 to fix"


def test_the_rows_come_back_as_openings():
    found = read_openings(a_file(a_row()))
    opening = found.openings[0]
    assert opening.number == "NEW-1"
    assert opening.function is Function.OFFICE
    assert not opening.exterior


def test_an_exterior_flag_is_read():
    found = read_openings(a_file(a_row(function="entrance", exterior="yes")))
    assert found.openings[0].exterior


def test_a_description_survives_a_comma():
    found = read_openings(a_file(a_row(description="north door, by the loading bay")))
    assert found.openings[0].description.startswith("north door,")


def test_a_missing_column_is_the_only_thing_reported():
    text = as_csv([["number", "building"], ["NEW-1", "MOO"]])
    found = read_openings(text)
    assert not found.is_clean
    assert {problem.column for problem in found.problems} == {"floor", "room", "function"}
    assert found.count == 0


def test_the_missing_columns_can_be_asked_for_first():
    text = as_csv([["number", "building"], ["NEW-1", "MOO"]])
    assert missing_columns(text) == ("floor", "room", "function")
    assert missing_columns(a_file(a_row())) == ()


def test_a_row_with_no_number_is_reported_with_its_line():
    found = read_openings(a_file(a_row(""), a_row("NEW-2")))
    assert [problem.line for problem in found.problems] == [2]
    assert found.count == 1


def test_a_number_twice_in_one_file_is_reported():
    found = read_openings(a_file(a_row(), a_row()))
    assert found.problems[0].message.endswith("twice")
    assert found.count == 1


def test_a_function_nobody_has_heard_of_is_reported():
    found = read_openings(a_file(a_row(function="hovercraft")))
    assert "hovercraft" in found.problems[0].message
    assert found.count == 0


def test_a_row_the_domain_refuses_is_reported_rather_than_raised():
    found = read_openings(a_file(a_row(function="privacy", exterior="yes")))
    assert not found.is_clean
    assert found.problems[0].column == "row"


def test_the_problems_describe_themselves_with_line_numbers():
    found = read_openings(a_file(a_row("")))
    assert describe(found.problems)[0].startswith("line 2, number:")


def test_a_clean_reading_applies_whole():
    schedule = an_empty_campus()
    found = read_openings(a_file(a_row(), a_row("NEW-2", room="302")))
    written, problems = apply_openings(schedule, found)
    assert written == 2
    assert problems == ()
    assert len(schedule) == 2


def test_a_dirty_reading_applies_nothing():
    schedule = an_empty_campus()
    found = read_openings(a_file(a_row(), a_row("")))
    written, problems = apply_openings(schedule, found)
    assert written == 0
    assert problems
    assert len(schedule) == 0


def test_a_building_nobody_created_stops_the_whole_load():
    schedule = an_empty_campus()
    found = read_openings(a_file(a_row(), a_row("NEW-2", building="ZZZ")))
    written, problems = apply_openings(schedule, found)
    assert written == 0
    assert problems[0].column == "building"
    assert len(schedule) == 0


def test_an_opening_that_is_already_there_stops_the_load():
    schedule = a_campus()
    found = read_openings(a_file(a_row("MOO-214", room="214", floor="2")))
    written, problems = apply_openings(schedule, found)
    assert written == 0
    assert problems[0].message.endswith("already on the list")


def test_an_empty_file_reads_nothing_and_complains_about_nothing():
    found = read_openings(as_csv([HEADER]))
    assert found.is_clean
    assert found.count == 0
