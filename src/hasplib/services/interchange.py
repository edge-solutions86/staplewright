"""Control keys over a database, and the one question worth asking of them.

The question is ``crossings``. Everything else here is bookkeeping so that
question can be answered, and the answer is a list of pairs of cores that
could be moved into each other's housings by two different people. On a
campus that has ever bought a lock from a second supplier the list is never
empty and nobody has ever looked at it.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy.orm import Session

from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.bitting import Bitting
from hasplib.persistence.models.campus import OpeningRow
from hasplib.persistence.models.interchange import ControlKeyRow, CoreRow
from hasplib.persistence.repositories import Repository
from hasplib.spec.interchange import (
    ControlKey,
    ControlRegister,
    Core,
    CoreFormat,
    HousingKind,
    crossing_report,
    holder_sheet,
    swappable,
)


class ControlKeyRepository(Repository[ControlKeyRow]):
    model = ControlKeyRow
    noun = "control key"
    order = (ControlKeyRow.code,)

    def live(self) -> Sequence[ControlKeyRow]:
        return self.rows(ControlKeyRow.withdrawn_on.is_(None))

    def held_by(self, holder: str) -> Sequence[ControlKeyRow]:
        return self.rows(ControlKeyRow.holder == holder)


class CoreRepository(Repository[CoreRow]):
    model = CoreRow
    noun = "core"
    order = (CoreRow.serial,)

    def pulled_by(self, code: str) -> Sequence[CoreRow]:
        return self.rows(CoreRow.control_code == code)

    def installed(self) -> Sequence[CoreRow]:
        return self.rows(CoreRow.installed_at.is_not(None))

    def on_the_shelf(self) -> Sequence[CoreRow]:
        return self.rows(CoreRow.installed_at.is_(None))


def key_to_row(key: ControlKey) -> ControlKeyRow:
    return ControlKeyRow(
        code=key.code,
        section=key.section,
        format=key.format.value,
        bitting=key.bitting,
        issued_on=key.issued_on,
        holder=key.holder,
        withdrawn_on=key.withdrawn_on,
    )


def key_from_row(row: ControlKeyRow) -> ControlKey:
    return ControlKey(
        code=row.code,
        section=row.section,
        format=CoreFormat(row.format),
        bitting=row.bitting,
        issued_on=row.issued_on,
        holder=row.holder,
        withdrawn_on=row.withdrawn_on,
    )


def core_to_row(core: Core) -> CoreRow:
    return CoreRow(
        serial=core.serial,
        format=core.format.value,
        control_code=core.control_code,
        housing=core.housing.value,
        installed_at=core.opening or None,
    )


def core_from_row(row: CoreRow) -> Core:
    return Core(
        serial=row.serial,
        format=CoreFormat(row.format),
        control_code=row.control_code,
        housing=HousingKind(row.housing),
        opening=row.installed_at or "",
    )


class InterchangeService:
    """Control keys, cores, and where a core could cross a trust boundary."""

    def __init__(self, session: Session) -> None:
        self.session = session
        self.keys = ControlKeyRepository(session)
        self.cores = CoreRepository(session)

    def register(self) -> ControlRegister:
        found = ControlRegister()
        for key in self.keys.all():
            found.add_key(key_from_row(key))
        for core in self.cores.all():
            found.add_core(core_from_row(core))
        return found

    def key(self, code: str) -> ControlKeyRow:
        return self.keys.require(code, "no such control key", code=code)

    def core(self, serial: str) -> CoreRow:
        return self.cores.require(serial, "no such core", serial=serial)

    def cut_key(
        self,
        code: str,
        section: str,
        format: CoreFormat,
        bitting: Bitting,
        when: date,
        holder: str = "",
    ) -> ControlKeyRow:
        if self.keys.find(code) is not None:
            raise ConflictError("that control key code is already used", code=code)
        key = ControlKey(code, section, format, bitting, when, holder)
        row = key_to_row(key)
        self.session.add(row)
        self.session.flush()
        return row

    def register_core(
        self,
        serial: str,
        format: CoreFormat,
        control_code: str,
        housing: HousingKind,
        opening: str = "",
    ) -> CoreRow:
        held = self.key(control_code)
        if held.format != format.value:
            raise ConflictError(
                "that control key is the wrong format for the core",
                serial=serial,
                core_format=format.value,
                key_format=held.format,
            )
        if opening and self.session.get(OpeningRow, opening) is None:
            raise NotFoundError("no such opening", opening=opening)
        core = Core(serial, format, control_code, housing, opening)
        row = core_to_row(core)
        self.session.add(row)
        self.session.flush()
        return row

    def hand_to(self, code: str, holder: str) -> ControlKeyRow:
        row = self.key(code)
        if not row.is_live:
            raise ConflictError("that control key is withdrawn", code=code)
        if not holder.strip():
            raise ConflictError("a control key goes to somebody", code=code)
        row.holder = holder
        self.session.flush()
        return row

    def withdraw(self, code: str, when: date) -> ControlKeyRow:
        row = self.key(code)
        if not row.is_live:
            raise ConflictError("that control key is already withdrawn", code=code)
        held = self.cores.pulled_by(code)
        if held:
            raise ConflictError(
                "cores on the register are still pulled by that key",
                code=code,
                cores=len(held),
            )
        row.withdrawn_on = when
        self.session.flush()
        return row

    def install(self, serial: str, opening: str) -> CoreRow:
        """Put a core in a door, refusing a housing that is already filled."""
        row = self.core(serial)
        if self.session.get(OpeningRow, opening) is None:
            raise NotFoundError("no such opening", opening=opening)
        held = self.cores.one(CoreRow.installed_at == opening, CoreRow.housing == row.housing)
        if held is not None and held.serial != serial:
            raise ConflictError(
                "that housing already has a core in it",
                opening=opening,
                housing=row.housing,
                serial=held.serial,
            )
        row.installed_at = opening
        self.session.flush()
        return row

    def pull(self, serial: str) -> CoreRow:
        """Take a core out, which is what a control key is for."""
        row = self.core(serial)
        if not row.is_installed:
            raise ConflictError("that core is already off the wall", serial=serial)
        row.installed_at = None
        self.session.flush()
        return row

    def crossings(self) -> tuple[tuple[str, str], ...]:
        return swappable([core_from_row(row) for row in self.cores.all()])

    def report(self) -> tuple[str, ...]:
        cores = [core_from_row(row) for row in self.cores.all()]
        return crossing_report(self.register(), cores)

    def holders(self) -> tuple[str, ...]:
        return holder_sheet(self.register())

    def sections(self) -> tuple[str, ...]:
        return self.register().sections()
