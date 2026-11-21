package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.KeyingSchedule;
import io.github.rkaushikdev.staplewright.persistence.models.ScheduleEntryRow;
import io.github.rkaushikdev.staplewright.persistence.models.SystemRow;
import io.github.rkaushikdev.staplewright.progression.Finding;
import io.github.rkaushikdev.staplewright.reports.SchedulePages;
import io.github.rkaushikdev.staplewright.services.SystemService;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Commands for keying systems: open, draw, cut, print, review.
 *
 * <p>The cut command is the one that gets used forty times an afternoon while a system is being laid
 * out, so it takes the system, the symbol and the bitting as three bare arguments in that order and
 * prints one line back.
 */
@Command(
        name = "system",
        mixinStandardHelpOptions = true,
        description = "Keying systems.",
        subcommands = {
            SystemCommands.Open.class,
            SystemCommands.Draw.class,
            SystemCommands.Cut.class,
            SystemCommands.Uncut.class,
            SystemCommands.Schedule.class,
            SystemCommands.OpenSymbols.class,
            SystemCommands.Review.class,
            SystemCommands.Archive.class
        })
final class SystemCommands {

    @ParentCommand
    Main main;

    /** A system service on a session of its own, under the shop's cutting rules. */
    void working(Consumer<SystemService> work) {
        Common.working(
                main, session -> new SystemService(session, main.settings().cutting()), work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Open a keying system on a chart and a keyway family. */
    @Command(
            name = "open",
            mixinStandardHelpOptions = true,
            description = "Open a keying system on a chart and a keyway family.")
    static final class Open implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Parameters(index = "1", paramLabel = "CHART_ID")
        private int chartId;

        @Parameters(index = "2", paramLabel = "FAMILY")
        private String family;

        @Parameters(index = "3", paramLabel = "SECTION")
        private String section;

        @Option(
                names = "--step",
                defaultValue = "2",
                showDefaultValue = picocli.CommandLine.Help.Visibility.ALWAYS,
                description = "progression step")
        private int step;

        @Override
        public Integer call() {
            group.working(service -> {
                SystemRow row = service.openSystem(name, chartId, family, section, step);
                group.out()
                        .println(row.getName() + " opened on chart " + row.getChartId() + ", step "
                                + row.getProgressionStep());
            });
            return Main.OK;
        }
    }

    /** Put a symbol on the schedule without cutting it. */
    @Command(
            name = "draw",
            mixinStandardHelpOptions = true,
            description = "Put a symbol on the schedule without cutting it.")
    static final class Draw implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Parameters(index = "1", paramLabel = "SYMBOL")
        private String symbol;

        @Option(names = "--section", description = "keyway section, or the one above")
        private Optional<String> section = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> {
                ScheduleEntryRow row = service.draw(name, KeySymbol.parse(symbol), section);
                group.out().println(row.getSymbol().render() + " drawn on " + row.getSection());
            });
            return Main.OK;
        }
    }

    /** Cut a symbol to a bitting. */
    @Command(name = "cut", mixinStandardHelpOptions = true, description = "Cut a symbol to a bitting.")
    static final class Cut implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Parameters(index = "1", paramLabel = "SYMBOL")
        private String symbol;

        @Parameters(index = "2", paramLabel = "BITTING")
        private String bitting;

        @Override
        public Integer call() {
            group.working(service -> {
                service.cut(name, KeySymbol.parse(symbol), Bitting.parse(bitting));
                group.out().println(symbol + " cut " + bitting);
            });
            return Main.OK;
        }
    }

    /** Take the cuts off a symbol and leave it on the schedule. */
    @Command(
            name = "uncut",
            mixinStandardHelpOptions = true,
            description = "Take the cuts off a symbol and leave it on the schedule.")
    static final class Uncut implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Parameters(index = "1", paramLabel = "SYMBOL")
        private String symbol;

        @Override
        public Integer call() {
            group.working(service -> {
                service.uncut(name, KeySymbol.parse(symbol));
                group.out().println(symbol + " back on the open list");
            });
            return Main.OK;
        }
    }

    /** Print the keying schedule. */
    @Command(name = "schedule", mixinStandardHelpOptions = true, description = "Print the keying schedule.")
    static final class Schedule implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Option(names = "--bittings", description = "sort by cut instead of by symbol")
        private boolean bittings;

        @Option(names = "--tree", description = "the hierarchy alone")
        private boolean tree;

        @Override
        public Integer call() {
            group.working(service -> {
                KeyingSchedule drawn = service.schedule(name);
                List<String> lines;
                if (bittings) {
                    lines = SchedulePages.bittingList(drawn);
                } else if (tree) {
                    lines = SchedulePages.treeReport(drawn);
                } else {
                    lines = SchedulePages.scheduleReport(drawn, name);
                }
                Common.echoLines(group.out(), lines);
            });
            return Main.OK;
        }
    }

    /** What is drawn and not cut. */
    @Command(name = "open-symbols", mixinStandardHelpOptions = true, description = "What is drawn and not cut.")
    static final class OpenSymbols implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            group.working(
                    service -> Common.echoLines(group.out(), SchedulePages.openSymbolsReport(service.schedule(name))));
            return Main.OK;
        }
    }

    /** Everything wrong with a system, worst first. */
    @Command(
            name = "review",
            mixinStandardHelpOptions = true,
            description = "Everything wrong with a system, worst first.")
    static final class Review implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            group.working(service -> {
                List<Finding> findings = service.review(name);
                if (findings.isEmpty()) {
                    group.out().println("nothing to report");
                    return;
                }
                for (Finding finding : findings) {
                    group.out()
                            .println(Text.left(finding.severity().value(), 9)
                                    + Text.left(finding.code(), 16)
                                    + finding.message());
                }
            });
            return Main.OK;
        }
    }

    /** Close a system so nothing else gets drawn on it. */
    @Command(
            name = "archive",
            mixinStandardHelpOptions = true,
            description = "Close a system so nothing else gets drawn on it.")
    static final class Archive implements Callable<Integer> {
        @ParentCommand
        private SystemCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            group.working(service -> {
                service.archive(name);
                group.out().println(name + " archived");
            });
            return Main.OK;
        }
    }
}
