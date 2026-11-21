package io.github.rkaushikdev.staplewright.persistence.models;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import jakarta.persistence.PersistenceException;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModelsControlTest {

    private static final LocalDate WHEN = LocalDate.of(2025, 3, 4);
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

    private KeyholderRow aPerson() {
        return aPerson(person -> {});
    }

    private KeyholderRow aPerson(Consumer<KeyholderRow> overrides) {
        KeyholderRow row = new KeyholderRow("kmaki", "Kirsi Maki", "faculty", "geography");
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private IssuedKeyRow aKey() {
        return aKey("41207", key -> {});
    }

    private IssuedKeyRow aKey(Consumer<IssuedKeyRow> overrides) {
        return aKey("41207", overrides);
    }

    private IssuedKeyRow aKey(String tag, Consumer<IssuedKeyRow> overrides) {
        IssuedKeyRow row = new IssuedKeyRow(tag, "AA1", "kmaki", WHEN);
        row.setDepositCents(2500);
        overrides.accept(row);
        session.add(row);
        session.flush();
        return row;
    }

    private static KeyRequestRow aRequest(String keyholder, String reason) {
        KeyRequestRow row = new KeyRequestRow("R-001", keyholder, "AA1", "MOO", WHEN);
        row.setReason(reason);
        return row;
    }

    @Test
    void theControlTablesAreOnTheMetadata() {
        assertThat(Schema.tableNames())
                .contains("keyholders", "authorizers", "key_requests", "issued_keys", "deposit_lines");
    }

    @Test
    void aKeyholderRoundTrips() {
        KeyholderRow person = aPerson();
        assertThat(person.getStanding()).isEqualTo("active");
        assertThat(person.getEndsOn()).isEmpty();
        assertThat(person.toString()).contains("kmaki");
    }

    @Test
    void aSeparatedRecordNeedsADate() {
        assertThatThrownBy(() -> aPerson(person -> person.setStanding("separated")))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aSeparatedRecordWithADateIsFine() {
        aPerson(person -> {
            person.setStanding("separated");
            person.setSeparatedOn(WHEN);
        });
        assertThat(session.get(KeyholderRow.class, "kmaki").orElseThrow().getSeparatedOn())
                .contains(WHEN);
    }

    @Test
    void anAuthorizerHangsOffAKeyholder() {
        aPerson();
        AuthorizerRow signature = new AuthorizerRow("kmaki", "geography");
        signature.setBuildings("MOO,VOS");
        session.add(signature);
        session.expireAll();
        AuthorizerRow found = session.get(AuthorizerRow.class, "kmaki").orElseThrow();
        assertThat(found.buildingCodes()).containsExactly("MOO", "VOS");
        assertThat(found.isMayApproveMasters()).isFalse();
        assertThat(found.getPerson().getName()).isEqualTo("Kirsi Maki");
        assertThat(found.toString()).contains("kmaki");
    }

    @Test
    void anAuthorizerWithNoBuildingsSignsEverywhere() {
        aPerson();
        session.add(new AuthorizerRow("kmaki", "geography"));
        session.flush();
        assertThat(session.get(AuthorizerRow.class, "kmaki").orElseThrow().buildingCodes())
                .isEmpty();
    }

    @Test
    void anAuthorizerNeedsAKeyholderThatExists() {
        assertThatThrownBy(() -> {
                    session.add(new AuthorizerRow("ghost", "geography"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void deletingAKeyholderTakesTheSignatureWithIt() {
        KeyholderRow person = aPerson();
        session.add(AuthorizerRow.of(person, "geography"));
        session.flush();
        session.delete(person);
        session.flush();
        assertThat(session.all(AuthorizerRow.class)).isEmpty();
    }

    @Test
    void aRequestRoundTrips() {
        aPerson();
        session.add(aRequest("kmaki", "moved into 214"));
        session.flush();
        KeyRequestRow found = session.get(KeyRequestRow.class, "R-001").orElseThrow();
        assertThat(found.getState()).isEqualTo("raised");
        assertThat(found.isMaster()).isFalse();
        assertThat(found.toString()).contains("R-001");
    }

    @Test
    void aDecidedRequestNeedsADate() {
        aPerson();
        KeyRequestRow row = aRequest("kmaki", "moved in");
        row.setState("approved");
        assertThatThrownBy(() -> {
                    session.add(row);
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aRequestNeedsAKeyholderThatExists() {
        assertThatThrownBy(() -> {
                    session.add(aRequest("ghost", "moved in"));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aKeyRoundTripsAgainstItsHolder() {
        aPerson();
        aKey();
        session.expireAll();
        IssuedKeyRow key = session.get(IssuedKeyRow.class, "41207").orElseThrow();
        assertThat(key.isOut()).isTrue();
        assertThat(key.getHolder().getDepartment()).isEqualTo("geography");
        assertThat(key.toString()).contains("41207");
    }

    @Test
    void aNegativeDepositIsRefused() {
        aPerson();
        assertThatThrownBy(() -> aKey(key -> key.setDepositCents(-1))).isInstanceOf(PersistenceException.class);
    }

    @Test
    void aKeyCannotBeDueBeforeItWentOut() {
        aPerson();
        assertThatThrownBy(() -> aKey(key -> key.setDueOn(LocalDate.of(2025, 1, 1))))
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aKeyDueOnTheDayItWentOutIsAllowed() {
        aPerson();
        aKey(key -> key.setDueOn(WHEN));
        assertThat(session.get(IssuedKeyRow.class, "41207").orElseThrow().getDueOn())
                .contains(WHEN);
    }

    @Test
    void aKeyholderListsTheKeysTheyHaveHad() {
        KeyholderRow person = aPerson();
        aKey();
        aKey("41208", key -> key.setSymbol("AA"));
        session.refresh(person);
        assertThat(person.getKeys().stream().map(IssuedKeyRow::getTag).sorted()).containsExactly("41207", "41208");
    }

    @Test
    void aKeyCannotBeIssuedToSomebodyWhoIsNotThere() {
        assertThatThrownBy(() -> aKey(key -> key.setKeyholder("ghost"))).isInstanceOf(PersistenceException.class);
    }

    @Test
    void depositLinesHangOffTheKey() {
        aPerson();
        IssuedKeyRow key = aKey();
        session.addAll(List.of(
                new DepositLineRow("41207", "kmaki", "charge", 2500, WHEN),
                new DepositLineRow("41207", "kmaki", "refund", 2500, LATER)));
        session.flush();
        session.refresh(key);
        assertThat(key.getDeposits()).hasSize(2);
        assertThat(key.getDeposits().stream()
                        .mapToInt(DepositLineRow::signedCents)
                        .sum())
                .isZero();
    }

    @Test
    void aLineThatMovesNothingIsRefused() {
        aPerson();
        aKey();
        assertThatThrownBy(() -> {
                    session.add(new DepositLineRow("41207", "kmaki", "charge", 0, WHEN));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aLineNeedsAKeyThatExists() {
        assertThatThrownBy(() -> {
                    session.add(new DepositLineRow("99999", "kmaki", "charge", 100, WHEN));
                    session.flush();
                })
                .isInstanceOf(PersistenceException.class);
    }

    @Test
    void aChargeAddsAndEverythingElseTakesAway() {
        aPerson();
        aKey();
        DepositLineRow charge = new DepositLineRow("41207", "kmaki", "charge", 2500, WHEN);
        DepositLineRow forfeit = new DepositLineRow("41207", "kmaki", "forfeit", 2500, LATER);
        session.addAll(List.of(charge, forfeit));
        session.flush();
        assertThat(charge.signedCents()).isEqualTo(2500);
        assertThat(forfeit.signedCents()).isEqualTo(-2500);
        assertThat(forfeit.toString()).contains("forfeit");
    }
}
