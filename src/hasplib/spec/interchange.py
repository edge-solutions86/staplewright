"""Control keys, which are the most dangerous keys in the building.

An interchangeable core has two shear lines. The operating one turns the lock
and is what the master key system is about. The control one pulls the core
out of the housing, and a person holding a control key does not open a door,
they take the lock away and put their own in.

That is why control keys are not on the master key system at all. A control
key is not above a grand master in the hierarchy, it is beside it, and a
shop that files it as another master eventually gives one to somebody because
they were already trusted with a building master.

The rule that earns its place here is the sleeve rule. Two cores keyed to
different control keys cannot go in the same housing family, because a core
somebody pulls with a control key can be put back in any housing that takes
it, and a campus with two control sections is a campus where half the cores
can be moved into the other half.
"""

from __future__ import annotations

from collections.abc import Iterator, Sequence
from dataclasses import dataclass, replace
from datetime import date
from enum import StrEnum

from hasplib.errors import ConflictError, InputError
from hasplib.keys.bitting import Bitting
from hasplib.store import Registry


class CoreFormat(StrEnum):
    """Which family of housing a core drops into."""

    SFIC_A2 = "small format, A2"
    SFIC_A3 = "small format, A3"
    SFIC_A4 = "small format, A4"
    LARGE = "large format"


class HousingKind(StrEnum):
    """What the core is sitting in."""

    MORTISE = "mortise housing"
    RIM = "rim housing"
    KNOB = "knob or lever"
    PADLOCK = "padlock"
    CABINET = "cabinet lock"


@dataclass(frozen=True)
class ControlKey:
    """One control key, and the cores it can pull."""

    code: str
    section: str
    format: CoreFormat
    bitting: Bitting
    issued_on: date
    holder: str = ""
    withdrawn_on: date | None = None

    def __post_init__(self) -> None:
        if not self.code.strip():
            raise InputError("a control key needs a code")
        if not self.section.strip():
            raise InputError("a control key is cut on a section", code=self.code)

    @property
    def is_live(self) -> bool:
        return self.withdrawn_on is None

    def can_pull(self, core: Core) -> bool:
        """Whether this key takes that core out of its housing."""
        return self.is_live and core.format is self.format and core.control_code == self.code

    def describe(self) -> str:
        held = self.holder or "nobody named"
        state = "live" if self.is_live else f"withdrawn {self.withdrawn_on}"
        return f"{self.code} {self.format.value} held by {held}, {state}"


@dataclass(frozen=True)
class Core:
    """One interchangeable core, and the control key that pulls it."""

    serial: str
    format: CoreFormat
    control_code: str
    housing: HousingKind
    opening: str = ""

    def __post_init__(self) -> None:
        if not self.serial.strip():
            raise InputError("a core has a serial")
        if not self.control_code.strip():
            raise InputError("a core is pulled by some control key", serial=self.serial)

    @property
    def is_installed(self) -> bool:
        return bool(self.opening)

    def fits(self, other: Core) -> bool:
        """Whether these two cores could swap housings, which is the risk."""
        return self.format is other.format and self.housing is other.housing


