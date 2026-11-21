package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.control.BoxState;
import io.github.rkaushikdev.staplewright.control.OpenedBy;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.models.KeyBoxRow;
import io.github.rkaushikdev.staplewright.reports.EscrowPages;
import io.github.rkaushikdev.staplewright.services.EscrowService;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Box commands, typed while standing in front of the box.
 *
 * <p>The check command is the one that gets used. Somebody walks a corridor, reads the number off
 * the tag, types it in, and either nothing happens or the box they are standing at turns out to have
 * been opened. So it prints one line either way and the line says which it was. The coverage command
 * is the only one here whose output is meant to leave the building, which is why it is separate from
 * the report.
 */
@Command(
        name = "boxes",
        mixinStandardHelpOptions = true,
        description = "Sealed key boxes, and what has happened to their seals.",
        subcommands = {
            BoxCommands.Install.class,
            BoxCommands.Check.class,
            BoxCommands.Opened.class,
            BoxCommands.Empty.class,
            BoxCommands.Reseal.class,
            BoxCommands.Withdraw.class,
            BoxCommands.Seals.class,
            BoxCommands.KeyOn.class,
            BoxCommands.Open.class,
            BoxCommands.Walk.class,
            BoxCommands.Coverage.class,
            BoxCommands.Found.class,
            BoxCommands.Report.class
        })
final class BoxCommands {

    @ParentCommand
    Main main;

