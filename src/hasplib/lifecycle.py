"""One way of saying no when a record is in the wrong state.

Five classes had each written their own version of this and they had drifted
in the way that matters: two of them put the state in the error facts, two
did not, and the fifth spelled the key differently. So a client trying to
tell a wrong state from a missing record had to know which object it was
talking to, which is exactly the thing an error code exists to avoid.

The message stays with the caller. A work order and a lockout call are not
in the same state for different reasons, and a shared message would be so
vague nobody could act on it.
"""

from __future__ import annotations

from collections.abc import Iterable
from typing import Any

from hasplib.errors import ConflictError


def require_state(current: Any, allowed: Iterable[Any], message: str, **facts: Any) -> None:
    """Refuse unless the record is in one of the states given.

    The state always reaches the facts under the key ``state``, so a caller
    can read it back without knowing what it was talking to.
    """
    wanted = tuple(allowed)
    if current in wanted:
        return
    raise ConflictError(
        message,
        state=str(current),
        allowed=tuple(str(item) for item in wanted),
        **facts,
    )


def require(condition: bool, message: str, **facts: Any) -> None:
    """Refuse unless a plain condition holds. The other half of the same job."""
    if not condition:
        raise ConflictError(message, **facts)


def state_of(value: Any) -> str:
    """However a state is spelled, this is what goes in a message."""
    return str(getattr(value, "value", value))
