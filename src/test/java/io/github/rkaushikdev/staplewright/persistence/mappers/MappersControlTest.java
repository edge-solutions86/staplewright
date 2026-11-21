package io.github.rkaushikdev.staplewright.persistence.mappers;

import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.authorizerFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.authorizerToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.depositBookFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.depositFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.depositToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.directoryFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.keyFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.keyToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.keyholderFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.keyholderToRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.ledgerFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.requestBookFromRows;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.requestFromRow;
import static io.github.rkaushikdev.staplewright.persistence.mappers.ControlMappers.requestToRow;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.control.Authorizer;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.DepositEntry;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.KeyRequest;
import io.github.rkaushikdev.staplewright.control.KeyState;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.RequestBook;
import io.github.rkaushikdev.staplewright.control.RequestState;
import io.github.rkaushikdev.staplewright.control.Standing;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.AuthorizerRow;
import io.github.rkaushikdev.staplewright.persistence.models.DepositLineRow;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyholderRow;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MappersControlTest {

    private static final LocalDate LATER = LocalDate.of(2025, 6, 2);

    private Session session;

    @BeforeEach
    void open() {
        session = TestDatabase.fresh().session();
    }

    @AfterEach
    void close() {
        session.close();
    }

    private Directory storeDirectory() {
        Directory directory = aDirectory();
        for (Keyholder person : directory) {
            session.add(keyholderToRow(person));
        }
        session.flush();
        for (Authorizer signer : directory.authorizers()) {
            session.add(authorizerToRow(signer));
        }
        session.flush();
        return directory;
    }

    private static KeyLedger aLedger(Directory directory) {
        KeyLedger ledger = new KeyLedger(directory);
        ledger.issue("41207", "AA1", "kmaki", TODAY, 2500);
        ledger.issue("41208", "AA1", "dsalo", TODAY, 2500);
        ledger.issue("41209", "AA", "rvirta", TODAY);
        ledger.takeBack("41209", LATER);
        return ledger;
    }

    private static KeyRequest movedIn() {
        return new KeyRequest("R-001", "kmaki", "AA1", "MOO", "moved in", TODAY);
    }

    @ParameterizedTest
    @ValueSource(strings = {"kmaki", "tkoski", "jwexler"})
    void aKeyholderSurvivesARoundTrip(String identifier) {
        Keyholder person = aDirectory().person(identifier);
        assertThat(keyholderFromRow(keyholderToRow(person))).isEqualTo(person);
    }

    @Test
    void aSeparatedKeyholderSurvives() {
        Directory directory = aDirectory();
        Keyholder person = directory.separate("kmaki", TODAY);
        Keyholder back = keyholderFromRow(keyholderToRow(person));
        assertThat(back.standing()).isSameAs(Standing.SEPARATED);
        assertThat(back.separatedOn()).contains(TODAY);
    }

    @Test
    void anAffiliationNobodyHasHeardOfIsRefused() {
        KeyholderRow row = keyholderToRow(aDirectory().person("kmaki"));
        row.setAffiliation("visiting hedgehog");
        assertThatThrownBy(() -> keyholderFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void anAuthorizerSurvivesARoundTrip() {
        Authorizer signer = aDirectory().authorizer("rvirta");
        assertThat(authorizerFromRow(authorizerToRow(signer))).isEqualTo(signer);
    }

    @Test
    void anAuthorizerWithNoBuildingsSurvives() {
        Authorizer signer = aDirectory().authorizer("phamm");
        Authorizer back = authorizerFromRow(authorizerToRow(signer));
        assertThat(back.buildings()).isEmpty();
        assertThat(back.mayApproveMasters()).isTrue();
    }

    @Test
    void aWholeDirectoryComesBack() {
        storeDirectory();
        List<KeyholderRow> people = session.all(KeyholderRow.class);
        List<AuthorizerRow> signers = session.all(AuthorizerRow.class);
        Directory back = directoryFromRows(people, signers);
        assertThat(back.size()).isEqualTo(aDirectory().size());
        assertThat(back.mayAuthorize("rvirta", "geography", "MOO", TODAY)).isTrue();
        assertThat(back.person("jwexler").mayHoldKeys(TODAY)).isFalse();
    }

    @Test
    void aRequestSurvivesARoundTrip() {
        KeyRequest request = movedIn();
        assertThat(requestFromRow(requestToRow(request))).isEqualTo(request);
    }

    @Test
    void aDecidedRequestSurvives() {
        Directory directory = aDirectory();
        RequestBook book = new RequestBook(directory);
        book.raiseRequest(movedIn());
        book.submit("R-001");
        KeyRequest approved = book.approve("R-001", "rvirta", TODAY, "fine");
        assertThat(requestFromRow(requestToRow(approved))).isEqualTo(approved);
    }

    @Test
    void aStateNobodyHasHeardOfIsRefused() {
        KeyRequestRow row = requestToRow(movedIn());
        row.setState("pondered");
        assertThatThrownBy(() -> requestFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aWholeRequestBookComesBack() {
        Directory directory = storeDirectory();
        RequestBook book = new RequestBook(directory);
        book.raiseRequest(movedIn());
        book.raiseRequest(new KeyRequest("R-002", "dsalo", "AA1", "MOO", "shares 216", TODAY));
        book.submit("R-002");
        for (KeyRequest request : book) {
            session.add(requestToRow(request));
        }
        session.flush();
        List<KeyRequestRow> rows = session.all(KeyRequestRow.class);
        RequestBook back = requestBookFromRows(directory, rows);
        assertThat(back.size()).isEqualTo(2);
        assertThat(back.request("R-002").state()).isSameAs(RequestState.SUBMITTED);
    }

    @Test
    void aKeySurvivesARoundTrip() {
        KeyLedger ledger = aLedger(aDirectory());
        IssuedKey key = ledger.key("41207");
        assertThat(keyFromRow(keyToRow(key))).isEqualTo(key);
    }

    @Test
    void aClosedKeySurvives() {
        KeyLedger ledger = aLedger(aDirectory());
        IssuedKey key = ledger.key("41209");
        IssuedKey back = keyFromRow(keyToRow(key));
        assertThat(back.state()).isSameAs(KeyState.RETURNED);
        assertThat(back.closedOn()).contains(LATER);
    }

    @Test
    void aKeyStateNobodyHasHeardOfIsRefused() {
        IssuedKeyRow row = keyToRow(aLedger(aDirectory()).key("41207"));
        row.setState("mislaid");
        assertThatThrownBy(() -> keyFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aWholeLedgerComesBack() {
        Directory directory = storeDirectory();
        KeyLedger ledger = aLedger(directory);
        for (IssuedKey key : ledger) {
            session.add(keyToRow(key));
        }
        session.flush();
        List<IssuedKeyRow> rows = session.all(IssuedKeyRow.class);
        KeyLedger back = ledgerFromRows(directory, rows);
        assertThat(back.size()).isEqualTo(ledger.size());
        assertThat(back.out().stream().map(IssuedKey::tag).toList()).containsExactly("41207", "41208");
        assertThat(back.depositsHeld()).isEqualTo(5000);
    }

    @Test
    void aKeyHeldBySomebodyWhoHasSinceSeparatedStillLoads() {
        Directory directory = storeDirectory();
        KeyLedger ledger = aLedger(directory);
        directory.separate("kmaki", LATER);
        List<IssuedKeyRow> rows = new ArrayList<>();
        for (IssuedKey key : ledger) {
            rows.add(keyToRow(key));
        }
        KeyLedger back = ledgerFromRows(directory, rows);
        assertThat(back.heldBy("kmaki").stream().map(IssuedKey::tag).toList()).containsExactly("41207");
    }

    @Test
    void aDepositLineSurvivesARoundTrip() {
        DepositBook book = new DepositBook();
        DepositEntry entry = book.charge("41207", "kmaki", 2500, TODAY);
        assertThat(depositFromRow(depositToRow(entry))).isEqualTo(entry);
    }

    @Test
    void aForfeitSurvives() {
        DepositBook book = new DepositBook();
        book.charge("41207", "kmaki", 2500, TODAY);
        DepositEntry entry = book.forfeit("41207", LATER, "never came back");
        assertThat(depositFromRow(depositToRow(entry)).note()).isEqualTo("never came back");
    }

    @Test
    void aDepositKindNobodyHasHeardOfIsRefused() {
        DepositBook book = new DepositBook();
        DepositEntry entry = book.charge("41207", "kmaki", 2500, TODAY);
        // A deposit line is never rewritten, so the stray kind goes in when the row is made.
        DepositLineRow row =
                new DepositLineRow(entry.tag(), entry.keyholder(), "donation", entry.cents(), entry.when());
        assertThatThrownBy(() -> depositFromRow(row)).isInstanceOf(InputError.class);
    }

    @Test
    void aWholeDepositBookComesBack() {
        Directory directory = storeDirectory();
        KeyLedger ledger = aLedger(directory);
        for (IssuedKey key : ledger) {
            session.add(keyToRow(key));
        }
        session.flush();
        DepositBook book = new DepositBook();
        book.charge("41207", "kmaki", 2500, TODAY);
        book.charge("41208", "dsalo", 2500, TODAY);
        book.refund("41208", LATER);
        for (DepositEntry entry : book) {
            session.add(depositToRow(entry));
        }
        session.flush();
        List<DepositLineRow> rows = session.all(DepositLineRow.class);
        DepositBook back = depositBookFromRows(rows);
        assertThat(back.totalHeld()).isEqualTo(2500);
        assertThat(back.openTags()).containsExactly("41207");
    }
}