    void working(Consumer<EscrowService> work) {
        Common.working(
                main, session -> new EscrowService(session, main.settings().boxes()), work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    static List<String> buildings(Session session) {
        return session.list("select b.code from BuildingRow b order by b.code", String.class);
    }

    /** The day option every box command shares. */
    static final class Day {
        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        Optional<LocalDateTime> when = Optional.empty();
    }

    /** Put a box on a wall with a key and a seal in it. */
    @Command(
            name = "install",
            mixinStandardHelpOptions = true,
            description = "Put a box on a wall with a key and a seal in it.")
    static final class Install implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "BUILDING")
        private String building;

        @Parameters(index = "2", paramLabel = "OPENING")
        private String opening;

        @Parameters(index = "3", paramLabel = "SYMBOL")
        private String symbol;

        @Parameters(index = "4", paramLabel = "SEAL")
        private String seal;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> {
                service.install(number, building, opening, KeySymbol.parse(symbol), seal, Common.asDay(day.when));
                group.out().println(number + " sealed on " + opening + " with " + symbol);
            });
            return Main.OK;
        }
    }

    /** Read the tag. Says nothing much, or says the box has been opened. */
    @Command(
            name = "check",
            mixinStandardHelpOptions = true,
            description = "Read the tag. Says nothing much, or says the box has been opened.")
    static final class Check implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "SEAL")
        private String seal;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> {
                KeyBoxRow row = service.check(number, seal, Common.asDay(day.when));
                if (row.getState().equals(BoxState.SEALED.value())) {
                    group.out().println(number + " still sealed on " + seal);
                } else {
                    group.out().println(number + " has been opened, " + row.getNote());
                }
            });
            return Main.OK;
        }
    }

    /** Record that somebody opened a box and said so. */
    @Command(
            name = "opened",
            mixinStandardHelpOptions = true,
            description = "Record that somebody opened a box and said so.")
    static final class Opened implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(
                names = "--by",
                description = "who opened it: ${COMPLETION-CANDIDATES}",
                completionCandidates = Openers.class)
        private String by = OpenedBy.FIRE.value();

        @Option(names = "--note", defaultValue = "")
        private String note;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            OpenedBy opener = OpenedBy.find(by)
                    .orElseThrow(() -> new CommandLine.ParameterException(
                            spec.commandLine(),
                            "Invalid value for '--by': '" + by + "' is not one of " + Openers.listed() + "."));
            group.working(service -> {
                service.reportOpened(number, opener, Common.asDay(day.when), note);
                group.out().println(number + " open, " + by);
            });
            return Main.OK;
        }
    }

    /** Everybody who can open a box, as the option names them. */
    static final class Openers implements Iterable<String> {
        static String listed() {
            return Arrays.stream(OpenedBy.values())
                    .map(item -> "'" + item.value() + "'")
                    .collect(Collectors.joining(", "));
        }

        @Override
        public java.util.Iterator<String> iterator() {
            return Arrays.stream(OpenedBy.values()).map(OpenedBy::value).iterator();
        }
    }

    /** Record that the key is gone out of an open box. */
    @Command(
            name = "empty",
            mixinStandardHelpOptions = true,
            description = "Record that the key is gone out of an open box.")
    static final class Empty implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(names = "--note", defaultValue = "")
        private String note;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> {
                service.empty(number, Common.asDay(day.when), note);
                group.out().println(number + " empty");
            });
            return Main.OK;
        }
    }

    /** New key, new seal, and a new row on the history. */
    @Command(
            name = "reseal",
            mixinStandardHelpOptions = true,
            description = "New key, new seal, and a new row on the history.")
    static final class Reseal implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "SEAL")
        private String seal;

        @Parameters(index = "2", paramLabel = "SYMBOL")
        private String symbol;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> {
                service.reseal(number, seal, KeySymbol.parse(symbol), Common.asDay(day.when));
                group.out().println(number + " sealed on " + seal + " with " + symbol);
            });
            return Main.OK;
        }
    }

    /** Take a box off the wall for good. */
    @Command(name = "withdraw", mixinStandardHelpOptions = true, description = "Take a box off the wall for good.")
    static final class Withdraw implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "REASON")
        private String reason;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> {
                service.withdraw(number, Common.asDay(day.when), reason);
                group.out().println(number + " off the wall");
            });
            return Main.OK;
        }
    }

    /** Every seal a box has carried, oldest first. */
    @Command(
            name = "seals",
            mixinStandardHelpOptions = true,
            description = "Every seal a box has carried, oldest first.")
    static final class Seals implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), EscrowPages.sealSheet(service.history(number), Common.asDay(day.when))));
            return Main.OK;
        }
    }

    /** Which key was in a box on a day. */
    @Command(name = "key-on", mixinStandardHelpOptions = true, description = "Which key was in a box on a day.")
    static final class KeyOn implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "DAY", converter = Common.DayConverter.class)
        private LocalDateTime day;

        @Override
        public Integer call() {
            group.working(service -> group.out()
                    .println(service.keyOn(number, day.toLocalDate())
                            .map(KeySymbol::render)
                            .orElse("nothing was in it")));
            return Main.OK;
        }
    }

    /** What is not sealed, and which keys are loose because of it. */
    @Command(
            name = "open",
            mixinStandardHelpOptions = true,
            description = "What is not sealed, and which keys are loose because of it.")
    static final class Open implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), EscrowPages.exposureSheet(service.register(), Common.asDay(day.when))));
            return Main.OK;
        }
    }

    /** The list to carry round the campus. */
    @Command(name = "walk", mixinStandardHelpOptions = true, description = "The list to carry round the campus.")
    static final class Walk implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service ->
                    Common.echoLines(group.out(), EscrowPages.walkSheet(service.register(), Common.asDay(day.when))));
            return Main.OK;
        }
    }

    /** Where the boxes are. This is the sheet the fire service gets. */
    @Command(
            name = "coverage",
            mixinStandardHelpOptions = true,
            description = "Where the boxes are. This is the sheet the fire service gets.")
    static final class Coverage implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), EscrowPages.coverageSheet(service.register(), buildings(service.session()))));
            return Main.OK;
        }
    }

    /** Which boxes hold a symbol, for a key somebody handed in. */
    @Command(
            name = "found",
            mixinStandardHelpOptions = true,
            description = "Which boxes hold a symbol, for a key somebody handed in.")
    static final class Found implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @Parameters(index = "0", paramLabel = "SYMBOL")
        private String symbol;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), EscrowPages.keyFound(service.register(), symbol)));
            return Main.OK;
        }
    }

    /** The shop's half of the paperwork, which stays in the building. */
    @Command(
            name = "report",
            mixinStandardHelpOptions = true,
            description = "The shop's half of the paperwork, which stays in the building.")
    static final class Report implements Callable<Integer> {
        @ParentCommand
        private BoxCommands group;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(),
                    EscrowPages.shopReport(service.register(), buildings(service.session()), Common.asDay(day.when))));
            return Main.OK;
        }
    }
}