class ControlRegister:
    """Every control key and every core the shop can account for."""

    def __init__(self) -> None:
        self._keys: Registry[ControlKey] = Registry("control key", "code")
        self._cores: Registry[Core] = Registry("core", "serial")

    def add_key(self, key: ControlKey) -> ControlKey:
        return self._keys.add(key.code, key)

    def add_core(self, core: Core) -> Core:
        """Put a core on the register, refusing one nothing can pull."""
        if core.control_code not in self._keys:
            raise ConflictError(
                "no control key on the register pulls that core",
                serial=core.serial,
                control=core.control_code,
            )
        held = self._keys.get(core.control_code)
        if held.format is not core.format:
            raise ConflictError(
                "that control key is the wrong format for the core",
                serial=core.serial,
                core_format=core.format.value,
                key_format=held.format.value,
            )
        return self._cores.add(core.serial, core)

    def key(self, code: str) -> ControlKey:
        return self._keys.get(code)

    def core(self, serial: str) -> Core:
        return self._cores.get(serial)

    def withdraw(self, code: str, when: date) -> ControlKey:
        """Take a control key out of service, which does not rekey anything."""
        key = self.key(code)
        if not key.is_live:
            raise ConflictError("that control key is already withdrawn", code=code)
        if self.cores_pulled_by(code):
            raise ConflictError(
                "cores on the register are still pulled by that key",
                code=code,
                cores=len(self.cores_pulled_by(code)),
            )
        return self._keys.put(code, replace(key, withdrawn_on=when))

    def hand_to(self, code: str, holder: str) -> ControlKey:
        """Record who has it, which is the whole of control key control."""
        if not holder.strip():
            raise InputError("a control key goes to somebody", code=code)
        key = self.key(code)
        if not key.is_live:
            raise ConflictError("that control key is withdrawn", code=code)
        return self._keys.put(code, replace(key, holder=holder))

    def cores_pulled_by(self, code: str) -> tuple[Core, ...]:
        return self._cores.where(lambda core: core.control_code == code)

    def live_keys(self) -> tuple[ControlKey, ...]:
        return self._keys.where(lambda key: key.is_live)

    def sections(self) -> tuple[str, ...]:
        return tuple(sorted({key.section for key in self.live_keys()}))

    def holders(self) -> dict[str, int]:
        """Who has a control key, and how many, most first."""
        counts: dict[str, int] = {}
        for key in self.live_keys():
            name = key.holder or "nobody named"
            counts[name] = counts.get(name, 0) + 1
        return dict(sorted(counts.items(), key=lambda pair: (-pair[1], pair[0])))

    def unaccounted_cores(self) -> tuple[Core, ...]:
        """Cores whose control key has been withdrawn, which is a hole."""
        live = {key.code for key in self.live_keys()}
        return self._cores.where(lambda core: core.control_code not in live)

    def __contains__(self, code: object) -> bool:
        return code in self._keys

    def __iter__(self) -> Iterator[ControlKey]:
        return iter(self._keys.values())

    def __len__(self) -> int:
        return len(self._keys)


def swappable(cores: Sequence[Core]) -> tuple[tuple[str, str], ...]:
    """Pairs of cores that could be moved into each other's housings.

    Only pairs on different control keys are worth reporting. Two cores on
    one control key are already in the same trust boundary, so moving one is
    not a crossing, it is a rekey somebody did with a screwdriver.
    """
    found: list[tuple[str, str]] = []
    for index, first in enumerate(cores):
        for second in cores[index + 1 :]:
            if first.control_code == second.control_code:
                continue
            if first.fits(second):
                found.append(tuple(sorted((first.serial, second.serial))))  # type: ignore[arg-type]
    return tuple(sorted(set(found)))


def control_sections(register: ControlRegister) -> tuple[str, ...]:
    """Sections carrying a live control key, which should be exactly one."""
    return register.sections()


def crossing_report(register: ControlRegister, cores: Sequence[Core]) -> tuple[str, ...]:
    """Where a core could cross from one control key to another."""
    sections = control_sections(register)
    lines = [f"{len(register.live_keys())} live control keys on {len(sections)} sections"]
    if len(sections) > 1:
        lines.append("more than one control section is in use: " + ", ".join(sections))
    pairs = swappable(cores)
    if not pairs:
        lines.append("no core can be moved across a control key")
        return tuple(lines)
    lines.append(f"{len(pairs)} core pairs could swap housings across control keys")
    lines.extend(f"  {first} and {second}" for first, second in pairs)
    return tuple(lines)


def holder_sheet(register: ControlRegister) -> tuple[str, ...]:
    """Who holds a control key. The shortest page in the building."""
    counts = register.holders()
    if not counts:
        return ("no control key is out",)
    lines = [f"{sum(counts.values())} control keys are out"]
    lines.extend(f"  {name:<20}{count}" for name, count in counts.items())
    return tuple(lines)
