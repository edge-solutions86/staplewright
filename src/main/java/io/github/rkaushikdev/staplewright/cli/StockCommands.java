package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.models.BlankRow;
import io.github.rkaushikdev.staplewright.reports.Reconciliation;
import io.github.rkaushikdev.staplewright.reports.StockPages;
import io.github.rkaushikdev.staplewright.services.CountResult;
import io.github.rkaushikdev.staplewright.services.StockService;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
 * Stock commands, typed at the drawer with a blank already in hand.
 *
 * <p>Everything here takes the part number first because that is what the person is holding and
 * reading off. The quantity defaults to one, because the common case at the drawer is one blank at a
 * time, and making somebody type a one forty times a day is how a shop stops using the ledger.
 *
 * <p>The reconcile command is the only one that takes a window, and it defaults to the year so far,
 * which is the window the annual return wants.
 */
@Command(
        name = "stock",
        mixinStandardHelpOptions = true,
        description = "Blank stock: what is in the drawer and where it went.",
        subcommands = {
            StockCommands.Show.class,
            StockCommands.Receive.class,
            StockCommands.Cut.class,
            StockCommands.Scrap.class,
            StockCommands.Count.class,
            StockCommands.Log.class,
            StockCommands.Reconcile.class,
            StockCommands.Reorder.class,
            StockCommands.Restricted.class,
            StockCommands.Orders.class,
            StockCommands.Shrinkage.class,
            StockCommands.Report.class
        })
final class StockCommands {

    private static final String WHEN_HELP = "the day it moved";

    @ParentCommand
    Main main;

    /** A stock service on a session of its own. */
    void working(Consumer<StockService> work) {
        Common.working(main, StockService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** The first of January of this year, where a window starts when nobody says. */
    static LocalDate startOfYear(LocalDate today) {
        return LocalDate.of(today.getYear(), 1, 1);
    }

    /** Refuse a number under {@code least} the way any other unreadable argument is refused. */
    static int atLeast(CommandSpec spec, String name, int value, int least) {
        if (value < least) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid value for '" + name + "': " + value + " is not in the range x>=" + least + ".");
        }
        return value;
    }

    /** Every part's reconciliation between two days, in the order the parts are given. */
    static List<Reconciliation> reconciled(
            StockService service, List<BlankRow> blanks, LocalDate since, LocalDate until) {
        return blanks.stream()
                .map(row -> StockPages.reconcile(row, service.movements(row.getPartNumber()), since, until))
                .toList();
    }

    /** What one part number stands at, both ways of counting it. */
    @Command(
            name = "show",
            mixinStandardHelpOptions = true,
            description = "What one part number stands at, both ways of counting it.")
    static final class Show implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Override
        public Integer call() {
            group.working(service -> {
                BlankRow row = service.blank(part);
                group.out()
                        .println(row.getPartNumber() + " " + row.getManufacturer() + " " + row.getChambers()
                                + " chamber");
                group.out().println("drawer " + row.getStock() + ", ledger " + service.ledgerTotal(part));
                if (row.isRestricted()) {
                    group.out().println("restricted, every movement needs a reference");
                }
            });
            return Main.OK;
        }
    }

    /** Book brass into the drawer. */
    @Command(name = "receive", mixinStandardHelpOptions = true, description = "Book brass into the drawer.")
    static final class Receive implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Option(names = "--count", defaultValue = "1", description = "how many")
        private int quantity;

