package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.DbCampus;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ApiPinningTest {

    private static final String SYSTEM = "chester park";
    private static final String GROUP = "moore offices";
    private static final Map<String, Object> KIT = Map.of(
            "bottoms",
                    IntStream.range(0, 10).map(step -> 150 + 15 * step).boxed().toList(),
            "wafers", IntStream.range(1, 10).map(step -> 15 * step).boxed().toList(),
            "drivers",
                    IntStream.range(0, 80).map(step -> 100 + 5 * step).boxed().toList(),
            "count", 40);

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh().inSession(DbCampus::build);
        client = Api.client();
    }

    private static Map<String, Object> order(Map<String, Object> overrides) {
        Map<String, Object> body = new HashMap<>(Map.of("system", SYSTEM, "group", GROUP, "kit", KIT));
        body.putAll(overrides);
        return body;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> stacks(Map<String, Object> body) {
        return (List<Map<String, Object>>) body.get("stacks");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> bins(Map<String, Object> body) {
        return (Map<String, Object>) body.get("bins");
    }

    private static int number(Object value) {
        return ((Number) value).intValue();
    }

    @Test
    void theStacksComeBackAChamberAtATime() {
        Map<String, Object> body =
                client.get("/pinning/" + SYSTEM + "/" + GROUP).json();
        assertThat(body.get("chambers")).isEqualTo(6);
        assertThat(stacks(body).stream().map(stack -> stack.get("chamber"))).containsExactly(1, 2, 3, 4, 5, 6);
    }

    @Test
    void everyStackReachesTheShearLineExactly() {
        Map<String, Object> body =
                client.get("/pinning/" + SYSTEM + "/" + GROUP).json();
        assertThat(stacks(body).stream().map(stack -> stack.get("height")).collect(Collectors.toSet()))
                .containsExactly(480);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theBreaksStartAtTheBottomPin() {
        for (Map<String, Object> stack :
                stacks(client.get("/pinning/" + SYSTEM + "/" + GROUP).json())) {
            assertThat(((List<Object>) stack.get("breaks")).get(0)).isEqualTo(stack.get("bottom"));
        }
    }

    @Test
    void theMasteredChambersAreTheOnesWithWafers() {
        Map<String, Object> body =
                client.get("/pinning/" + SYSTEM + "/" + GROUP).json();
        List<Object> wafered = stacks(body).stream()
                .filter(stack -> !((List<?>) stack.get("wafers")).isEmpty())
                .map(stack -> stack.get("chamber"))
                .toList();
        assertThat(body.get("mastered")).isEqualTo(wafered);
    }

    @Test
    void spoolingReachesEveryDriver() {
        Map<String, Object> body = client.get("/pinning/" + SYSTEM + "/" + GROUP, "profile", "spool")
                .json();
        assertThat(stacks(body).stream().map(stack -> stack.get("profile")).collect(Collectors.toSet()))
                .containsExactly("spool");
    }

    @Test
    void aFamilyNobodyStocksIsRefused() {
        assertThat(client.get("/pinning/" + SYSTEM + "/" + GROUP, "manufacturer", "Yale")
                        .status())
                .isEqualTo(404);
    }

    @Test
    void anotherFamilyGivesAnotherColumn() {
        Map<String, Object> body = client.get(
                        "/pinning/" + SYSTEM + "/" + GROUP, "manufacturer", "Sargent", "family", "LA six pin")
                .json();
        assertThat(stacks(body).stream().map(stack -> stack.get("height")).collect(Collectors.toSet()))
                .containsExactly(480);
    }

    @Test
    void aGroupNobodyDefinedIsA404() {
        assertThat(client.get("/pinning/" + SYSTEM + "/nothing at all").status())
                .isEqualTo(404);
    }

    @Test
    void aSystemNobodyOpenedIsA404() {
        assertThat(client.get("/pinning/nowhere/" + GROUP).status()).isEqualTo(404);
    }

    @Test
    @SuppressWarnings("unchecked")
    void theChartComesBackAsLines() {
        Map<String, Object> body = client.get("/pinning/" + SYSTEM + "/" + GROUP + "/chart", "serial", "C-1001")
                .json();
        assertThat(((List<String>) body.get("lines")).get(0)).isEqualTo("moore offices  C-1001");
    }

    @Test
    void theChartComesBackAsPaperWhenAsked() {
        Api.Answer found = client.get("/pinning/" + SYSTEM + "/" + GROUP + "/chart", "plain", true);
        assertThat(found.contentType())
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(found.text().lines().findFirst()).contains("moore offices");
    }

    @Test
    void theKeysComeBackInScheduleOrder() {
        Map<String, Object> body =
                client.get("/pinning/" + SYSTEM + "/" + GROUP + "/keys").json();
        assertThat(body.get("symbols")).isEqualTo(List.of("GGM", "A", "AA", "AA1"));
        assertThat(body.get("bittings")).isEqualTo(List.of("442446", "642446", "622446", "620446"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void theCylindersInAGroupComeBackWithWhereTheyAre() {
        List<Map<String, Object>> rows = (List<Map<String, Object>>)
                client.get("/pinning/groups/" + GROUP + "/cylinders").json().get("cylinders");
        assertThat(rows.stream().map(row -> row.get("serial"))).containsExactly("C-1001", "C-1002");
        assertThat((String) rows.get(0).get("installed_at")).isNotEmpty();
    }

    @Test
    void drawingBrassTakesItOutOfTheKit() {
        Map<String, Object> body = client.post("/pinning/draw", order(Map.of())).json();
        assertThat(body.get("drawn")).isEqualTo(body.get("pieces"));
        assertThat(number(body.get("left"))).isEqualTo(40 * (10 + 9 + 80) - number(body.get("drawn")));
    }

    @Test
    void theDrawCountsTheBinsItEmptied() {
        Map<String, Object> body = client.post("/pinning/draw", order(Map.of())).json();
        assertThat(bins(body).get("bottom")).isEqualTo(6);
        assertThat(bins(body).get("driver")).isEqualTo(6);
        assertThat(number(bins(body).get("bottom"))
                        + number(bins(body).get("driver"))
                        + number(bins(body).get("wafer")))
                .isEqualTo(number(body.get("pieces")));
    }

    @Test
    void aKitThatCannotFillItIsA409() {
        Map<String, Object> thin =
                Map.of("bottoms", List.of(195), "wafers", List.of(30), "drivers", List.of(285), "count", 1);
        Api.Answer found = client.post("/pinning/draw", order(Map.of("kit", thin)));
        assertThat(found.status()).isEqualTo(409);
        assertThat(found.json().get("message")).isEqualTo("the kit cannot fill that");
    }

    @Test
    void aKitWithAnEmptyBinListIsRefused() {
        Map<String, Object> thin =
                Map.of("bottoms", List.of(), "wafers", List.of(30), "drivers", List.of(285), "count", 1);
        assertThat(client.post("/pinning/draw", order(Map.of("kit", thin))).status())
                .isEqualTo(422);
    }

    @Test
    void drawingForAGroupNobodyDefinedIsA404() {
        assertThat(client.post("/pinning/draw", order(Map.of("group", "nothing at all")))
                        .status())
                .isEqualTo(404);
    }
}
