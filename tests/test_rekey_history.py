from datetime import date

import pytest

from hasplib.errors import ConflictError, InputError
from hasplib.keys.symbol import KeySymbol
from hasplib.rekey.history import RekeyEvent, RekeyHistory


def sym(text: str) -> KeySymbol:
    return KeySymbol.parse(text)


def an_event(serial: str = "C-1001", when: date = date(2020, 3, 4), **overrides) -> RekeyEvent:
    settings = {
        "serial": serial,
        "happened_on": when,
        "to_symbols": (sym("AA1"), sym("AA")),
        "technician": "jkoivu",
    }
    settings.update(overrides)
    return RekeyEvent(**settings)


def a_history(**counts) -> RekeyHistory:
    history = RekeyHistory()
    for serial, years in counts.items():
        for offset in range(years):
            history.record(an_event(serial, date(2015 + offset, 6, 1)))
    return history


def test_an_event_names_a_cylinder_and_what_it_came_back_as():
    event = an_event()
    assert event.serial == "C-1001"
    assert event.wafers == 1
    assert "AA1" in event.describe()


def test_an_event_without_a_cylinder_is_refused():
    with pytest.raises(InputError):
        an_event(serial="  ")


def test_a_cylinder_comes_back_keyed_to_something():
    with pytest.raises(ConflictError):
        an_event(to_symbols=())


def test_a_symbol_twice_on_one_cylinder_is_refused():
    with pytest.raises(ConflictError):
        an_event(to_symbols=(sym("AA1"), sym("AA1")))


def test_one_symbol_carries_no_wafers():
    assert an_event(to_symbols=(sym("AA1"),)).wafers == 0


def test_the_events_for_a_cylinder_come_back_oldest_first():
    history = RekeyHistory()
    history.record(an_event("C-1001", date(2022, 1, 1)))
    history.record(an_event("C-1001", date(2019, 1, 1)))
    found = history.events_for("C-1001")
    assert [event.happened_on.year for event in found] == [2019, 2022]


def test_a_cylinder_nobody_touched_has_no_history():
    history = a_history(**{"C-1001": 2})
    assert history.events_for("C-9999") == ()
    assert history.times_rekeyed("C-9999") == 0
    assert history.last_rekeyed("C-9999") is None


def test_the_count_and_the_last_date_read_off_the_events():
    history = a_history(**{"C-1001": 3})
    assert history.times_rekeyed("C-1001") == 3
    assert history.last_rekeyed("C-1001") == date(2017, 6, 1)


def test_what_it_is_keyed_to_now_is_its_last_event():
    history = RekeyHistory()
    history.record(an_event("C-1001", date(2019, 1, 1), to_symbols=(sym("AA1"),)))
    history.record(an_event("C-1001", date(2022, 1, 1), to_symbols=(sym("AB1"), sym("AB"))))
    assert [s.render() for s in history.current_symbols("C-1001")] == ["AB1", "AB"]


def test_a_cylinder_with_no_history_is_keyed_to_nothing_on_record():
    assert RekeyHistory().current_symbols("C-1001") == ()


def test_the_intervals_are_the_gaps_between_repins():
    history = RekeyHistory()
    history.record(an_event("C-1001", date(2020, 1, 1)))
    history.record(an_event("C-1001", date(2020, 3, 1)))
    history.record(an_event("C-1001", date(2021, 3, 1)))
    assert history.intervals("C-1001") == (60, 365)
    assert history.average_interval("C-1001") == 212


def test_one_repin_has_no_interval():
    history = a_history(**{"C-1001": 1})
    assert history.intervals("C-1001") == ()
    assert history.average_interval("C-1001") is None


def test_a_cylinder_past_the_threshold_is_reported():
    history = a_history(**{"C-1001": 10, "C-1002": 3})
    found = history.worn_out()
    assert [serial for serial, _ in found] == ["C-1001"]
    assert found[0][1] == 10


def test_a_shop_that_replaces_sooner_says_so():
    history = RekeyHistory(replace_after=2)
    history.record_all([an_event("C-1002", date(2020, 1, 1)), an_event("C-1002", date(2021, 1, 1))])
    assert [serial for serial, _ in history.worn_out()] == ["C-1002"]


def test_a_threshold_of_nothing_is_refused():
    with pytest.raises(InputError):
        RekeyHistory(replace_after=0)


def test_the_cylinders_with_no_record_are_named():
    history = a_history(**{"C-1001": 2})
    assert history.never_rekeyed(["C-1001", "C-1002", "C-1003"]) == ("C-1002", "C-1003")


def test_the_busiest_cylinders_come_first():
    history = a_history(**{"C-1001": 4, "C-1002": 7, "C-1003": 1})
    assert [serial for serial, _ in history.busiest(2)] == ["C-1002", "C-1001"]


def test_one_technicians_work_comes_back_together():
    history = RekeyHistory()
    history.record(an_event("C-1001", date(2020, 1, 1), technician="jkoivu"))
    history.record(an_event("C-1002", date(2020, 2, 1), technician="phamm"))
    assert [event.serial for event in history.by_technician("phamm")] == ["C-1002"]


def test_everything_since_a_date_comes_back_in_order():
    history = a_history(**{"C-1001": 4})
    found = history.since(date(2017, 1, 1))
    assert [event.happened_on.year for event in found] == [2017, 2018]


def test_the_history_prints_for_the_back_of_a_work_order():
    history = a_history(**{"C-1001": 2})
    lines = history.lines("C-1001")
    assert lines[0] == "C-1001: 2 repins"
    assert len(lines) == 3


def test_a_cylinder_with_nothing_on_record_says_so():
    assert RekeyHistory().lines("C-1001") == ("C-1001: nothing on record",)


def test_the_history_counts_and_iterates_everything():
    history = a_history(**{"C-1001": 2, "C-1002": 3})
    assert len(history) == 5
    assert len(list(history)) == 5
