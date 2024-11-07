"""The pinning chart, which is the piece of paper that goes to the bench.

This used to live on the stack classes, one render method each, and by the
third one they had started to disagree about spacing. A chart is a document,
not a property of a column, so it lives here now and the pinning code went
back to being arithmetic.

The format is fixed by what the bench does with it. One row a chamber,
numbered down the left, brass in order from the bottom pin out, and the
control wafer in brackets because that is the one a technician has to not
lose. Everything lines up in columns so a row with a missing piece is
obvious at arm's length.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence

from hasplib.keys.bitting import Bitting
from hasplib.pinning.core import CorePinning, CoreStack
from hasplib.pinning.cylinder import CylinderPinning
from hasplib.pinning.segments import PinSegment
from hasplib.pinning.stack import PinStack

_WIDTH = 5


def stack_row(stack: PinStack) -> str:
    """One chamber, bottom pin first, driver last."""
    pieces = [str(stack.bottom_mils)]
    if isinstance(stack, CoreStack):
        pieces.extend(str(wafer) for wafer in stack.wafers_mils[:-1])
        pieces.append(f"[{stack.control_wafer_mils}]")
    else:
        pieces.extend(str(wafer) for wafer in stack.wafers_mils)
    pieces.append(str(stack.driver_mils))
    return "".join(piece.rjust(_WIDTH) for piece in pieces)


def chart_body(stacks: Sequence[PinStack]) -> tuple[str, ...]:
    """The numbered rows, without any heading."""
    return tuple(f"{stack.chamber:>2}{stack_row(stack)}" for stack in stacks)


def key_lines(keys: Sequence[Bitting]) -> tuple[str, ...]:
    return tuple(f"   key {key.render()}" for key in keys)


def cylinder_chart(pinning: CylinderPinning, title: str = "") -> tuple[str, ...]:
    """The whole sheet for one cylinder."""
    lines: list[str] = []
    if title:
        lines.append(title)
    lines.extend(key_lines(pinning.keys))
    lines.append(f"   {pinning.wafer_count} wafers, {pinning.piece_count} pieces")
    lines.extend(chart_body(pinning.stacks))
    return tuple(lines)


def core_chart(pinning: CorePinning, title: str = "") -> tuple[str, ...]:
    """The same sheet for a core, with the control key called out."""
    lines: list[str] = []
    if title:
        lines.append(title)
    lines.extend(key_lines(pinning.keys))
    lines.append(f"   control {pinning.control_key.render()}")
    lines.extend(chart_body(pinning.stacks))
    return tuple(lines)


def parts_summary(parts: Iterable[PinSegment]) -> tuple[str, ...]:
    """How much of each bin a job draws, for the person filling the tray."""
    counts: dict[str, int] = {}
    for part in parts:
        counts[part.bin_code] = counts.get(part.bin_code, 0) + 1
    return tuple(f"{code:<8}{count:>4}" for code, count in sorted(counts.items()))


def as_text(lines: Iterable[str]) -> str:
    return "\n".join(lines)
