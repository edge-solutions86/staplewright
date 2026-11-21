package io.github.rkaushikdev.staplewright.reports;

import static io.github.rkaushikdev.staplewright.support.Campus.aCampus;
import static io.github.rkaushikdev.staplewright.support.Keyed.aRegister;
import static io.github.rkaushikdev.staplewright.support.People.TODAY;
import static io.github.rkaushikdev.staplewright.support.People.aDirectory;
import static io.github.rkaushikdev.staplewright.support.Systems.emptySchedule;
import static io.github.rkaushikdev.staplewright.support.Systems.smallSchedule;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ExportsTest {

    private static final ObjectMapper READER = new ObjectMapper();

    private static KeySymbol sym(String text) {
        return KeySymbol.parse(text);
    }

    private static KeyingSchedule aCutSchedule() {
        KeyingSchedule schedule = smallSchedule();
        schedule.assign(sym("AA1"), Bitting.parse("620446"));
        return schedule;
    }

    private static KeyLedger aLedger() {
        KeyLedger ledger = new KeyLedger(aDirectory());
        ledger.issue("41207", "AA1", "kmaki", TODAY, 2500);
        ledger.issue("41208", "AA", "dsalo", TODAY, LocalDate.of(2025, 5, 1));
        return ledger;
    }

    private static List<String> lines(String text) {
        return Arrays.asList(Text.strip(text).split("\n", -1));
    }

    private static JsonNode parsed(String json) throws Exception {
        return READER.readTree(json);
    }

    @Test
    void aDocumentStartsWithItsHeader() {
        String found = Exports.toText(List.of("one", "two"), List.of(Row.of(1, 2)));
        assertThat(lines(found).get(0)).isEqualTo("one,two");
    }

    @Test
    void aMissingValueComesOutAsAnEmptyField() {
        assertThat(Text.strip(Exports.toText(List.of("one", "two"), List.of(Row.of(1, null)))))
                .endsWith("1,");
    }

    @Test
    void aCommaInAValueIsQuoted() {
        String found = Exports.toText(List.of("one"), List.of(Row.of("a room, shared")));
        assertThat(found).contains("\"a room, shared\"");
    }

    @Test
    void theLineEndingsAreUnix() {
        assertThat(Exports.toText(List.of("one"), List.of(Row.of(1)))).doesNotContain("\r");
    }

    @Test
    void aDocumentWithNoRowsIsJustAHeader() {
        assertThat(lines(Exports.toText(List.of("one", "two"), List.of()))).containsExactly("one,two");
    }

    @Test
    void theScheduleRowsCarryTheLevelAndTheCut() {
        List<Row> rows = Exports.scheduleRows(aCutSchedule().entries());
        Map<Object, Row> bySymbol = new HashMap<>();
        for (Row row : rows) {
            bySymbol.put(row.cell(0).orElseThrow(), row);
        }
        assertThat(bySymbol.get("AA1").cell(1)).contains(3);
        assertThat(bySymbol.get("AA1").cell(3)).contains("620446");
        assertThat(bySymbol.get("AA2").cell(3)).isEmpty();
    }

    @Test
    void theScheduleDocumentHasALineASymbol() {
        KeyingSchedule schedule = aCutSchedule();
        List<String> found = lines(Exports.SCHEDULE.csv(schedule.entries()));
        assertThat(found.get(0)).startsWith("symbol,");
        assertThat(found).hasSize(schedule.size() + 1);
    }

    @Test
    void theOpeningsDocumentSaysYesAndNoForExterior() {
        List<String> found = lines(Exports.OPENINGS.csv(aCampus()));
        List<String> outside =
                found.stream().filter(line -> line.startsWith("MOO-101")).toList();
        assertThat(outside.get(0)).endsWith("north door");
        assertThat(outside.get(0)).contains(",yes,");
    }

    @Test
    void anInteriorDoorSaysNo() {
        List<String> found = lines(Exports.OPENINGS.csv(aCampus()));
        List<String> inside =
                found.stream().filter(line -> line.startsWith("MOO-214")).toList();
        assertThat(inside.get(0)).contains(",no,");
    }

    @Test
    void theCylinderDocumentLeavesAShelvedCylinderBlank() {
        List<String> found = lines(Exports.CYLINDERS.csv(aRegister()));
        List<String> shelved =
                found.stream().filter(line -> line.startsWith("C-1099")).toList();
        assertThat(shelved.get(0)).endsWith(",,,");
    }

    @Test
    void anInstalledCylinderNamesItsDoor() {
        List<String> found = lines(Exports.CYLINDERS.csv(aRegister()));
        List<String> installed =
                found.stream().filter(line -> line.startsWith("C-1001")).toList();
        assertThat(installed.get(0)).contains("MOO-214");
        assertThat(installed.get(0)).contains("lockset");
    }

    @Test
    void theHoldingsDocumentCarriesTheDates() {
        List<String> found = lines(Exports.HOLDINGS.csv(aLedger().out()));
        assertThat(found.get(1)).contains(TODAY.toString());
        assertThat(found.get(2)).contains("2025-05-01");
    }

    @Test
    void aKeyWithNoDueDateLeavesItBlank() {
        List<Row> rows = Exports.holdingRows(aLedger().out());
        assertThat(rows.get(0).cell(4)).isEmpty();
    }

    @Test
    void theDepositComesOffTheBookWhenOneIsGiven() {
        KeyLedger ledger = aLedger();
        DepositBook book = new DepositBook();
        book.charge("41207", "kmaki", 2500, TODAY);
        book.refund("41207", LocalDate.of(2025, 6, 1));
        List<Row> rows = Exports.holdingRows(ledger.out(), book);
        assertThat(rows.get(0).cell(5)).contains(0L);
    }

    @Test
    void withoutABookTheDepositIsWhatTheKeySays() {
        List<Row> rows = Exports.holdingRows(aLedger().out());
        assertThat(rows.get(0).cell(5)).contains(2500L);
    }

    @Test
    void theFilenameCarriesTheKindAndTheDay() {
        assertThat(Exports.filename("holdings", LocalDate.of(2025, 4, 8)))
                .isEqualTo("staplewright-holdings-2025-04-08.csv");
    }

    @Test
    void aTableKnowsItsOwnColumns() {
        assertThat(Exports.SCHEDULE.header().get(0)).isEqualTo("symbol");
        assertThat(Exports.OPENINGS.name()).isEqualTo("openings");
    }

    @Test
    void aTableHandsBackRecordsForJson() {
        List<Map<String, Optional<Object>>> found =
                Exports.SCHEDULE.records(aCutSchedule().entries());
        assertThat(found.get(0).get("symbol")).contains("GGM");
        assertThat(found.get(0).keySet()).isEqualTo(new HashSet<>(Exports.SCHEDULE.header()));
    }

    @Test
    void aTableWritesJsonAPersonCanRead() throws Exception {
        JsonNode found = parsed(Exports.OPENINGS.json(aCampus()));
        assertThat(found.get(0).get("building").asText()).isEqualTo("HEI");
    }

    @Test
    void aMissingValueStaysNullInJson() throws Exception {
        KeyingSchedule schedule = emptySchedule();
        schedule.add(KeySymbol.parse("A"), "LA");
        JsonNode found = parsed(Exports.SCHEDULE.json(schedule.entries()));
        assertThat(found.get(found.size() - 1).get("bitting").isNull()).isTrue();
    }

    @Test
    void aMissingValueIsEmptyInCsv() {
        KeyingSchedule schedule = emptySchedule();
        schedule.add(KeySymbol.parse("A"), "LA");
        List<String> found = lines(Exports.SCHEDULE.csv(schedule.entries()));
        assertThat(found.get(found.size() - 1)).endsWith(",,");
    }

    @Test
    void aTableNamesItsOwnFile() {
        assertThat(Exports.CYLINDERS.filename(LocalDate.of(2025, 4, 8)))
                .isEqualTo("staplewright-cylinders-2025-04-08.csv");
        assertThat(Exports.CYLINDERS.filename(LocalDate.of(2025, 4, 8), "json")).endsWith(".json");
    }

    @Test
    void theTablesCanBeLookedUpByName() {
        assertThat(Exports.table("holdings")).isSameAs(Exports.HOLDINGS);
    }

    @Test
    void aTableNobodyExportsIsA404() {
        assertThatThrownBy(() -> Exports.table("payroll")).isInstanceOf(NotFoundError.class);
    }

    @Test
    void theTableNamesComeBackSorted() {
        assertThat(Exports.tableNames()).containsExactly("cylinders", "holdings", "openings", "schedule");
    }

    @Test
    void aRowWithTheWrongWidthIsCaught() {
        assertThatThrownBy(() -> Exports.toRecords(List.of("a", "b"), List.of(Row.of("only one"))))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
