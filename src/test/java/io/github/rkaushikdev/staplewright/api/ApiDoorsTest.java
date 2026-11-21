package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.services.LockoutService;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiDoorsTest {

    private static final String WALKED = "2025-05-12";
    private static final String CALLED = "2025-05-12T21:40:00";
    private static final String LATER = "2025-05-12T22:05:00";

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(session -> {
            DbCampus.build(session);
            new LockoutService(session).openSeries();
        });
        client = Api.client();
    }

    private Api.Answer call() {
        return call("MOO-214");
    }

    private Api.Answer call(String opening) {
        return client.post("/lockouts", Map.of("opening", opening, "keyholder", "38210", "called_at", CALLED));
    }

    private Api.Answer attend() {
        return attend("L-00001");
    }

    private Api.Answer attend(String number) {
        return client.post(
                "/lockouts/" + number + "/attendance", Map.of("technician", "rlaakso", "attended_at", LATER));
    }

    private Api.Answer start() {
        return client.post("/surveys", Map.of("building", "MOO", "walked_on", WALKED));
    }

    private Api.Answer line(Map<String, Object> overrides) {
        return line("MOO-214", overrides);
    }

    private Api.Answer line(String opening, Map<String, Object> overrides) {
        Map<String, Object> body =
                new HashMap<>(Map.of("surveyor", "jkoivu", "condition", 1, "deficiencies", List.of(), "note", ""));
        body.putAll(overrides);
        return client.put("/surveys/MOO/" + WALKED + "/" + opening, body);
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Map<String, Object> body, String key) {
        return (T) body.get(key);
    }

    @Test
    void loggingACallGivesBackANumber() {
        Api.Answer found = call();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json()).containsEntry("number", "L-00001");
        assertThat(found.json()).containsEntry("state", "logged");
    }

    @Test
    void aFreshCallHasNoTechnicianAndNoTimes() {
        Map<String, Object> body = call().json();
        assertThat(body).containsEntry("technician", "");
        assertThat(body).containsEntry("attended_at", null);
        assertThat(body).containsEntry("closed_at", null);
    }

    @Test
    void aCallOnADoorNobodyHasIsRefused() {
        assertThat(call("MOO-999").status()).isEqualTo(404);
    }

    @Test
    void attendingPutsANameAndATimeOnIt() {
        call();
        Map<String, Object> body = attend().json();
        assertThat(body).containsEntry("state", "attended");
        assertThat(body).containsEntry("technician", "rlaakso");
        assertThat((String) field(body, "attended_at")).startsWith("2025-05-12T22:05");
    }

    @Test
    void attendingACallNobodyLoggedIsRefused() {
        assertThat(attend("L-09000").status()).isEqualTo(404);
    }

    @Test
    void aDoorCannotBeOpenedBeforeAnybodyAttends() {
        call();
        Api.Answer found =
                client.post("/lockouts/L-00001/entry", Map.of("identified_by", "campus card", "closed_at", LATER));
        assertThat(found.status()).isEqualTo(409);
    }

    @Test
    void openingTheDoorRecordsHowTheyWereIdentified() {
        call();
        attend();
        Api.Answer found =
                client.post("/lockouts/L-00001/entry", Map.of("identified_by", "campus card", "closed_at", LATER));
        assertThat(found.json()).containsEntry("state", "opened");
        assertThat(found.json()).containsEntry("identified_by", "campus card");
    }

    @Test
    void anIdentificationNobodyAcceptsIsRefused() {
        call();
        attend();
        Api.Answer found = client.post(
                "/lockouts/L-00001/entry", Map.of("identified_by", "he seemed alright", "closed_at", LATER));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void refusingNeedsAReasonWrittenDown() {
        call();
        attend();
        Api.Answer found = client.post("/lockouts/L-00001/refusal", Map.of("closed_at", LATER, "note", ""));
        assertThat(found.status()).isEqualTo(422);
    }

    @Test
    void aRefusalClosesTheCall() {
        call();
        attend();
        Api.Answer found = client.post(
                "/lockouts/L-00001/refusal", Map.of("closed_at", LATER, "note", "no card and not on the room list"));
        assertThat(found.json()).containsEntry("state", "refused");
    }

    @Test
    void theOpenCallsAreTheOnesNobodyClosed() {
        call();
        call("MOO-216");
        attend("L-00002");
        client.post("/lockouts/L-00002/entry", Map.of("identified_by", "campus card", "closed_at", LATER));
        Map<String, Object> body = client.get("/lockouts").json();
        assertThat(body).containsEntry("total", 2);
        List<String> open = field(body, "open");
        assertThat(open).hasSize(1);
        assertThat(open.get(0)).contains("L-00001");
    }

    @Test
    void startingASurveyNamesTheDay() {
        Api.Answer found = start();
        assertThat(found.status()).isEqualTo(201);
        assertThat(found.json()).isEqualTo(Map.of("building", "MOO", "walked_on", WALKED));
    }

    @Test
    void aBuildingCannotBeWalkedTwiceInOneDay() {
        start();
        assertThat(start().status()).isEqualTo(409);
    }

    @Test
    void aLineNeedsASurveyToHangOff() {
        assertThat(line(Map.of()).status()).isEqualTo(404);
    }

    @Test
    void aLineComesBackWithTheConditionInWords() {
        start();
        Map<String, Object> body = line(Map.of(
                        "condition", 4, "deficiencies", List.of("frame or strike needs work first")))
                .json();
        assertThat(body).containsEntry("condition", "broken");
        assertThat(body).containsEntry("deficiencies", List.of("frame or strike needs work first"));
    }

    @Test
    void sendingTheSameLineTwiceLeavesOneReading() {
        start();
        line(Map.of("condition", 2));
        line(Map.of("condition", 4, "note", "second look, it is worse than that"));
        Map<String, Object> body = client.get("/surveys/MOO/" + WALKED).json();
        assertThat(body).containsEntry("doors", 1);
        Map<String, Object> conditions = field(body, "conditions");
        assertThat(conditions).containsEntry("broken", 1);
    }

    @Test
    void aDeficiencyNobodyRecognisesIsRefused() {
        start();
        assertThat(line(Map.of("deficiencies", List.of("the door is haunted"))).status())
                .isEqualTo(422);
    }

    @Test
    void theSurveyReadsBackTheBlockingDoors() {
        start();
        line(Map.of("condition", 1));
        line(
                "MOO-216",
                Map.of("condition", 4, "deficiencies", List.of("hardware needs replacing before it is rekeyed")));
        Map<String, Object> body = client.get("/surveys/MOO/" + WALKED).json();
        assertThat(body).containsEntry("doors", 2);
        assertThat(body).containsEntry("blocking", List.of("MOO-216"));
    }

    @Test
    void aSurveyNobodyWalkedIsA404() {
        assertThat(client.get("/surveys/MOO/" + WALKED).status()).isEqualTo(404);
    }

    @Test
    void theLatestSurveyIsTheOneMostRecentlyWalked() {
        start();
        client.post("/surveys", Map.of("building", "MOO", "walked_on", "2025-09-30"));
        Map<String, Object> body = client.get("/buildings/MOO/latest-survey").json();
        assertThat(body).containsEntry("walked_on", "2025-09-30");
    }

    @Test
    void aBuildingNobodyWalkedSaysSoWithoutFailing() {
        Map<String, Object> body = client.get("/buildings/VOS/latest-survey").json();
        Map<String, Object> expected = new HashMap<>();
        expected.put("building", "VOS");
        expected.put("walked_on", null);
        expected.put("doors", 0);
        assertThat(body).isEqualTo(expected);
    }
}