        @Option(names = "--against", defaultValue = "", description = "a tag, order or job")
        private String reference;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = WHEN_HELP)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            atLeast(spec, "--count", quantity, 1);
            group.working(service -> {
                service.receive(part, quantity, Common.asDay(when), reference);
                group.out().println(part + " now " + service.onHand(part));
            });
            return Main.OK;
        }
    }

    /** Take brass out to cut it. */
    @Command(name = "cut", mixinStandardHelpOptions = true, description = "Take brass out to cut it.")
    static final class Cut implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Option(names = "--count", defaultValue = "1", description = "how many")
        private int quantity;

        @Option(names = "--against", defaultValue = "", description = "a tag, order or job")
        private String reference;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = WHEN_HELP)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            atLeast(spec, "--count", quantity, 1);
            group.working(service -> {
                service.cut(part, quantity, Common.asDay(when), reference);
                group.out().println(part + " now " + service.onHand(part));
            });
            return Main.OK;
        }
    }

    /** Write brass off, with the reason on the line. */
    @Command(
            name = "scrap",
            mixinStandardHelpOptions = true,
            description = "Write brass off, with the reason on the line.")
    static final class Scrap implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Parameters(index = "1", paramLabel = "REASON")
        private String reason;

        @Option(names = "--count", defaultValue = "1", description = "how many")
        private int quantity;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = WHEN_HELP)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            atLeast(spec, "--count", quantity, 1);
            group.working(service -> {
                service.scrap(part, quantity, Common.asDay(when), reason);
                group.out().println(part + " now " + service.onHand(part));
            });
            return Main.OK;
        }
    }

    /** Write down what is actually in the drawer. */
    @Command(
            name = "count",
            mixinStandardHelpOptions = true,
            description = "Write down what is actually in the drawer.")
    static final class Count implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Parameters(index = "1", paramLabel = "COUNTED")
        private int counted;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = WHEN_HELP)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            atLeast(spec, "COUNTED", counted, 0);
            group.working(service -> {
                CountResult found = service.count(part, counted, Common.asDay(when));
                group.out().println(found.describe());
            });
            return Main.OK;
        }
    }

    /** Every movement of one part, oldest first. */
    @Command(name = "log", mixinStandardHelpOptions = true, description = "Every movement of one part, oldest first.")
    static final class Log implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), StockPages.movementLog(service.movements(part))));
            return Main.OK;
        }
    }

    /** Account for one part between two days, opening to closing. */
    @Command(
            name = "reconcile",
            mixinStandardHelpOptions = true,
            description = "Account for one part between two days, opening to closing.")
    static final class Reconcile implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Parameters(index = "0", paramLabel = "PART")
        private String part;

        @Option(
                names = "--from",
                converter = Common.DayConverter.class,
                description = "the first day, the first of January by default")
        private Optional<LocalDateTime> since = Optional.empty();

        @Option(names = "--to", converter = Common.DayConverter.class, description = "the last day, today by default")
        private Optional<LocalDateTime> until = Optional.empty();

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();
            LocalDate opened = Common.asGivenDay(since, startOfYear(today));
            LocalDate closed = Common.asGivenDay(until, today);
            group.working(service -> {
                Reconciliation found =
                        StockPages.reconcile(service.blank(part), service.movements(part), opened, closed);
                Common.echoLines(group.out(), StockPages.reconciliationSheet(found));
            });
            return Main.OK;
        }
    }

    /** What to put on the next order. */
    @Command(name = "reorder", mixinStandardHelpOptions = true, description = "What to put on the next order.")
    static final class Reorder implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), StockPages.reorderSheet(service.blanks().all())));
            return Main.OK;
        }
    }

    /** How much restricted brass is in the building. */
    @Command(
            name = "restricted",
            mixinStandardHelpOptions = true,
            description = "How much restricted brass is in the building.")
    static final class Restricted implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), StockPages.restrictedPosition(service.blanks().all())));
            return Main.OK;
        }
    }

    /** What is still on order and what is left on it. */
    @Command(
            name = "orders",
            mixinStandardHelpOptions = true,
            description = "What is still on order and what is left on it.")
    static final class Orders implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(), StockPages.outstandingOrders(service.orders().all())));
            return Main.OK;
        }
    }

    /** Which parts do not account for themselves this year. */
    @Command(
            name = "shrinkage",
            mixinStandardHelpOptions = true,
            description = "Which parts do not account for themselves this year.")
    static final class Shrinkage implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Option(
                names = "--from",
                converter = Common.DayConverter.class,
                description = "the first day, the first of January by default")
        private Optional<LocalDateTime> since = Optional.empty();

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();
            LocalDate opened = Common.asGivenDay(since, startOfYear(today));
            group.working(service -> Common.echoLines(
                    group.out(),
                    StockPages.shrinkage(reconciled(service, service.blanks().all(), opened, today))));
            return Main.OK;
        }
    }

    /** The whole pack, in the order somebody signs it. */
    @Command(
            name = "report",
            mixinStandardHelpOptions = true,
            description = "The whole pack, in the order somebody signs it.")
    static final class Report implements Callable<Integer> {
        @ParentCommand
        private StockCommands group;

        @Option(
                names = "--from",
                converter = Common.DayConverter.class,
                description = "the first day, the first of January by default")
        private Optional<LocalDateTime> since = Optional.empty();

        @Override
        public Integer call() {
            LocalDate today = LocalDate.now();
            LocalDate opened = Common.asGivenDay(since, startOfYear(today));
            group.working(service -> {
                List<BlankRow> blanks = service.blanks().all();
                List<Reconciliation> found = reconciled(service, blanks, opened, today);
                Common.echoLines(
                        group.out(),
                        StockPages.stockReport(blanks, found, service.orders().all(), opened, today));
            });
            return Main.OK;
        }
    }
}
