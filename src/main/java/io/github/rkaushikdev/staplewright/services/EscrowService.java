package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.BoxPolicy;
import io.github.rkaushikdev.staplewright.control.BoxRegister;
import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.KeyBox;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.EscrowMappers;
import io.github.rkaushikdev.staplewright.persistence.models.BuildingRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SealRow;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sealed boxes over a database, with the seal history kept in step.
 *
 * <p>Every change here does two things that have to happen together: it moves the box row, and it
 * opens or closes a seal row. A box row that says sealed with no open seal behind it is a box whose
 * key nobody can name, and a seal left open under a box that was resealed is a box that appears to
 * have had two keys in it at once.
 *
 * <p>This layer also checks the symbol against a real schedule. The register refuses a symbol too
 * far up the tree, but it has no idea whether the symbol exists, and a box sealed on a key nobody
 * ever cut is a box with something unidentified in it.
 */
public final class EscrowService {

    private final Session session;
    private final BoxPolicy policy;
    private final BoxRepository boxes;

    public EscrowService(Session session) {
        this(session, new BoxPolicy());
    }

    public EscrowService(Session session, BoxPolicy policy) {
        this.session = session;
        this.policy = policy;
        this.boxes = new BoxRepository(session);
    }

    public Session session() {
        return session;
    }

    public BoxRepository boxes() {
        return boxes;
    }

    /** Every box on file, as the register the rules run on. */
    public BoxRegister register() {
        return EscrowMappers.registerFromRows(boxes.all(), policy);
    }

    /** The box's row, refusing a number nobody installed. */
    public KeyBoxRow row(String number) {
        return boxes.require(number, "no such key box", Facts.of("box", number));
    }

    public KeyBox box(String number) {
        return EscrowMappers.boxFromRow(row(number));
    }

    /** Put a box up, once the wall and the key both check out. */
    public KeyBoxRow install(
            String number, String building, String opening, KeySymbol symbol, String seal, LocalDate when) {
        mustBeABuilding(building);
        mustBeAnOpening(opening, building);
        mustBeCut(symbol);
        KeyBox box = register().install(new KeyBox(number, building, opening, symbol, seal, when));
        KeyBoxRow row = EscrowMappers.boxToRow(box);
        session.add(row);
        session.add(EscrowMappers.sealToRow(box));
        session.flush();
        return row;
    }

    /** Read the tag. A number that does not match closes the seal on the record. */
    public KeyBoxRow check(String number, String seal, LocalDate when) {
        KeyBoxRow row = row(number);
        KeyBox box = register().check(number, seal, when);
        if (box.state() == BoxState.OPENED) {
            closeCurrent(row, when, OpenedBy.UNKNOWN.value(), box.note());
        }
        return EscrowMappers.applyToRow(row, box);
    }

    public KeyBoxRow reportOpened(String number, OpenedBy by, LocalDate when) {
        return reportOpened(number, by, when, "");
    }

    /** Somebody says they opened it: the seal closes, naming who. */
    public KeyBoxRow reportOpened(String number, OpenedBy by, LocalDate when, String note) {
        KeyBoxRow row = row(number);
        KeyBox box = register().reportOpened(number, by, when, note);
        closeCurrent(row, when, by.value(), note);
        return EscrowMappers.applyToRow(row, box);
    }

    public KeyBoxRow empty(String number, LocalDate when) {
        return empty(number, when, "");
    }

    /** The key has been taken out of an open box. */
    public KeyBoxRow empty(String number, LocalDate when, String note) {
        KeyBoxRow row = row(number);
        return EscrowMappers.applyToRow(row, register().empty(number, when, note));
    }

    /** A new key and a new seal, and a new row on the history. */
    public KeyBoxRow reseal(String number, String seal, KeySymbol symbol, LocalDate when) {
        mustBeCut(symbol);
        KeyBoxRow row = row(number);
        KeyBox box = register().reseal(number, seal, symbol, when);
        closeCurrent(row, when, "", "");
        EscrowMappers.applyToRow(row, box);
        session.add(EscrowMappers.sealToRow(box));
        session.flush();
        return row;
    }

    /** Take the box off the wall. */
    public KeyBoxRow withdraw(String number, LocalDate when, String note) {
        KeyBoxRow row = row(number);
        KeyBox box = register().withdraw(number, when, note);
        closeCurrent(row, when, "", note);
        return EscrowMappers.applyToRow(row, box);
    }

    /** Every seal the box has carried, oldest first. */
    public List<SealRow> history(String number) {
        row(number);
        return EscrowMappers.sealHistory(seals(number));
    }

    /** The seal on the box now, if it has one. */
    public Optional<SealRow> sealNow(String number) {
        row(number);
        return EscrowMappers.currentSeal(seals(number));
    }

    /** Which key was in this box on a day, which is the whole point. */
    public Optional<KeySymbol> keyOn(String number, LocalDate when) {
        row(number);
        return EscrowMappers.symbolOn(seals(number), when);
    }

    public List<KeyBox> openBoxes() {
        return register().openBoxes();
    }

    public List<KeyBox> dueForCheck(LocalDate today) {
        return register().dueForCheck(today);
    }

    public List<KeyBox> overdueReseal(LocalDate today) {
        return register().overdueReseal(today);
    }

    /** Every box the walk round has to stop at today. */
    public List<KeyBox> walkRound(LocalDate today) {
        return BoxRegister.boxesNeedingWork(register(), today);
    }

    /** Buildings with no live box, which is the fire brigade's problem. */
    public List<String> uncovered() {
        List<String> codes = session.list("select b.code from BuildingRow b order by b.code", String.class);
        return BoxRegister.coverage(register(), codes);
    }

    public Map<String, Integer> exposure() {
        return register().exposure();
    }

    private List<SealRow> seals(String number) {
        return session.list("select s from SealRow s where s.boxNumber = ?1 order by s.id", SealRow.class, number);
    }

    private void closeCurrent(KeyBoxRow row, LocalDate when, String by, String note) {
        Optional<SealRow> held = EscrowMappers.currentSeal(seals(row.getNumber()));
        if (held.isEmpty()) {
            return;
        }
        EscrowMappers.closeSeal(held.get(), when, by, note);
        session.flush();
    }

    private void mustBeABuilding(String code) {
        if (session.get(BuildingRow.class, code).isEmpty()) {
            throw new NotFoundError("no such building", Facts.of("building", code));
        }
    }

    private void mustBeAnOpening(String number, String building) {
        OpeningRow row = session.get(OpeningRow.class, number)
                .orElseThrow(() -> new NotFoundError("no such opening", Facts.of("opening", number)));
        if (!row.getBuildingCode().equals(building)) {
            throw new ConflictError(
                    "that opening is on another building",
                    Facts.of("opening", number, "building", building, "actually", row.getBuildingCode()));
        }
        if (boxes.onOpening(number).isPresent()) {
            throw new ConflictError("that opening already has a box", Facts.of("opening", number));
        }
    }

    private void mustBeCut(KeySymbol symbol) {
        if (session.first(
                        "select e from ScheduleEntryRow e where e.symbol = ?1 and e.bitting is not null",
                        ScheduleEntryRow.class,
                        symbol)
                .isEmpty()) {
            throw new ConflictError(
                    "nothing on any schedule is cut to that symbol", Facts.of("symbol", symbol.render()));
        }
    }
}
