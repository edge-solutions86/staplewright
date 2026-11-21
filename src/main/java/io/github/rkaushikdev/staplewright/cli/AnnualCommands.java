package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.models.AnnualReturnRow;
import io.github.rkaushikdev.staplewright.services.AnnualService;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Return commands, typed once a year with somebody from risk in the room.
 *
 * <p>{@code take} and {@code file} are separate commands because they are separate decisions.
 * Everybody runs the numbers first, argues about two of them, fixes whatever was wrong in the
 * ledger, and runs them again. Filing is the last thing that happens and it happens once.
 */
@Command(
        name = "return",
        mixinStandardHelpOptions = true,
        description = "The annual key control return.",
        subcommands = {
            AnnualCommands.Take.class,
            AnnualCommands.FileIt.class,
            AnnualCommands.Withdraw.class,
            AnnualCommands.Trend.class,
            AnnualCommands.Masters.class,
            AnnualCommands.Unaccounted.class,
            AnnualCommands.Concentration.class,
            AnnualCommands.Movement.class
        })
final class AnnualCommands {

    /** What the day option means on every return command. */
    static final String MEASURED = "the day to measure against";

    @ParentCommand
    Main main;

    /** An annual service on a session of its own. */
    void working(Consumer<AnnualService> work) {
        Common.working(main, AnnualService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Run the numbers without committing to them. */
    @Command(
            name = "take",
            mixinStandardHelpOptions = true,
            description = "Run the numbers without committing to them.")
    static final class Take implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = MEASURED)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.sheet(Common.asDay(day))));
            return Main.OK;
        }
    }

    /** Write this year down, once. */
    @Command(name = "file", mixinStandardHelpOptions = true, description = "Write this year down, once.")
    static final class FileIt implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Option(names = "--by", defaultValue = "", description = "who signed it off")
        private String filedBy;

        @Option(names = "--note", defaultValue = "")
        private String note;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = MEASURED)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> {
                AnnualReturnRow row = service.fileReturn(Common.asDay(day), filedBy, note);
                group.out()
                        .println(row.getYear() + " filed: " + row.getKeysOut() + " out, " + row.getUnaccounted()
                                + " unaccounted");
            });
            return Main.OK;
        }
    }

    /** Mark a filed year as not to be used, with the reason on it. */
    @Command(
            name = "withdraw",
            mixinStandardHelpOptions = true,
            description = "Mark a filed year as not to be used, with the reason on it.")
    static final class Withdraw implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Parameters(index = "0", paramLabel = "YEAR")
        private int year;

        @Parameters(index = "1", paramLabel = "REASON")
        private String reason;

        @Override
        public Integer call() {
            group.working(service -> {
                service.withdraw(year, reason);
                group.out().println(year + " withdrawn");
            });
            return Main.OK;
        }
    }

    /** Every filed year side by side. */
    @Command(name = "trend", mixinStandardHelpOptions = true, description = "Every filed year side by side.")
    static final class Trend implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.trend()));
            return Main.OK;
        }
    }

    /** Every master key that is out, and who has it. */
    @Command(
            name = "masters",
            mixinStandardHelpOptions = true,
            description = "Every master key that is out, and who has it.")
    static final class Masters implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.masters()));
            return Main.OK;
        }
    }

    /** The keys nobody can produce. */
    @Command(name = "unaccounted", mixinStandardHelpOptions = true, description = "The keys nobody can produce.")
    static final class Unaccounted implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = MEASURED)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.unaccounted(Common.asDay(day))));
            return Main.OK;
        }
    }

    /** Who holds the most keys, which is where one loss hurts most. */
    @Command(
            name = "concentration",
            mixinStandardHelpOptions = true,
            description = "Who holds the most keys, which is where one loss hurts most.")
    static final class Concentration implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Spec
        private CommandSpec spec;

        @Option(
                names = "--top",
                defaultValue = "5",
                showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
                description = "how many holders to list, at least one")
        private int top;

        @Override
        public Integer call() {
            if (top < 1) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for '--top': " + top + " is not in the range x>=1.");
            }
            group.working(service -> Common.echoLines(
                    group.out(),
                    service.concentration(top).stream()
                            .map(tally -> Text.left(tally.name(), 18) + tally.count())
                            .toList()));
            return Main.OK;
        }
    }

    /** What changed against the last filed year. */
    @Command(
            name = "movement",
            mixinStandardHelpOptions = true,
            description = "What changed against the last filed year.")
    static final class Movement implements Callable<Integer> {
        @ParentCommand
        private AnnualCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = MEASURED)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.againstLastYear(Common.asDay(day))));
            return Main.OK;
        }
    }
}
