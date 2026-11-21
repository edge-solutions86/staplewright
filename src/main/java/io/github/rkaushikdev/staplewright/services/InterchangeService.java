package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.ControlKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.CoreRow;
import io.github.rkaushikdev.staplewright.persistence.models.OpeningRow;
import io.github.rkaushikdev.staplewright.spec.ControlKey;
import io.github.rkaushikdev.staplewright.spec.ControlRegister;
import io.github.rkaushikdev.staplewright.spec.Core;
import io.github.rkaushikdev.staplewright.spec.CoreFormat;
import io.github.rkaushikdev.staplewright.spec.Crossing;
import io.github.rkaushikdev.staplewright.spec.HousingKind;
import io.github.rkaushikdev.staplewright.spec.Interchange;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Control keys over a database, and the one question worth asking of them.
 *
 * <p>The question is {@link #crossings}. Everything else here is bookkeeping so that question can be
 * answered, and the answer is the pairs of cores that could be moved into each other's housings by
 * two different people. On a campus that has ever bought a lock from a second supplier the list is
 * never empty and nobody has ever looked at it.
 */
public final class InterchangeService {

    private final Session session;
    private final ControlKeyRepository keys;
    private final CoreRepository cores;

    public InterchangeService(Session session) {
        this.session = session;
        this.keys = new ControlKeyRepository(session);
        this.cores = new CoreRepository(session);
    }

    public Session session() {
        return session;
    }

    public ControlKeyRepository keys() {
        return keys;
    }

    public CoreRepository cores() {
        return cores;
    }

    /** A control key as stored. */
    public static ControlKeyRow keyToRow(ControlKey key) {
        ControlKeyRow row =
                new ControlKeyRow(key.code(), key.section(), key.format().value(), key.bitting(), key.issuedOn());
        row.setHolder(key.holder());
        row.setWithdrawnOn(key.withdrawnOn().orElse(null));
        return row;
    }

    /** A stored control key read back. */
    public static ControlKey keyFromRow(ControlKeyRow row) {
        return new ControlKey(
                row.getCode(),
                row.getSection(),
                CoreFormat.fromValue(row.getFormat()),
                row.getBitting(),
                row.getIssuedOn(),
                row.getHolder(),
                row.getWithdrawnOn());
    }

    /** A core as stored, with no door for one on the shelf. */
    public static CoreRow coreToRow(Core core) {
        CoreRow row = new CoreRow(
                core.serial(),
                core.format().value(),
                core.controlCode(),
                core.housing().value());
        row.setInstalledAt(core.opening().isEmpty() ? null : core.opening());
        return row;
    }

    /** A stored core read back. */
    public static Core coreFromRow(CoreRow row) {
        return new Core(
                row.getSerial(),
                CoreFormat.fromValue(row.getFormat()),
                row.getControlCode(),
                HousingKind.fromValue(row.getHousing()),
                row.getInstalledAt().orElse(""));
    }

    /** Every control key and core on file, as the register the rules run on. */
    public ControlRegister register() {
        ControlRegister found = new ControlRegister();
        for (ControlKeyRow key : keys.all()) {
            found.addKey(keyFromRow(key));
        }
        for (CoreRow core : cores.all()) {
            found.addCore(coreFromRow(core));
        }
        return found;
    }

    public ControlKeyRow key(String code) {
        return keys.require(code, "no such control key", Facts.of("code", code));
    }

    public CoreRow core(String serial) {
        return cores.require(serial, "no such core", Facts.of("serial", serial));
    }

    public ControlKeyRow cutKey(String code, String section, CoreFormat format, Bitting bitting, LocalDate when) {
        return cutKey(code, section, format, bitting, when, "");
    }

    /** Cut a new control key, refusing a code already used. */
    public ControlKeyRow cutKey(
            String code, String section, CoreFormat format, Bitting bitting, LocalDate when, String holder) {
        if (keys.find(code).isPresent()) {
            throw new ConflictError("that control key code is already used", Facts.of("code", code));
        }
        ControlKeyRow row = keyToRow(new ControlKey(code, section, format, bitting, when, holder));
        session.add(row);
        session.flush();
        return row;
    }

    public CoreRow registerCore(String serial, CoreFormat format, String controlCode, HousingKind housing) {
        return registerCore(serial, format, controlCode, housing, "");
    }

    /** Put a core on the register, refusing a key of the wrong format or a door nobody has. */
    public CoreRow registerCore(
            String serial, CoreFormat format, String controlCode, HousingKind housing, String opening) {
        ControlKeyRow held = key(controlCode);
        if (!held.getFormat().equals(format.value())) {
            throw new ConflictError(
                    "that control key is the wrong format for the core",
                    Facts.of("serial", serial, "core_format", format.value(), "key_format", held.getFormat()));
        }
        if (!opening.isEmpty() && session.get(OpeningRow.class, opening).isEmpty()) {
            throw new NotFoundError("no such opening", Facts.of("opening", opening));
        }
        CoreRow row = coreToRow(new Core(serial, format, controlCode, housing, opening));
        session.add(row);
        session.flush();
        return row;
    }

    /** Give a live control key to somebody. */
    public ControlKeyRow handTo(String code, String holder) {
        ControlKeyRow row = key(code);
        if (!row.isLive()) {
            throw new ConflictError("that control key is withdrawn", Facts.of("code", code));
        }
        if (Text.isBlank(holder)) {
            throw new ConflictError("a control key goes to somebody", Facts.of("code", code));
        }
        row.setHolder(holder);
        session.flush();
        return row;
    }

    /** Withdraw a control key, refusing while cores on the register still answer to it. */
    public ControlKeyRow withdraw(String code, LocalDate when) {
        ControlKeyRow row = key(code);
        if (!row.isLive()) {
            throw new ConflictError("that control key is already withdrawn", Facts.of("code", code));
        }
        List<CoreRow> held = cores.pulledBy(code);
        if (!held.isEmpty()) {
            throw new ConflictError(
                    "cores on the register are still pulled by that key", Facts.of("code", code, "cores", held.size()));
        }
        row.setWithdrawnOn(when);
        session.flush();
        return row;
    }

    /** Put a core in a door, refusing a housing that is already filled. */
    public CoreRow install(String serial, String opening) {
        CoreRow row = core(serial);
        if (session.get(OpeningRow.class, opening).isEmpty()) {
            throw new NotFoundError("no such opening", Facts.of("opening", opening));
        }
        Optional<CoreRow> held = cores.inHousing(opening, row.getHousing());
        if (held.isPresent() && !held.get().getSerial().equals(serial)) {
            throw new ConflictError(
                    "that housing already has a core in it",
                    Facts.of(
                            "opening",
                            opening,
                            "housing",
                            row.getHousing(),
                            "serial",
                            held.get().getSerial()));
        }
        row.setInstalledAt(opening);
        session.flush();
        return row;
    }

    /** Take a core out, which is what a control key is for. */
    public CoreRow pull(String serial) {
        CoreRow row = core(serial);
        if (!row.isInstalled()) {
            throw new ConflictError("that core is already off the wall", Facts.of("serial", serial));
        }
        row.setInstalledAt(null);
        session.flush();
        return row;
    }

    /** The pairs of cores that could be moved into each other's housings. */
    public List<Crossing> crossings() {
        return Interchange.swappable(
                cores.all().stream().map(InterchangeService::coreFromRow).toList());
    }

    public List<String> report() {
        return Interchange.crossingReport(
                register(),
                cores.all().stream().map(InterchangeService::coreFromRow).toList());
    }

    public List<String> holders() {
        return Interchange.holderSheet(register());
    }

    public List<String> sections() {
        return register().sections();
    }
}
