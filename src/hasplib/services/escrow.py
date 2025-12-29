"""Sealed boxes over a database, with the history kept in step.

Every method here does two things that have to happen together: it moves the
box row, and it opens or closes a seal row. Those cannot be split. A box row
that says sealed with no open seal row behind it is a box whose key nobody
can name, and a seal row left open under a box that was resealed is a box
that appears to have had two keys in it at once.

The other thing this layer adds is the symbol check against a real schedule.
The register refuses a symbol that is too far up the tree, but it has no idea
whether the symbol exists, and a box sealed on a key nobody ever cut is a box
with something unidentified in it.
"""

from __future__ import annotations

from collections.abc import Sequence
from datetime import date

from sqlalchemy import select
from sqlalchemy.orm import Session

from hasplib.control.escrow import (
    BoxPolicy,
    BoxRegister,
    BoxState,
    KeyBox,
    OpenedBy,
    boxes_needing_work,
    coverage,
)
from hasplib.errors import ConflictError, NotFoundError
from hasplib.keys.symbol import KeySymbol
from hasplib.persistence.mappers.escrow import (
    apply_to_row,
    box_from_row,
    box_to_row,
    close_seal,
    current_seal,
    register_from_rows,
    seal_history,
    seal_to_row,
    symbol_on,
)
from hasplib.persistence.models.campus import BuildingRow, OpeningRow
from hasplib.persistence.models.escrow import KeyBoxRow, SealRow
from hasplib.persistence.models.keying import ScheduleEntryRow
from hasplib.persistence.repositories import Repository


class BoxRepository(Repository[KeyBoxRow]):
    model = KeyBoxRow
    noun = "key box"
    order = (KeyBoxRow.number,)

    def in_building(self, code: str) -> Sequence[KeyBoxRow]:
        return self.rows(KeyBoxRow.building_code == code)

    def live(self) -> Sequence[KeyBoxRow]:
        return self.rows(KeyBoxRow.state != "withdrawn")

    def in_state(self, state: str) -> Sequence[KeyBoxRow]:
        return self.rows(KeyBoxRow.state == state)

    def on_opening(self, number: str) -> KeyBoxRow | None:
        return self.one(KeyBoxRow.opening_number == number)


class EscrowService:
    """The boxes on the walls, and the seal history behind them."""

    def __init__(self, session: Session, policy: BoxPolicy | None = None) -> None:
        self.session = session
        self.policy = policy or BoxPolicy()
        self.boxes = BoxRepository(session)

    def register(self) -> BoxRegister:
        return register_from_rows(self.boxes.all(), self.policy)

    def row(self, number: str) -> KeyBoxRow:
        return self.boxes.require(number, "no such key box", box=number)

    def box(self, number: str) -> KeyBox:
        return box_from_row(self.row(number))

    def install(
        self,
        number: str,
        building: str,
        opening: str,
        symbol: KeySymbol,
        seal: str,
        when: date,
    ) -> KeyBoxRow:
        """Put a box up, once the wall and the key both check out."""
        self._must_be_a_building(building)
        self._must_be_an_opening(opening, building)
        self._must_be_cut(symbol)
        register = self.register()
        box = register.install(
            KeyBox(
                number=number,
                building=building,
                opening=opening,
                symbol=symbol,
                seal=seal,
                sealed_on=when,
            )
        )
        row = box_to_row(box)
        self.session.add(row)
        self.session.add(seal_to_row(box))
        self.session.flush()
        return row

    def check(self, number: str, seal: str, when: date) -> KeyBoxRow:
        """Read the tag. A number that does not match closes the seal."""
        row = self.row(number)
        register = self.register()
        box = register.check(number, seal, when)
        if box.state is BoxState.OPENED:
            self._close_current(row, when, str(OpenedBy.UNKNOWN), box.note)
        return apply_to_row(row, box)

    def report_opened(self, number: str, by: OpenedBy, when: date, note: str = "") -> KeyBoxRow:
        row = self.row(number)
        box = self.register().report_opened(number, by, when, note)
        self._close_current(row, when, str(by), note)
        return apply_to_row(row, box)

    def empty(self, number: str, when: date, note: str = "") -> KeyBoxRow:
        row = self.row(number)
        return apply_to_row(row, self.register().empty(number, when, note))

    def reseal(self, number: str, seal: str, symbol: KeySymbol, when: date) -> KeyBoxRow:
        """A new key and a new seal, and a new row on the history."""
        self._must_be_cut(symbol)
        row = self.row(number)
        box = self.register().reseal(number, seal, symbol, when)
        self._close_current(row, when, "", "")
        apply_to_row(row, box)
        self.session.add(seal_to_row(box))
        self.session.flush()
        return row

    def withdraw(self, number: str, when: date, note: str) -> KeyBoxRow:
        row = self.row(number)
        box = self.register().withdraw(number, when, note)
        self._close_current(row, when, "", note)
        return apply_to_row(row, box)

    def history(self, number: str) -> tuple[SealRow, ...]:
        self.row(number)
        return seal_history(self._seals(number))

    def seal_now(self, number: str) -> SealRow | None:
        self.row(number)
        return current_seal(self._seals(number))

    def key_on(self, number: str, when: date) -> KeySymbol | None:
        """Which key was in this box on a day, which is the whole point."""
        self.row(number)
        return symbol_on(self._seals(number), when)

    def open_boxes(self) -> tuple[KeyBox, ...]:
        return self.register().open_boxes()

    def due_for_check(self, today: date) -> tuple[KeyBox, ...]:
        return self.register().due_for_check(today)

    def overdue_reseal(self, today: date) -> tuple[KeyBox, ...]:
        return self.register().overdue_reseal(today)

    def walk_round(self, today: date) -> tuple[KeyBox, ...]:
        return boxes_needing_work(self.register(), today)

    def uncovered(self) -> tuple[str, ...]:
        """Buildings with no live box, which is the fire brigade's problem."""
        codes = self.session.execute(select(BuildingRow.code)).scalars().all()
        return coverage(self.register(), sorted(codes))

    def exposure(self) -> dict[str, int]:
        return self.register().exposure()

    def _seals(self, number: str) -> Sequence[SealRow]:
        statement = select(SealRow).where(SealRow.box_number == number)
        return self.session.execute(statement.order_by(SealRow.id)).scalars().all()

    def _close_current(self, row: KeyBoxRow, when: date, by: str, note: str) -> None:
        held = current_seal(self._seals(row.number))
        if held is None:
            return
        close_seal(held, when, by, note)
        self.session.flush()

    def _must_be_a_building(self, code: str) -> None:
        if self.session.get(BuildingRow, code) is None:
            raise NotFoundError("no such building", building=code)

    def _must_be_an_opening(self, number: str, building: str) -> None:
        row = self.session.get(OpeningRow, number)
        if row is None:
            raise NotFoundError("no such opening", opening=number)
        if row.building_code != building:
            raise ConflictError(
                "that opening is on another building",
                opening=number,
                building=building,
                actually=row.building_code,
            )
        held = self.boxes.on_opening(number)
        if held is not None:
            raise ConflictError("that opening already has a box", opening=number)

    def _must_be_cut(self, symbol: KeySymbol) -> None:
        statement = select(ScheduleEntryRow).where(
            ScheduleEntryRow.symbol == symbol, ScheduleEntryRow.bitting.is_not(None)
        )
        if self.session.execute(statement).scalars().first() is None:
            raise ConflictError(
                "nothing on any schedule is cut to that symbol",
                symbol=symbol.render(),
            )
