from charts import seven_pin_core_chart, six_pin_chart
from hasplib.keys.bitting import Bitting
from hasplib.pinning.core import CoreSpec, build_core_stack, pin_core
from hasplib.pinning.cylinder import pin_cylinder
from hasplib.pinning.stack import PinningSpec, build_stack
from hasplib.reports.page import as_text
from hasplib.reports.pinning import (
    chart_body,
    core_chart,
    cylinder_chart,
    parts_summary,
    stack_row,
)


def b(text: str) -> Bitting:
    return Bitting.parse(text)


def a_spec() -> PinningSpec:
    return PinningSpec(six_pin_chart(), 150, 480)


def a_core_spec() -> CoreSpec:
    return CoreSpec(PinningSpec(seven_pin_core_chart(), 100, 600, 25, 75), 50)


def test_a_row_reads_bottom_pin_then_wafers_then_driver():
    row = stack_row(build_stack(a_spec(), 2, [2, 4, 8]))
    assert row.split() == ["180", "30", "60", "210"]


def test_a_row_of_one_pin_still_has_two_pieces():
    assert stack_row(build_stack(a_spec(), 1, [3])).split() == ["195", "285"]


def test_every_piece_is_right_aligned_in_its_own_column():
    row = stack_row(build_stack(a_spec(), 2, [2, 4, 8]))
    assert len(row) == 20
    assert row.startswith("  180")


def test_a_core_row_brackets_the_control_wafer():
    row = stack_row(build_core_stack(a_core_spec(), 1, [2, 6], 6))
    assert row.split() == ["150", "100", "[50]", "300"]


def test_the_body_numbers_every_chamber_down_the_side():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    body = chart_body(pinning.stacks)
    assert len(body) == 6
    assert body[0].startswith(" 1")
    assert body[5].startswith(" 6")


def test_the_cylinder_sheet_carries_its_keys_and_its_cost():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    lines = cylinder_chart(pinning, "moore hall 214")
    assert lines[0] == "moore hall 214"
    assert "key 342156" in lines[1]
    assert "2 wafers" in lines[3]
    assert len(lines) == 4 + 6


def test_a_sheet_without_a_title_starts_at_the_keys():
    pinning = pin_cylinder(a_spec(), [b("342156")])
    assert cylinder_chart(pinning)[0].strip() == "key 342156"


def test_the_core_sheet_calls_out_the_control_key():
    core = pin_core(a_core_spec(), [b("2462462")], b("6666666"))
    lines = core_chart(core, "voss hall 3rd floor")
    assert lines[0] == "voss hall 3rd floor"
    assert lines[2].strip() == "control 6666666"
    assert all("[" in line for line in lines[3:])


def test_the_parts_summary_counts_every_bin_once():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    summary = parts_summary(pinning.parts())
    assert len(summary) == len({part.bin_code for part in pinning.parts()})
    assert summary == tuple(sorted(summary))


def test_the_parts_summary_adds_up_to_the_piece_count():
    pinning = pin_cylinder(a_spec(), [b("342156"), b("362356")])
    counted = sum(int(line.split()[1]) for line in parts_summary(pinning.parts()))
    assert counted == pinning.piece_count


def test_the_summary_of_nothing_is_nothing():
    assert parts_summary([]) == ()


def test_lines_join_into_a_page():
    pinning = pin_cylinder(a_spec(), [b("342156")])
    page = as_text(cylinder_chart(pinning, "moore hall 214"))
    assert page.count("\n") == len(cylinder_chart(pinning, "moore hall 214")) - 1
    assert page.startswith("moore hall 214")
