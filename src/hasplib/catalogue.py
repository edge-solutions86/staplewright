"""The refusal codes, gathered, because they are part of the interface.

A caller catching a refusal branches on the code, not on the class and not on
the message. The class is a Python detail and the message is prose somebody
will improve one day, but the code is a promise: ``not_found`` has meant the
same thing since the first release and changing it would break whoever is
branching on it.

So the codes live in one place, there is a test that no two errors share one,
and there is a test that every error class is on this list. Both of those
exist because a code was duplicated once and the api quietly started mapping
two different refusals to the same status.
"""

from __future__ import annotations

from dataclasses import dataclass

from hasplib.errors import (
    BittingError,
    CapacityError,
    ConflictError,
    HaspError,
    HierarchyError,
    InputError,
    KeywayError,
    NotFoundError,
    PinningError,
    ProgressionError,
    SpecificationError,
)


@dataclass(frozen=True)
class Refusal:
    """One way this library says no, and what a caller should do about it."""

    code: str
    kind: type[HaspError]
    status: int
    meaning: str
    retry: bool

    def describe(self) -> str:
        again = "worth another go" if self.retry else "not worth another go"
        return f"{self.code:<14}{self.status}  {self.meaning} ({again})"


CATALOGUE: tuple[Refusal, ...] = (
    Refusal(
        InputError.code,
        InputError,
        422,
        "the request could not be read",
        retry=True,
    ),
    Refusal(
        NotFoundError.code,
        NotFoundError,
        404,
        "nothing on record answers to that",
        retry=False,
    ),
    Refusal(
        ConflictError.code,
        ConflictError,
        409,
        "the record is not in a state that allows it",
        retry=False,
    ),
    Refusal(
        CapacityError.code,
        CapacityError,
        409,
        "there is not enough of something to do it",
        retry=True,
    ),
    Refusal(
        SpecificationError.code,
        SpecificationError,
        400,
        "the manufacturer specification does not allow it",
        retry=False,
    ),
    Refusal(
        BittingError.code,
        BittingError,
        400,
        "that is not a cut this chart can produce",
        retry=False,
    ),
    Refusal(
        KeywayError.code,
        KeywayError,
        400,
        "the keyway sections do not work that way",
        retry=False,
    ),
    Refusal(
        HierarchyError.code,
        HierarchyError,
        400,
        "the symbol does not sit where that needs it to",
        retry=False,
    ),
    Refusal(
        ProgressionError.code,
        ProgressionError,
        400,
        "the progression cannot produce that",
        retry=False,
    ),
    Refusal(
        PinningError.code,
        PinningError,
        400,
        "the brass cannot be stacked that way",
        retry=False,
    ),
    Refusal(
        HaspError.code,
        HaspError,
        400,
        "a refusal with nothing more specific to say",
        retry=False,
    ),
)
"""Every refusal, with the status the api gives it and whether to try again."""

BY_CODE = {item.code: item for item in CATALOGUE}


def refusal_for(error: HaspError) -> Refusal:
    """The catalogue entry for a refusal, by the closest class that has one."""
    for kind in type(error).__mro__:
        found = next((item for item in CATALOGUE if item.kind is kind), None)
        if found is not None:
            return found
    return BY_CODE[HaspError.code]


def status_for(error: HaspError) -> int:
    return refusal_for(error).status


def worth_retrying(error: HaspError) -> bool:
    """Whether the same request could succeed if the caller fixed something."""
    return refusal_for(error).retry


def codes() -> tuple[str, ...]:
    return tuple(sorted(BY_CODE))


def catalogue_sheet() -> tuple[str, ...]:
    """The codes as a page, which is what goes in the interface note."""
    lines = [f"{len(CATALOGUE)} refusal codes"]
    lines.extend(f"  {item.describe()}" for item in CATALOGUE)
    return tuple(lines)
