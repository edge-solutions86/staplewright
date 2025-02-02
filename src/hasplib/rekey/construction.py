"""Construction keying, where the key has to stop working on its own.

A building under construction is full of trades who all need to get in, and
none of them should still be getting in the week after handover. Handing out
the permanent keys early and collecting them afterwards has never once worked.

Two ways to do it properly. On interchangeable cores the answer is dull and
reliable: construction cores go in, permanent cores go in a box, and at
handover somebody walks the building with a control key. On conventional
cylinders it is the lost ball. A small steel ball sits above the driver in
one or two chambers, which raises the break the key has to reach, so the
construction cut in those chambers is deeper than the permanent one. The
first turn of the permanent key drops the ball into a void milled in the
shell, the extra break disappears, and every construction key in the county
becomes a paperweight.

The arithmetic that matters is small and the failure is expensive: pick the
ball chambers wrong and either the construction key does not work at all, or
it survives handover and nobody notices for a year.
"""

from __future__ import annotations

from collections.abc import Sequence
from dataclasses import dataclass

from hasplib.errors import ConflictError, InputError
from hasplib.keys.bitting import Bitting
from hasplib.spec.chart import DepthAndSpaceChart


@dataclass(frozen=True)
class BallKeying:
    """The lost ball arrangement for one cylinder."""

    permanent: Bitting
    construction: Bitting
    ball_chambers: tuple[int, ...]
    ball_steps: int = 2

    def __post_init__(self) -> None:
        if not self.ball_chambers:
            raise ConflictError("a construction cylinder carries at least one ball")
        if self.ball_steps < 1:
            raise InputError("a ball raises the break by at least one step")
        if self.permanent.chambers != self.construction.chambers:
            raise ConflictError(
                "the two keys are not the same length",
                permanent=self.permanent.chambers,
                construction=self.construction.chambers,
            )
        for chamber in self.ball_chambers:
            if chamber < 1 or chamber > self.permanent.chambers:
                raise ConflictError("no such chamber on this key", chamber=chamber)

    @property
    def plain_chambers(self) -> tuple[int, ...]:
        balls = set(self.ball_chambers)
        return tuple(c for c in range(1, self.permanent.chambers + 1) if c not in balls)

    def problems(self) -> tuple[str, ...]:
        """Everything wrong with this arrangement, in the order it is read."""
        found: list[str] = []
        for chamber in self.plain_chambers:
            if self.permanent.at(chamber) != self.construction.at(chamber):
                found.append(f"chamber {chamber} carries no ball and the two keys disagree there")
        for chamber in self.ball_chambers:
            gap = self.construction.at(chamber) - self.permanent.at(chamber)
            if gap <= 0:
                found.append(
                    f"chamber {chamber} needs the construction cut deeper than the permanent one"
                )
            elif gap != self.ball_steps:
                found.append(
                    f"chamber {chamber} steps {gap} and the ball is worth {self.ball_steps}"
                )
        return tuple(found)

    @property
    def is_sound(self) -> bool:
        return not self.problems()

    def voided_by_first_use(self) -> bool:
        """Whether turning the permanent key really kills the other one."""
        if not self.is_sound:
            return False
        return any(
            self.construction.at(chamber) != self.permanent.at(chamber)
            for chamber in self.ball_chambers
        )


def build_construction_key(
    chart: DepthAndSpaceChart,
    permanent: Bitting,
    ball_chambers: Sequence[int],
    ball_steps: int = 2,
) -> Bitting:
    """Cut a construction key for a permanent one, or say why it cannot be.

    The construction cut sits deeper by the ball, so a permanent cut already
    near the bottom of the chart leaves nowhere to go. That is the check
    people forget, and it is the reason the ball chambers get picked after
    the permanent keys are cut rather than before.
    """
    cuts = list(permanent.depths)
    for chamber in ball_chambers:
        if chamber < 1 or chamber > permanent.chambers:
            raise ConflictError("no such chamber on this key", chamber=chamber)
        wanted = permanent.at(chamber) + ball_steps
        if not chart.allows_depth(wanted):
            raise ConflictError(
                "the permanent cut is too deep to sit a ball above",
                chamber=chamber,
                permanent=permanent.at(chamber),
                wanted=wanted,
            )
        cuts[chamber - 1] = wanted
    return Bitting(cuts)


def workable_ball_chambers(
    chart: DepthAndSpaceChart, permanent: Bitting, ball_steps: int = 2
) -> tuple[int, ...]:
    """Chambers with room above the permanent cut for a ball."""
    return tuple(
        chamber
        for chamber in range(1, permanent.chambers + 1)
        if chart.allows_depth(permanent.at(chamber) + ball_steps)
    )


@dataclass(frozen=True)
class CoreSwap:
    """The handover plan for a building running on interchangeable cores."""

    opening: str
    construction_core: str
    permanent_core: str

    def __post_init__(self) -> None:
        if self.construction_core == self.permanent_core:
            raise ConflictError(
                "the same core cannot be both halves of a swap", opening=self.opening
            )


def swap_plan(
    openings: Sequence[str],
    construction_cores: Sequence[str],
    permanent_cores: Sequence[str],
) -> tuple[CoreSwap, ...]:
    """Line up a construction core and a permanent core against every door."""
    if not len(openings) == len(construction_cores) == len(permanent_cores):
        raise InputError(
            "every door needs both cores",
            openings=len(openings),
            construction=len(construction_cores),
            permanent=len(permanent_cores),
        )
    if len(set(permanent_cores)) != len(permanent_cores):
        raise ConflictError("a permanent core is on the plan twice")
    return tuple(
        CoreSwap(opening, construction, permanent)
        for opening, construction, permanent in zip(
            openings, construction_cores, permanent_cores, strict=True
        )
    )


def unswapped(plan: Sequence[CoreSwap], done_openings: Sequence[str]) -> tuple[str, ...]:
    """Doors still on a construction core after a handover walk."""
    done = set(done_openings)
    return tuple(sorted(swap.opening for swap in plan if swap.opening not in done))
