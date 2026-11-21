package io.github.rkaushikdev.staplewright.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.support.Api;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

class ApiWiringTest {

    /** A stand in service, so the wiring can be tested without a domain. */
    static final class Counter {

        static final List<Integer> SEEN = new ArrayList<>();

        final Session session;
        final String label;

        Counter(Session session) {
            this(session, "plain");
        }

        Counter(Session session, String label) {
            this.session = session;
            this.label = label;
            synchronized (SEEN) {
                SEEN.add(System.identityHashCode(session));
            }
        }
    }

    /** Routes a test adds beside the service's own. */
    @RestController
    static class Probe {

        private final Work work;

        Probe(Work work) {
            this.work = work;
        }

        @GetMapping("/session")
        Map<String, Boolean> one() {
            return work.apply(session -> Map.of("live", session.isOpen()));
        }

        @GetMapping("/service")
        Map<String, String> two() {
            return work.apply(session -> Map.of("label", new Counter(session).label));
        }

        @GetMapping("/labelled")
        Map<String, String> three() {
            return work.apply(session -> Map.of("label", new Counter(session, "wide").label));
        }

        @GetMapping("/both")
        Map<String, Boolean> four() {
            return work.apply(session -> {
                Counter first = new Counter(session);
                Counter second = new Counter(session, "wide");
                return Map.of("same", first.session == second.session);
            });
        }

        @GetMapping("/page")
        ResponseEntity<?> five(@RequestParam(defaultValue = "false") boolean plain) {
            return Pages.page(List.of("first line", "second line"), plain);
        }
    }

    private Api client;

    @BeforeEach
    void open() {
        TestDatabase.fresh();
        synchronized (Counter.SEEN) {
            Counter.SEEN.clear();
        }
        client = Api.client(Settings.DEFAULTS, Probe.class);
    }

    @Test
    void theSessionDependencyHandsOverALiveSession() {
        assertThat(client.get("/session").json()).isEqualTo(Map.of("live", true));
    }

    @Test
    void aServiceIsBuiltWithItsDefaults() {
        assertThat(client.get("/service").json()).isEqualTo(Map.of("label", "plain"));
    }

    @Test
    void extraArgumentsReachTheService() {
        assertThat(client.get("/labelled").json()).isEqualTo(Map.of("label", "wide"));
    }

    @Test
    void twoServicesInOneRequestShareASession() {
        assertThat(client.get("/both").json()).isEqualTo(Map.of("same", true));
    }

    @Test
    void twoRequestsDoNotShareASession() {
        client.get("/service");
        client.get("/service");
        assertThat(new HashSet<>(Counter.SEEN)).hasSize(2);
    }

    @Test
    void aReportComesBackAsLinesByDefault() {
        assertThat(client.get("/page").json()).isEqualTo(Map.of("lines", List.of("first line", "second line")));
    }

    @Test
    void aReportComesBackAsTextWhenAsked() {
        Api.Answer found = client.get("/page", "plain", true);
        assertThat(found.contentType())
                .hasValueSatisfying(type -> assertThat(type).startsWith("text/plain"));
        assertThat(found.text().lines()).containsExactly("first line", "second line");
    }

    @Test
    void thePlainFlagIsAQueryAndNeverAPath() {
        assertThat(client.get("/page/true").status()).isEqualTo(404);
    }
}
