"""The rules that sit on top of a chart and throw good looking keys away.

A cut can be on the chart and still be a bad idea. Two neighbouring chambers
that jump too far leave a key the mill cannot cut cleanly, and the tip of the
milling wheel runs into the metal it just left. That limit is the macs, and
every chart publishes one.

The rest of what is in here is shop policy rather than physics. The shop will
not issue a key that is one long run of the same depth, because it looks like
a blank to a student and gets thrown away. Those rules live apart from the
chart so that changing policy does not mean editing a manufacturer's numbers.

Nothing here raises. A key gets checked once and the caller gets every reason
it failed, because handing a locksmith one complaint at a time out of six is
how an afternoon disappears.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass, field

from hasplib.keys.bitting import Bitting
from hasplib.spec.chart import DepthAndSpaceChart


@dataclass(frozen=True)
class CutViolation:
    """One reason a bitting was turned down."""

    rule: str
    message: str
    chamber: int | None = None
    facts: tuple[tuple[str, object], ...] = ()

    def as_dict(self) -> dict[str, object]:
        out: dict[str, object] = {"rule": self.rule, "message": self.message}
        if self.chamber is not None:
            out["chamber"] = self.chamber
        out.update(dict(self.facts))
        return out


@dataclass(frozen=True)
class CuttingRules:
    """Shop policy about what a cut sequence is allowed to look like.

    ``max_run`` is how many chambers in a row may carry the same depth.
    ``min_distinct`` is how many different depths a key has to use overall.
    ``shallow_tip_limit`` caps the depth of the last chamber, because a deep
    cut at the tip on a worn key lets the plug turn a hair early.
    """

    max_run: int = 3
    min_distinct: int = 2
    shallow_tip_limit: int | None = None
    forbid_all_shallowest: bool = True
    forbid_all_deepest: bool = True

    def __post_init__(self) -> None:
        if self.max_run < 1:
            raise ValueError("max_run of zero forbids every key")
        if self.min_distinct < 1:
            raise ValueError("a key uses at least one depth")


DEFAULT_RULES = CuttingRules()


def check_macs(chart: DepthAndSpaceChart, bitting: Bitting) -> list[CutViolation]:
    """Every neighbouring pair that steps further than the chart allows."""
    found: list[CutViolation] = []
    steps = bitting.adjacent_differences()
    for index, step in enumerate(steps):
        if step > chart.macs:
            low = index + 1
            found.append(
                CutViolation(
                    rule="macs",
                    message=(
                        f"chambers {low} and {low + 1} step {step}, the chart allows {chart.macs}"
                    ),
                    chamber=low,
                    facts=(("step", step), ("macs", chart.macs)),
                )
            )
    return found


def check_runs(rules: CuttingRules, bitting: Bitting) -> list[CutViolation]:
    """Runs of one depth that go on longer than policy allows."""
    found: list[CutViolation] = []
    run_start = 1
    run_depth = bitting.at(1)
    depths = bitting.depths
    for chamber in range(2, bitting.chambers + 2):
        current = depths[chamber - 1] if chamber <= bitting.chambers else None
        if current == run_depth:
            continue
        length = chamber - run_start
        if length > rules.max_run:
            found.append(
                CutViolation(
                    rule="run",
                    message=f"{length} chambers in a row cut {run_depth}",
                    chamber=run_start,
                    facts=(("length", length), ("depth", run_depth), ("limit", rules.max_run)),
                )
            )
        run_start = chamber
        if current is not None:
            run_depth = current
    return found


def check_variety(
    chart: DepthAndSpaceChart, rules: CuttingRules, bitting: Bitting
) -> list[CutViolation]:
    """Keys that use too few depths, or none but the ends of the chart."""
    found: list[CutViolation] = []
    distinct = set(bitting.depths)
    if len(distinct) < rules.min_distinct:
        found.append(
            CutViolation(
                rule="variety",
                message=f"the key uses {len(distinct)} depths, policy wants {rules.min_distinct}",
                facts=(("distinct", len(distinct)), ("wanted", rules.min_distinct)),
            )
        )
    if rules.forbid_all_shallowest and distinct == {chart.shallowest}:
        found.append(
            CutViolation(
                rule="all_shallowest",
                message="every chamber is cut at the shallowest depth on the chart",
            )
        )
    if rules.forbid_all_deepest and distinct == {chart.deepest}:
        found.append(
            CutViolation(
                rule="all_deepest",
                message="every chamber is cut at the deepest depth on the chart",
            )
        )
    return found


def check_tip(rules: CuttingRules, bitting: Bitting) -> list[CutViolation]:
    """The last chamber, which policy may want kept shallow."""
    if rules.shallow_tip_limit is None:
        return []
    tip = bitting.at(bitting.chambers)
    if tip <= rules.shallow_tip_limit:
        return []
    return [
        CutViolation(
            rule="tip",
            message=f"the tip cut is {tip} and policy stops at {rules.shallow_tip_limit}",
            chamber=bitting.chambers,
            facts=(("depth", tip), ("limit", rules.shallow_tip_limit)),
        )
    ]


def check_cuts(
    chart: DepthAndSpaceChart,
    bitting: Bitting,
    rules: CuttingRules = DEFAULT_RULES,
) -> tuple[CutViolation, ...]:
    """Every reason this bitting is not one the shop would cut.

    The chart is asked first. A key of the wrong length or carrying a depth
    the chart never published gets that answer and nothing else, because the
    later rules would only be guessing at what the cuts meant.
    """
    if bitting.chambers != chart.chambers:
        return (
            CutViolation(
                rule="length",
                message=f"the key has {bitting.chambers} cuts and the family has {chart.chambers}",
                facts=(("chambers", chart.chambers), ("got", bitting.chambers)),
            ),
        )
    off_chart = [
        CutViolation(
            rule="depth",
            message=f"depth {depth} is not on the {chart.family} chart",
            chamber=chamber,
            facts=(("depth", depth),),
        )
        for chamber, depth in enumerate(bitting.depths, start=1)
        if not chart.allows_depth(depth)
    ]
    if off_chart:
        return tuple(off_chart)
    found: list[CutViolation] = []
    found.extend(check_macs(chart, bitting))
    found.extend(check_runs(rules, bitting))
    found.extend(check_variety(chart, rules, bitting))
    found.extend(check_tip(rules, bitting))
    return tuple(found)


def is_acceptable(
    chart: DepthAndSpaceChart, bitting: Bitting, rules: CuttingRules = DEFAULT_RULES
) -> bool:
    return not check_cuts(chart, bitting, rules)


def reasons(violations: Sequence[CutViolation]) -> tuple[str, ...]:
    """Just the rule names, which is what a summary line wants."""
    return tuple(violation.rule for violation in violations)


@dataclass(frozen=True)
class RuleSet:
    """A chart and the policy that goes with it, carried together."""

    chart: DepthAndSpaceChart
    rules: CuttingRules = field(default_factory=CuttingRules)

    def check(self, bitting: Bitting) -> tuple[CutViolation, ...]:
        return check_cuts(self.chart, bitting, self.rules)

    def accepts(self, bitting: Bitting) -> bool:
        return is_acceptable(self.chart, bitting, self.rules)
