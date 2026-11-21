package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.KeyState;
import io.github.rkaushikdev.staplewright.control.Standing;
import io.github.rkaushikdev.staplewright.control.Transfer;
import io.github.rkaushikdev.staplewright.control.TransferBook;
import io.github.rkaushikdev.staplewright.control.TransferPolicy;
import io.github.rkaushikdev.staplewright.control.TransferState;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.KeyRepository;
import io.github.rkaushikdev.staplewright.persistence.Numbering;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers;
import io.github.rkaushikdev.staplewright.persistence.mappers.TransferMappers.Stray;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.persistence.models.TransferRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Handovers over a database, and the checks nobody thinks to write.
 *
 * <p>Two things here are not in the domain book because the book cannot see them. The first is
 * that the person receiving the key has to be somebody who may hold keys at all, and a handover to
 * a keyholder whose appointment ended is a handover to nobody. The second is that the key has to
 * actually be out to the person claiming to have handed it over, which sounds obvious and is the
 * check that catches the real mistake: somebody handing on a key they returned to the counter last
 * year and have not had since.
 *
 * <p>The sweep is the other half. Claims lapse on their own only if something looks at them, so
 * {@link #lapseOverdue} is meant to be run on a clock, and the count it returns is the number worth
 * putting in front of somebody.
 */
public final class TransferService {

    /** The series every handover number comes off. */
    public static final String TRANSFER_SERIES = "key transfers";

    private final Session session;
    private final TransferPolicy policy;
    private final TransferRepository transfers;
    private final KeyRepository keys;
    private final Numbering numbering;

    public TransferService(Session session) {
        this(session, new TransferPolicy());
    }

    public TransferService(Session session, TransferPolicy policy) {
        this.session = session;
        this.policy = policy;
        this.transfers = new TransferRepository(session);
        this.keys = new KeyRepository(session);
        this.numbering = new Numbering(session);
    }

    public Session session() {
        return session;
    }

    /** The handovers on file. */
    public TransferRepository transfers() {
        return transfers;
    }

    /** Open the handover series. Called once, at setup. */
    public void openSeries() {
        numbering.openSeries(TRANSFER_SERIES, "T-", 5, 1);
    }

    /** Every handover on file, as the book the rules run on. */
    public TransferBook book() {
        return TransferMappers.bookFromRows(transfers.all(), policy);
    }

    /** The handover's row, refusing a number nobody claimed. */
    public TransferRow row(String number) {
        return transfers.require(number, "no such handover", Facts.of("transfer", number));
    }

    /** Somebody says they handed a key on. Nothing moves yet. */
    public TransferRow claim(String tag, String passedTo, LocalDate when) {
        IssuedKeyRow key = mustBeOut(tag);
        mustBeAbleToHold(passedTo, when);
        KeySymbol symbol = KeySymbol.parse(key.getSymbol());
        String number = numbering.next(TRANSFER_SERIES);
        Transfer transfer = new Transfer(number, tag, key.getSymbol(), key.getKeyholder(), passedTo, when);
        book().claim(transfer, !symbol.isChangeKey());
        TransferRow row = TransferMappers.transferToRow(transfer);
        session.add(row);
        session.flush();
        return row;
    }

    /** The person receiving the key signs for it, with no note. */
    public TransferRow confirm(String number, LocalDate when) {
        return confirm(number, when, "");
    }

    /** The person receiving the key signs for it, if they may still hold keys. */
    public TransferRow confirm(String number, LocalDate when, String note) {
        TransferRow row = row(number);
        TransferBook book = book();
        mustBeAbleToHold(row.getPassedTo(), when);
        return TransferMappers.applyToRow(row, book.confirm(number, when, note));
    }

    /** The person named says they never got it. */
    public TransferRow refuse(String number, LocalDate when, String note) {
        TransferRow row = row(number);
        return TransferMappers.applyToRow(row, book().refuse(number, when, note));
    }

    public TransferRow cancel(String number, LocalDate when) {
        return cancel(number, when, "");
    }

    /** Whoever claimed the handover takes it back. */
    public TransferRow cancel(String number, LocalDate when, String note) {
        TransferRow row = row(number);
        return TransferMappers.applyToRow(row, book().cancel(number, when, note));
    }

    /** Close one claim nobody confirmed inside the window. */
    public TransferRow lapse(String number, LocalDate today) {
        TransferRow row = row(number);
        return TransferMappers.applyToRow(row, book().lapse(number, today));
    }

    /** Close every claim nobody confirmed inside the window. */
    public List<TransferRow> lapseOverdue(LocalDate today) {
        TransferBook book = book();
        List<TransferRow> found = new ArrayList<>();
        for (Transfer late : book.late(today)) {
            TransferRow row = row(late.number());
            found.add(TransferMappers.applyToRow(row, book.lapse(late.number(), today)));
        }
        session.flush();
        return List.copyOf(found);
    }

    /** Who has this key today, which is the ledger plus the handovers. */
    public String holderOf(String tag, LocalDate today) {
        IssuedKeyRow key = keys.find(tag).orElseThrow(() -> new NotFoundError("no such key", Facts.of("tag", tag)));
        return TransferMappers.holderNow(key, transfers.forTag(tag), today);
    }

    /** Every tag this person actually has today, handovers included, sorted. */
    public List<String> holdings(String identifier, LocalDate today) {
        return TransferMappers.holdingsOf(keys.out(), transfers.all(), identifier, today);
    }

    /** Keys whose ledger holder is not the person who has them, sorted. */
    public List<Stray> strays(LocalDate today) {
        return TransferMappers.strays(keys.out(), transfers.all(), today);
    }

    /** How many keys each person is sitting on without having signed for them. */
    public Map<String, Integer> exposure(LocalDate today) {
        return TransferBook.unconfirmedExposure(book(), today);
    }

    /** The open claims past their window. */
    public List<Transfer> late(LocalDate today) {
        return book().late(today);
    }

    /** Every handover of one key, in the order it passed between hands. */
    public List<Transfer> chainFor(String tag) {
        return book().chainFor(tag);
    }

    /** The open claims waiting on one person's signature. */
    public List<TransferRow> waitingOn(String identifier) {
        return transfers.waitingOn(identifier);
    }

    public List<TransferRow> inState(TransferState state) {
        return transfers.inState(state.value());
    }

    /** Tags the ledger has against a person, before any handover is applied. */
    public static List<String> keysOutTo(Session session, String identifier) {
        return session.list(
                "select k.tag from IssuedKeyRow k where k.keyholder = ?1 and k.state = ?2 order by k.tag",
                String.class,
                identifier,
                KeyState.OUT.value());
    }

    private IssuedKeyRow mustBeOut(String tag) {
        IssuedKeyRow key = keys.require(tag, "no such key", Facts.of("tag", tag));
        if (!key.getState().equals(KeyState.OUT.value())) {
            throw new ConflictError("that key is not out to anybody", Facts.of("tag", tag, "state", key.getState()));
        }
        return key;
    }

    private KeyholderRow mustBeAbleToHold(String identifier, LocalDate when) {
        KeyholderRow person = session.get(KeyholderRow.class, identifier)
                .orElseThrow(() -> new NotFoundError("no such keyholder", Facts.of("keyholder", identifier)));
        if (!person.getStanding().equals(Standing.ACTIVE.value())) {
            throw new ConflictError(
                    "that keyholder may not hold keys",
                    Facts.of("keyholder", identifier, "standing", person.getStanding()));
        }
        if (person.getEndsOn().map(ended -> ended.isBefore(when)).orElse(false)) {
            throw new ConflictError(
                    "that appointment has already ended",
                    Facts.of(
                            "keyholder",
                            identifier,
                            "ended",
                            person.getEndsOn().orElseThrow().toString()));
        }
        return person;
    }
}
