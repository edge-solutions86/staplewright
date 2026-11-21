package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.health.Finding;
import io.github.rkaushikdev.staplewright.health.Health;
import io.github.rkaushikdev.staplewright.persistence.Schema;
import io.github.rkaushikdev.staplewright.persistence.Seed;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

/**
 * The commands that are about the database rather than about keys: building the schema, listing
 * the tables, printing the settings, filling in an example and running the self check. None of
 * them belong to a group because none of them are about one part of the shop.
 */
final class ServiceCommands {

    private ServiceCommands() {}

    /** Create the tables, or bring an older schema up to date. */
    @Command(name = "build", mixinStandardHelpOptions = true, description = "Create the tables.")
    static final class Build implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            main.database().migrate();
            main.terminal().out().println("schema built at " + main.url());
            return Main.OK;
        }
    }

    /** Print the numbers this shop is running on. */
    @Command(
            name = "settings",
            mixinStandardHelpOptions = true,
            description = "Print the numbers this shop is running on.")
    static final class ShowSettings implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Common.echoLines(main.terminal().out(), main.settings().describe());
            return Main.OK;
        }
    }

    /** List the tables, which is the quickest check that a URL is right. */
    @Command(
            name = "tables",
            mixinStandardHelpOptions = true,
            description = "List the tables, which is the quickest check that a url is right.")
    static final class Tables implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Common.echoLines(main.terminal().out(), Schema.tableNames());
            return Main.OK;
        }
    }

    /** Build the tables and fill them with a worked example campus. */
    @Command(
            name = "demo",
            mixinStandardHelpOptions = true,
            description = "Build the tables and fill them with a worked example campus.")
    static final class Demo implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            LocalDate today = Common.asDay(day);
            main.database().migrate();
            main.inSession(session -> Seed.seed(session, today));
            main.terminal().out().println("demo campus written to " + main.url());
            Common.echoLines(
                    main.terminal().out(),
                    Seed.summary().stream().map(line -> "  " + line).toList());
            return Main.OK;
        }
    }

    /**
     * Run the self checks over the database and say what is wrong. Exits one on a fault and nought on
     * anything else, so a nightly run can be wired to something that shouts without anybody parsing
     * the output.
     */
    @Command(
            name = "check",
            mixinStandardHelpOptions = true,
            description = "Run the self checks over the database and say what is wrong.")
    static final class Check implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Option(names = "--quiet", description = "print nothing, just set the exit code")
        private boolean quiet;

        @Override
        public Integer call() {
            LocalDate today = Common.asDay(day);
            return main.fromSession(session -> {
                List<Finding> findings = Health.runChecks(session, today);
                if (!quiet) {
                    Common.echoLines(main.terminal().out(), Health.healthReport(session, today));
                }
                return Health.exitCode(findings);
            });
        }
    }
}
