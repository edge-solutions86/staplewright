package io.github.rkaushikdev.staplewright.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.support.TestDatabase;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

class CliCommonTest {

    private static final LocalDateTime WHEN = LocalDateTime.of(2025, 4, 8, 14, 12);

    /** A command with the standing day option, printing what it was handed. */
    @Command(name = "run", mixinStandardHelpOptions = true)
    static final class WithDay implements Callable<Integer> {
        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        Optional<LocalDateTime> day = Optional.empty();

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().getOut().println(day.map(String::valueOf).orElse("nothing"));
            return 0;
        }
    }

    /** The same option with help text of its own. */
    @Command(name = "run", mixinStandardHelpOptions = true)
    static final class WithOwnHelp implements Callable<Integer> {
        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = "the day the key came back")
        Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            return 0;
        }
    }

    /** A command with the standing moment option. */
    @Command(name = "run")
    static final class WithMoment implements Callable<Integer> {
        @Option(names = Common.MOMENT, converter = Common.MomentConverter.class, description = Common.MOMENT_HELP)
        Optional<LocalDateTime> moment = Optional.empty();

        @CommandLine.Spec
        CommandLine.Model.CommandSpec spec;

        @Override
        public Integer call() {
            spec.commandLine().getOut().println(moment.map(String::valueOf).orElse("nothing"));
            return 0;
        }
    }

    record Ran(int exitCode, String output) {}

    private static Ran invoke(Object command, String... args) {
        StringWriter printed = new StringWriter();
        PrintWriter out = new PrintWriter(printed, true);
        int code = new CommandLine(command).setOut(out).setErr(out).execute(args);
        return new Ran(code, printed.toString());
    }

    @Test
    void aDayGivenIsTheDayUsed() {
        assertThat(Common.asDay(Optional.of(WHEN))).isEqualTo(LocalDate.of(2025, 4, 8));
    }

    @Test
    void noDayGivenMeansToday() {
        assertThat(Common.asDay(Optional.empty())).isEqualTo(LocalDate.now());
    }

    @Test
    void aMomentGivenIsTheMomentUsed() {
        assertThat(Common.asMoment(Optional.of(WHEN))).isEqualTo(WHEN);
    }

    @Test
    void noMomentGivenMeansNow() {
        LocalDateTime found = Common.asMoment(Optional.empty());
        assertThat(Duration.between(found, LocalDateTime.now()).abs()).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void aCallerCanNameItsOwnFallbackDay() {
        assertThat(Common.asGivenDay(Optional.empty(), LocalDate.of(2020, 1, 1)))
                .isEqualTo(LocalDate.of(2020, 1, 1));
        assertThat(Common.asGivenDay(Optional.of(WHEN), LocalDate.of(2020, 1, 1)))
                .isEqualTo(LocalDate.of(2025, 4, 8));
    }

    @Test
    void theDayOptionIsSpelledOnEverywhere() {
        Ran found = invoke(new WithDay(), "--on", "2025-04-08");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("2025-04-08");
    }

    @Test
    void theDayOptionDefaultsToNothing() {
        assertThat(invoke(new WithDay()).output().strip()).isEqualTo("nothing");
    }

    @Test
    void aDayInTheWrongShapeIsRefused() {
        assertThat(invoke(new WithDay(), "--on", "the eighth").exitCode()).isNotZero();
    }

    @Test
    void theMomentOptionIsSpelledAtEverywhere() {
        Ran found = invoke(new WithMoment(), "--at", "2025-04-08T14:12:00");
        assertThat(found.exitCode()).isZero();
        assertThat(found.output()).contains("14:12");
    }

    @Test
    void theHelpTextCanBeSet() {
        assertThat(invoke(new WithOwnHelp(), "--help").output()).contains("the day the key came back");
    }

    @Test
    void theStandingHelpTextIsUsedWhenNoneIsGiven() {
        assertThat(invoke(new WithDay(), "--help").output()).contains("the day it happened");
    }

    /** A stand in service, so the helper can be tested without a domain. */
    record Service(Session session, String label) {
        Service(Session session) {
            this(session, "plain");
        }
    }

    @Test
    void theWorkingHelperHandsOverAServiceOnASession() {
        List<String> seen = new ArrayList<>();
        List<Session> handed = new ArrayList<>();
        Common.Sessions shop = work -> {
            seen.add("opened");
            TestDatabase.fresh().inSession(session -> {
                handed.add(session);
                work.accept(session);
            });
            seen.add("closed");
        };
        Common.working(shop, Service::new, service -> {
            assertThat(service.session()).isSameAs(handed.get(0));
            assertThat(service.label()).isEqualTo("plain");
        });
        assertThat(seen).containsExactly("opened", "closed");
    }

    @Test
    void theWorkingHelperPassesTheExtraArgumentsThrough() {
        Common.Sessions shop = work -> TestDatabase.fresh().inSession(work);
        List<String> labels = new ArrayList<>();
        Common.working(shop, session -> new Service(session, "wide"), service -> labels.add(service.label()));
        assertThat(labels).containsExactly("wide");
    }

    @Test
    void theSessionClosesEvenWhenTheBodyRaises() {
        List<String> seen = new ArrayList<>();
        Common.Sessions shop = work -> {
            try {
                TestDatabase.fresh().inSession(work);
            } finally {
                seen.add("closed");
            }
        };
        assertThatThrownBy(() -> Common.working(shop, Service::new, service -> {
                    throw new IllegalStateException("something went wrong");
                }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(seen).containsExactly("closed");
    }

    @Test
    void echoingLinesPrintsOnePerLine() {
        StringWriter printed = new StringWriter();
        Common.echoLines(new PrintWriter(printed, true), List.of("first", "second"));
        assertThat(printed.toString().lines()).containsExactly("first", "second");
    }

    @Test
    void echoingNothingPrintsNothing() {
        StringWriter printed = new StringWriter();
        Common.echoLines(new PrintWriter(printed, true), List.of());
        assertThat(printed.toString()).isEmpty();
    }
}
