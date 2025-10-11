from datetime import date

import pytest

from hasplib.errors import ConflictError, InputError, NotFoundError
from hasplib.rekey.phasing import (
    Calendar,
    Door,
    Restriction,
    Window,
    WindowKind,
    carry_over,
    phase,
    year_plan,
)

SUMMER = Window("summer", WindowKind.VACATION, date(2025, 6, 2), date(2025, 6, 6), 4)
AUTUMN = Window("autumn term", WindowKind.TERM, date(2025, 9, 1), date(2025, 9, 5), 4)
WINTER = Window("winter shutdown", WindowKind.SHUTDOWN, date(2025, 12, 22), date(2025, 12, 24), 4)


def a_calendar() -> Calendar:
    calendar = Calendar()
    calendar.add(SUMMER, [Restriction.OCCUPIED, Restriction.TEACHING])
    calendar.add(AUTUMN, [])
    calendar.add(WINTER, [Restriction.TEACHING])
    return calendar


def a_door(number: str, restriction: Restriction = Restriction.NONE) -> Door:
    return Door(number, number.split("-")[0], restriction)


def test_a_window_needs_a_name():
    with pytest.raises(InputError):
        Window(" ", WindowKind.TERM, date(2025, 1, 1), date(2025, 1, 2))


def test_a_window_closes_after_it_opens():
    with pytest.raises(InputError):
        Window("bad", WindowKind.TERM, date(2025, 1, 5), date(2025, 1, 1))


def test_a_window_does_some_work():
    with pytest.raises(InputError):
        Window("idle", WindowKind.TERM, date(2025, 1, 1), date(2025, 1, 2), 0)


def test_a_window_counts_its_days_inclusively():
    assert SUMMER.days == 5


def test_the_capacity_is_days_times_the_rate():
    assert SUMMER.capacity == 20


def test_a_window_knows_which_days_it_covers():
    assert SUMMER.covers(date(2025, 6, 4))
    assert not SUMMER.covers(date(2025, 6, 7))


def test_two_windows_that_do_not_touch_do_not_overlap():
    assert not SUMMER.overlaps(AUTUMN)


def test_a_window_inside_another_overlaps_it():
    inside = Window("inside", WindowKind.WEEKEND, date(2025, 6, 3), date(2025, 6, 4))
    assert SUMMER.overlaps(inside)


def test_the_first_doors_land_on_the_opening_day():
    assert SUMMER.day_for(0) == date(2025, 6, 2)
    assert SUMMER.day_for(3) == date(2025, 6, 2)


def test_the_next_door_rolls_onto_the_next_day():
    assert SUMMER.day_for(4) == date(2025, 6, 3)


def test_a_position_before_the_start_is_refused():
    with pytest.raises(InputError):
        SUMMER.day_for(-1)


def test_a_window_reads_back_as_one_line():
    assert SUMMER.describe() == "summer 2025-06-02 to 2025-06-06, 20 doors"


def test_a_door_takes_some_minutes():
    with pytest.raises(InputError):
        Door("MOO-101", "MOO", minutes=0)


def test_a_door_with_nothing_stopping_it_is_free():
    assert a_door("MOO-101").is_free


def test_a_calendar_refuses_a_window_that_runs_over_another():
    calendar = a_calendar()
    clash = Window("clash", WindowKind.WEEKEND, date(2025, 6, 4), date(2025, 6, 8))
    with pytest.raises(ConflictError) as caught:
        calendar.add(clash)
    assert caught.value.facts["against"] == "summer"


def test_a_window_nobody_added_is_a_404():
    with pytest.raises(NotFoundError):
        Calendar().window("spring")


def test_every_window_takes_unrestricted_work():
    calendar = a_calendar()
    assert calendar.allows("autumn term", Restriction.NONE)


def test_a_window_only_takes_what_it_was_told_to():
    calendar = a_calendar()
    assert calendar.allows("summer", Restriction.OCCUPIED)
    assert not calendar.allows("autumn term", Restriction.OCCUPIED)


def test_the_windows_come_back_in_date_order():
    assert [window.name for window in a_calendar()] == [
        "summer",
        "autumn term",
        "winter shutdown",
    ]


def test_the_windows_open_to_a_restriction_are_named():
    calendar = a_calendar()
    found = calendar.windows_for(Restriction.TEACHING)
    assert [window.name for window in found] == ["summer", "winter shutdown"]


def test_the_total_capacity_adds_the_windows_up():
    assert a_calendar().total_capacity() == 20 + 20 + 12


def test_a_calendar_counts_and_contains():
    calendar = a_calendar()
    assert len(calendar) == 3
    assert "summer" in calendar


