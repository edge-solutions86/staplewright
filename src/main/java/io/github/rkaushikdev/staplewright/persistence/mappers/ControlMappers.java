package io.github.rkaushikdev.staplewright.persistence.mappers;

import io.github.rkaushikdev.staplewright.control.Affiliation;
import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.DepositEntry;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.EntryKind;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.KeyRequest;
import io.github.rkaushikdev.staplewright.control.KeyState;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.RequestBook;
import io.github.rkaushikdev.staplewright.control.RequestState;
import io.github.rkaushikdev.staplewright.control.Standing;
import io.github.rkaushikdev.staplewright.persistence.models.AuthorizerRow;
import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.util.Choices;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * People, requests, keys and deposits, between the tables and the domain objects.
 *
 * <p>The ledger is rebuilt the way it was written: keys go in one at a time and then their later
 * states are put back on. That is slower than assigning the final state straight into the
 * collection and it is worth it, because the rebuild goes through the same store method the
 * counter does, so a row that could not have been produced by the counter cannot be produced by a
 * reload either.
 *
 * <p>The one deliberate exception is the standing check on issue. A separated keyholder still has
 * keys out that were issued while they were active, and a reload that refused them would lose
 * exactly the rows the shop most wants to see.
 *
 * <p>Every stored choice reads back through a lookup that refuses text nobody has heard of with an
 * {@link io.github.rkaushikdev.staplewright.InputError}.
 */
public final class ControlMappers {

    private ControlMappers() {}

    /** A new keyholder row, the affiliation and standing stored as their values. */
    public static KeyholderRow keyholderToRow(Keyholder person) {
        KeyholderRow row = new KeyholderRow(
                person.identifier(), person.name(), person.affiliation().value(), person.department());
        row.setStanding(person.standing().value());
        row.setEndsOn(person.endsOn().orElse(null));
        row.setSeparatedOn(person.separatedOn().orElse(null));
        return row;
    }

    /** The keyholder a row holds. */
    public static Keyholder keyholderFromRow(KeyholderRow row) {
        return new Keyholder(
                row.getIdentifier(),
                row.getName(),
                Choices.fromValue(Affiliation.class, row.getAffiliation(), "affiliation"),
                row.getDepartment(),
                Choices.fromValue(Standing.class, row.getStanding(), "standing"),
                row.getEndsOn(),
                row.getSeparatedOn());
    }

    /** A new authorizer row, the buildings stored comma separated. */
    public static AuthorizerRow authorizerToRow(Authorizer signer) {
        AuthorizerRow row = new AuthorizerRow(signer.identifier(), signer.department());
        row.setBuildings(String.join(",", signer.buildings()));
        row.setMayApproveMasters(signer.mayApproveMasters());
        return row;
    }

    /** The authorizer a row holds. */
    public static Authorizer authorizerFromRow(AuthorizerRow row) {
        return new Authorizer(row.getIdentifier(), row.getDepartment(), row.buildingCodes(), row.isMayApproveMasters());
    }

    /** The directory the rows hold, every person added before any signature. */
    public static Directory directoryFromRows(List<KeyholderRow> people, List<AuthorizerRow> signers) {
        Directory directory = new Directory();
        for (KeyholderRow row : people) {
            directory.add(keyholderFromRow(row));
        }
        for (AuthorizerRow signer : signers) {
            directory.addAuthorizer(authorizerFromRow(signer));
        }
        return directory;
    }

    /** A new request row, its state stored as the state's value. */
    public static KeyRequestRow requestToRow(KeyRequest request) {
        KeyRequestRow row = new KeyRequestRow(
                request.number(), request.keyholder(), request.symbol(), request.building(), request.raisedOn());
        row.setReason(request.reason());
        row.setState(request.state().value());
        row.setMaster(request.isMaster());
        row.setDecidedOn(request.decidedOn().orElse(null));
        row.setDecidedBy(request.decidedBy());
        row.setNote(request.note());
        return row;
    }

    /** The request a row holds. */
    public static KeyRequest requestFromRow(KeyRequestRow row) {
        return new KeyRequest(
                row.getNumber(),
                row.getKeyholder(),
                row.getSymbol(),
                row.getBuilding(),
                row.getReason(),
                row.getRaisedOn(),
                Choices.fromValue(RequestState.class, row.getState(), "request state"),
                row.isMaster(),
                row.getDecidedOn(),
                row.getDecidedBy(),
                row.getNote());
    }

    /** The request book the rows hold. Requests go back in as they were, decided ones included. */
    public static RequestBook requestBookFromRows(Directory directory, List<KeyRequestRow> rows) {
        RequestBook book = new RequestBook(directory);
        rows.stream()
                .sorted(Comparator.comparing(KeyRequestRow::getRaisedOn).thenComparing(KeyRequestRow::getNumber))
                .forEach(row -> book.load(requestFromRow(row)));
        return book;
    }

    /** A new issued key row, its state stored as the state's value. */
    public static IssuedKeyRow keyToRow(IssuedKey key) {
        IssuedKeyRow row = new IssuedKeyRow(key.tag(), key.symbol(), key.keyholder(), key.issuedOn());
        row.setDueOn(key.dueOn().orElse(null));
        row.setDepositCents(key.depositCents());
        row.setState(key.state().value());
        row.setClosedOn(key.closedOn().orElse(null));
        row.setNote(key.note());
        return row;
    }

    /** The issued key a row holds. */
    public static IssuedKey keyFromRow(IssuedKeyRow row) {
        return new IssuedKey(
                row.getTag(),
                row.getSymbol(),
                row.getKeyholder(),
                row.getIssuedOn(),
                row.getDueOn(),
                row.getDepositCents(),
                Choices.fromValue(KeyState.class, row.getState(), "key state"),
                row.getClosedOn(),
                row.getNote());
    }

    /** Every key ever issued, in the order it went out. */
    public static KeyLedger ledgerFromRows(Directory directory, List<IssuedKeyRow> rows) {
        KeyLedger ledger = new KeyLedger(directory);
        rows.stream()
                .sorted(Comparator.comparing(IssuedKeyRow::getIssuedOn).thenComparing(IssuedKeyRow::getTag))
                .forEach(row -> ledger.load(keyFromRow(row)));
        return ledger;
    }

    /** A new deposit line row, its kind stored as the kind's value. */
    public static DepositLineRow depositToRow(DepositEntry entry) {
        DepositLineRow row =
                new DepositLineRow(entry.tag(), entry.keyholder(), entry.kind().value(), entry.cents(), entry.when());
        row.setNote(entry.note());
        return row;
    }

    /** The deposit line a row holds. */
    public static DepositEntry depositFromRow(DepositLineRow row) {
        return new DepositEntry(
                row.getTag(),
                row.getKeyholder(),
                Choices.fromValue(EntryKind.class, row.getKind(), "deposit kind"),
                row.getCents(),
                row.getHappenedOn(),
                row.getNote());
    }

    /** The deposit book the rows hold, by the day each line happened and then the order it was written. */
    public static DepositBook depositBookFromRows(List<DepositLineRow> rows) {
        DepositBook book = new DepositBook();
        rows.stream()
                .sorted(Comparator.comparing(DepositLineRow::getHappenedOn)
                        .thenComparingInt(row -> Objects.requireNonNullElse(row.getId(), 0)))
                .forEach(row -> book.load(depositFromRow(row)));
        return book;
    }
}
