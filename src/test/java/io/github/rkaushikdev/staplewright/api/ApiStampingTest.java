package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.keys.Marking;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.Cli;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiStampingTest {

    private static final String TODAY = "2025-06-30";

    private Api client;

    /** The whole campus, for the routes and the commands alike. */
    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        client = Api.client();
    }

    private static Cli.Result call(String... args) {
        return Cli.onTestDatabase(args);
    }

    private static Cli.Result typed(String input, String... args) {
        String[] all = new String[args.length + 2];
        all[0] = "--database";
        all[1] = TestDatabase.URL;
        System.arraycopy(args, 0, all, 2, args.length);
        return Cli.run(Map.of(), input, all);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> body, String key) {
        return (List<Map<String, Object>>) body.get(key);
    }

    @Test
    void theStampingPolicyComesOffTheEnvironment() {
        ShopSettings settings = Settings.fromMapping(Map.of(
                "STAPLEWRIGHT_STAMP_CHANGE_KEYS", "blind",
                "STAPLEWRIGHT_STAMP_MASTERS", "nothing",
                "STAPLEWRIGHT_STAMP_DND", "no",
                "STAPLEWRIGHT_BOW_CHARACTERS", "6"));
        assertThat(settings.stamping().changeKeys()).isEqualTo(Marking.BLIND);
        assertThat(settings.stamping().masters()).isEqualTo(Marking.NOTHING);
        assertThat(settings.stamping().addDoNotDuplicate()).isFalse();
        assertThat(settings.stamping().bowCharacters()).isEqualTo(6);
    }

    @Test
    void aMarkingCanBeNamedTheLongWay() {
        assertThat(Settings.fromMapping(Map.of("STAPLEWRIGHT_STAMP_MASTERS", "the key symbol"))
                        .stamping()
                        .masters())
                .isEqualTo(Marking.SYMBOL);
    }

    @Test
    void aMarkingNobodyRecognisesIsRefused() {
        assertThatThrownBy(() -> Settings.fromMapping(Map.of("STAPLEWRIGHT_STAMP_MASTERS", "engraved")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void aBowThatHoldsNothingIsRefused() {
        assertThatThrownBy(() -> Settings.fromMapping(Map.of("STAPLEWRIGHT_BOW_CHARACTERS", "2")))
                .isInstanceOf(InputError.class);
    }

    @Test
    void theDescriptionSaysWhatGetsStamped() {
        assertThat(Settings.DEFAULTS.describe()).anyMatch(line -> line.startsWith("stamping:"));
    }

    @Test
    void assigningACodeGivesANumber() {
        Api.Answer found = client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY));
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json()).isEqualTo(Map.of("symbol", "AA", "code", "4000"));
    }

    @Test
    void aSymbolKeepsTheCodeItWasGiven() {
        client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY));
        assertThat(client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY))
                        .json())
                .containsEntry("code", "4000");
    }

    @Test
    void aNumberReadsBackAsASymbol() {
        client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY));
        assertThat(client.get("/stamping/identify/4000").json()).containsEntry("symbol", "AA");
    }

    @Test
    void aNumberNobodyStampedIsA404() {
        assertThat(client.get("/stamping/identify/9999").status()).isEqualTo(404);
    }

    @Test
    void aSymbolNobodyStampedHasNoCode() {
        assertThat(client.get("/stamping/codes/AA").status()).isEqualTo(404);
    }

    @Test
    void theSymbolsOutAreListed() {
        assertThat(client.get("/stamping/symbols").json().get("out")).isEqualTo(List.of("A", "AA", "AA1", "GGM"));
    }

    @Test
    void theDefaultPolicyLeavesNothingBlank() {
        assertThat(client.get("/stamping/blank").json().get("tags")).isEqualTo(List.of());
    }

    @Test
    void oneKeyComesBackWithWhatGoesOnIt() {
        Map<String, Object> body =
                client.get("/stamping/keys/41207", "on", TODAY).json();
        assertThat(body).containsEntry("text", "AA1").containsEntry("blind", false);
    }

    @Test
    void aMasterKeyComesBackBlind() {
        Map<String, Object> body =
                client.get("/stamping/keys/41211", "on", TODAY).json();
        assertThat(body).containsEntry("blind", true).containsEntry("text", "4000");
    }

    @Test
    void aKeyNobodyIssuedIsA404() {
        assertThat(client.get("/stamping/keys/99999").status()).isEqualTo(404);
    }

    @Test
    void aRunComesBackInTagOrder() {
        Map<String, Object> body = client.post(
                        "/stamping/runs", Map.of("tags", List.of("41211", "41207"), "stamped_on", TODAY))
                .json();
        assertThat(rows(body, "stamps").stream().map(stamp -> stamp.get("tag"))).containsExactly("41207", "41211");
    }

    @Test
    void aRunWithNoTagsIsA422() {
        assertThat(client.post("/stamping/runs", Map.of("tags", List.of(), "stamped_on", TODAY))
                        .status())
                .isEqualTo(422);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theSheetCoversEveryKeyOut() {
        List<String> lines =
                (List<String>) client.get("/stamping/sheet", "on", TODAY).json().get("lines");
        assertThat(lines.get(0)).isEqualTo("5 keys to stamp");
    }

    @Test
    void theWholeBookWarnsBeforeItPrints() {
        client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY));
        Map<String, Object> body = client.get("/stamping/book").json();
        assertThat((String) body.get("warning")).startsWith("this table turns");
        assertThat(body.get("codes")).isEqualTo(List.of(Map.of("symbol", "AA", "code", "4000")));
    }

    @Test
    void aCodeStillOnAKeyCannotBeRetired() {
        client.post("/stamping/codes", Map.of("symbol", "AA", "assigned_on", TODAY));
        assertThat(client.delete("/stamping/codes/AA").status()).isEqualTo(409);
    }

    @Test
    void aCodeNobodyCarriesCanBeRetired() {
        client.post("/stamping/codes", Map.of("symbol", "AB1", "assigned_on", TODAY));
        assertThat(client.delete("/stamping/codes/AB1").json()).containsEntry("retired", true);
    }

    @Test
    void theIdentifyCommandAnswersAFoundKey() {
        call("stamping", "assign", "AA", "--on", TODAY);
        assertThat(call("stamping", "identify", "4000").output().strip()).isEqualTo("AA");
    }

    @Test
    void theIdentifyCommandRefusesANumberNobodyStamped() {
        assertThat(call("stamping", "identify", "9999").exitCode()).isNotZero();
    }

    @Test
    void theAssignCommandPrintsThePair() {
        assertThat(call("stamping", "assign", "AA", "--on", TODAY).output().strip())
                .isEqualTo("AA is 4000");
    }

    @Test
    void theCodeCommandReadsItBack() {
        call("stamping", "assign", "AA", "--on", TODAY);
        assertThat(call("stamping", "code", "AA").output().strip()).isEqualTo("4000");
    }

    @Test
    void theKeyCommandDescribesOneStamp() {
        assertThat(call("stamping", "key", "41207", "--on", TODAY).output().strip())
                .isEqualTo("41207 AA1 (the key symbol)");
    }

    @Test
    void theRunCommandPrintsASheet() {
        assertThat(call("stamping", "run", "41207", "41211", "--on", TODAY)
                        .lines()
                        .get(0))
                .isEqualTo("2 keys to stamp");
    }

    @Test
    void theEverythingCommandCoversTheKeysOut() {
        assertThat(call("stamping", "everything", "--on", TODAY).lines()).hasSize(5);
    }

    @Test
    void theBlankCommandIsQuietByDefault() {
        assertThat(call("stamping", "blank").output()).contains("every key out carries something");
    }

    @Test
    void theSymbolsCommandListsWhatNeedsACode() {
        assertThat(call("stamping", "symbols").lines()).containsExactly("A", "AA", "AA1", "GGM");
    }

    @Test
    void theRetireCommandRefusesWhileKeysCarryIt() {
        call("stamping", "assign", "AA", "--on", TODAY);
        assertThat(call("stamping", "retire", "AA").exitCode()).isNotZero();
    }

    @Test
    void theWholeBookCommandNeedsConfirming() {
        call("stamping", "assign", "AA", "--on", TODAY);
        assertThat(typed("n\n", "stamping", "the-whole-book").output()).doesNotContain("4000");
    }

    @Test
    void theWholeBookCommandWarnsFirst() {
        call("stamping", "assign", "AA", "--on", TODAY);
        String output = typed("y\n", "stamping", "the-whole-book").output();
        assertThat(output).contains("turns every stamped number into a symbol").contains("4000");
    }
}