def test_a_free_door_lands_in_the_first_window():
    found = phase(a_calendar(), [a_door("MOO-101")])
    assert found.placed[0].window == "summer"
    assert found.placed[0].on == date(2025, 6, 2)


def test_a_restricted_door_skips_the_window_that_will_not_take_it():
    found = phase(a_calendar(), [a_door("MOO-101", Restriction.OCCUPIED)])
    assert found.placed[0].window == "summer"


def test_a_door_nothing_will_take_does_not_land():
    found = phase(a_calendar(), [a_door("MOO-101", Restriction.EXAMS)])
    assert found.placed == ()
    assert found.unplaced[0][1] == "no window takes work that is exams running"


def test_a_full_calendar_says_the_windows_are_full():
    calendar = Calendar()
    calendar.add(Window("tiny", WindowKind.WEEKEND, date(2025, 6, 2), date(2025, 6, 2), 1))
    found = phase(calendar, [a_door("MOO-101"), a_door("MOO-110")])
    assert len(found.placed) == 1
    assert found.unplaced[0][1] == "every window that would take it is full"


def test_the_hardest_doors_are_placed_first():
    calendar = Calendar()
    calendar.add(
        Window("only", WindowKind.VACATION, date(2025, 6, 2), date(2025, 6, 2), 1),
        [Restriction.OCCUPIED],
    )
    calendar.add(Window("open", WindowKind.TERM, date(2025, 9, 1), date(2025, 9, 1), 1))
    doors = [a_door("MOO-101"), a_door("VOS-100", Restriction.OCCUPIED)]
    found = phase(calendar, doors)
    landed = {item.door.number: item.window for item in found.placed}
    assert landed["VOS-100"] == "only"
    assert landed["MOO-101"] == "open"


def test_the_placements_come_back_in_day_order():
    doors = [a_door(f"MOO-{n}") for n in range(101, 111)]
    found = phase(a_calendar(), doors)
    days = [item.on for item in found.placed]
    assert days == sorted(days)


def test_the_doors_in_one_window_come_back_together():
    doors = [a_door(f"MOO-{n}") for n in range(101, 130)]
    found = phase(a_calendar(), doors)
    assert len(found.in_window("summer")) == 20


def test_the_doors_on_one_day_come_back_together():
    doors = [a_door(f"MOO-{n}") for n in range(101, 111)]
    found = phase(a_calendar(), doors)
    assert len(found.on_day(date(2025, 6, 2))) == 4


def test_the_load_covers_every_window_even_the_empty_ones():
    found = phase(a_calendar(), [a_door("MOO-101")])
    assert found.load_by_window() == {"summer": 1, "autumn term": 0, "winter shutdown": 0}


def test_the_spare_capacity_is_what_is_left():
    found = phase(a_calendar(), [a_door("MOO-101")])
    assert found.spare_capacity()["summer"] == 19


def test_a_plan_where_everything_fits_has_landed():
    assert phase(a_calendar(), [a_door("MOO-101")]).landed


def test_the_reasons_are_counted_most_common_first():
    doors = [a_door("MOO-101", Restriction.EXAMS), a_door("MOO-110", Restriction.EXAMS)]
    found = phase(a_calendar(), doors)
    assert found.reasons() == {"no window takes work that is exams running": 2}


def test_the_door_count_covers_both_halves():
    doors = [a_door("MOO-101"), a_door("MOO-110", Restriction.EXAMS)]
    assert phase(a_calendar(), doors).door_count == 2


def test_the_year_plan_leads_with_the_totals():
    lines = year_plan(a_calendar(), [a_door("MOO-101")])
    assert lines[0] == "1 doors across 3 windows"
    assert lines[-1] == "everything fits this year"


def test_the_year_plan_names_the_load_per_window():
    lines = year_plan(a_calendar(), [a_door("MOO-101")])
    assert "summer" in lines[1]
    assert "1 placed" in lines[1]


def test_the_year_plan_counts_what_does_not_fit():
    doors = [a_door("MOO-101", Restriction.EXAMS)]
    lines = year_plan(a_calendar(), doors)
    assert lines[-2] == "1 doors do not fit"


def test_what_did_not_fit_carries_over():
    doors = [a_door("MOO-101"), a_door("MOO-110", Restriction.EXAMS)]
    found = phase(a_calendar(), doors)
    assert [door.number for door in carry_over(found)] == ["MOO-110"]


def test_a_plan_that_fits_carries_nothing_over():
    assert carry_over(phase(a_calendar(), [a_door("MOO-101")])) == ()
