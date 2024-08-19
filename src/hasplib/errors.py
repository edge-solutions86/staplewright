"""Every way this library refuses.

The shop cares about two very different kinds of refusal. One is a mistake in
what somebody typed, and the counter fixes it and tries again. The other is a
statement that the metal cannot do what was asked, and that one has to be
readable months later when the same question comes back. So a refusal carries
a short stable code, a sentence a person can read, and whatever facts made the
answer come out that way.
"""

from __future__ import annotations

from typing import Any


class HaspError(Exception):
    """Base for everything this library raises on purpose."""

    code = "hasp_error"

    def __init__(self, message: str, **facts: Any) -> None:
        super().__init__(message)
        self.message = message
        self.facts: dict[str, Any] = dict(facts)

    def __str__(self) -> str:
        if not self.facts:
            return self.message
        rendered = ", ".join(f"{k}={self.facts[k]!r}" for k in sorted(self.facts))
        return f"{self.message} ({rendered})"

    def as_dict(self) -> dict[str, Any]:
        """The shape the api and the printed reports both want."""
        return {"code": self.code, "message": self.message, "facts": dict(self.facts)}


class InputError(HaspError):
    """Somebody typed something the library cannot read."""

    code = "input"


class SpecificationError(HaspError):
    """A manufacturer specification is inconsistent or was used wrongly."""

    code = "specification"


class BittingError(HaspError):
    """A cut sequence is not one the specification allows."""

    code = "bitting"


class KeywayError(HaspError):
    """A blank or a keyway section will not do what was asked of it."""

    code = "keyway"


class HierarchyError(HaspError):
    """A key symbol or a level does not fit the schedule it was handed to."""

    code = "hierarchy"


class ProgressionError(HaspError):
    """The array cannot be progressed the way the system was set up."""

    code = "progression"


class PinningError(HaspError):
    """The stack the shop would have to build does not exist in metal."""

    code = "pinning"


class CapacityError(HaspError):
    """The system has run out of the thing being asked for."""

    code = "capacity"


class NotFoundError(HaspError):
    """Something was looked up by name or number and is not there."""

    code = "not_found"


class ConflictError(HaspError):
    """Two facts that both have to be true are not."""

    code = "conflict"


def error_codes() -> dict[str, type[HaspError]]:
    """Every code this library can produce, keyed by the code itself.

    The api layer needs this to turn a raised error into a status without
    importing each class by hand, and the tests use it to make sure two
    classes never quietly claim the same code.
    """
    found: dict[str, type[HaspError]] = {}
    pending: list[type[HaspError]] = [HaspError]
    while pending:
        cls = pending.pop()
        existing = found.get(cls.code)
        if existing is not None and existing is not cls:
            raise SpecificationError(
                "two error classes claim one code",
                code=cls.code,
                first=existing.__name__,
                second=cls.__name__,
            )
        found[cls.code] = cls
        pending.extend(cls.__subclasses__())
    return found
