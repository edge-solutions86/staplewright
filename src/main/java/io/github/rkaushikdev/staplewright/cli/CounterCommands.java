package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.control.NoticeLog;
import io.github.rkaushikdev.staplewright.control.Notices;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.models.IssuedKeyRow;
import io.github.rkaushikdev.staplewright.persistence.models.KeyRequestRow;
import io.github.rkaushikdev.staplewright.reports.ControlPages;
import io.github.rkaushikdev.staplewright.services.CounterService;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Commands for the counter: request, approve, issue, take back, chase.
 *
 * <p>Dates default to today because that is what they are ninety times out of a hundred, and every
 * one of them can be given explicitly for the afternoon somebody is working through a fortnight of
 * paper.
 */
@Command(
        name = "counter",
        mixinStandardHelpOptions = true,
        description = "Requests, keys and deposits.",
        subcommands = {
            CounterCommands.Series.class,
            CounterCommands.Request.class,
            CounterCommands.Approve.class,
            CounterCommands.Deny.class,
            CounterCommands.Issue.class,
            CounterCommands.TakeBack.class,
            CounterCommands.Lost.class,
            CounterCommands.Statement.class,
            CounterCommands.Deposits.class,
            CounterCommands.Department.class,
            CounterCommands.Overdue.class,
            CounterCommands.Letters.class
        })
final class CounterCommands {

    @ParentCommand
    Main main;

    /** A counter service on a session of its own, at the shop's deposit rates. */
    void counter(Consumer<CounterService> work) {
        Common.working(
                main, session -> new CounterService(session, main.settings().deposits()), work);
    }

    /** Everything on file, read through one session, for the reports. */
    void reading(Consumer<Workspace> work) {
        Common.working(main, Workspace::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Open the tag and request counters. Once, when the shop is set up. */
    @Command(
            name = "series",
            mixinStandardHelpOptions = true,
            description = "Open the tag and request counters. Once, when the shop is set up.")
    static final class Series implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Override
        public Integer call() {
            group.counter(service -> {
                service.openSeries();
                group.out().println("tag and request series opened");
            });
            return Main.OK;
        }
    }

    /** Raise a request for one key. */
    @Command(name = "request", mixinStandardHelpOptions = true, description = "Raise a request for one key.")
    static final class Request implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "KEYHOLDER")
        private String keyholder;

        @Parameters(index = "1", paramLabel = "SYMBOL")
        private String symbol;

        @Parameters(index = "2", paramLabel = "BUILDING")
        private String building;

        @Parameters(index = "3", paramLabel = "REASON")
        private String reason;

        @Option(names = "--master", description = "this symbol masters something")
        private boolean master;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                KeyRequestRow row =
                        service.raiseRequest(keyholder, symbol, building, reason, Common.asDay(day), master);
                group.out().println(row.getNumber() + " raised for " + keyholder + ", " + symbol);
            });
            return Main.OK;
        }
    }

    /** Sign a request off. */
    @Command(name = "approve", mixinStandardHelpOptions = true, description = "Sign a request off.")
    static final class Approve implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "SIGNER")
        private String signer;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                service.approve(number, signer, Common.asDay(day));
                group.out().println(number + " approved by " + signer);
            });
            return Main.OK;
        }
    }

    /** Turn a request down, with a reason. */
    @Command(name = "deny", mixinStandardHelpOptions = true, description = "Turn a request down, with a reason.")
    static final class Deny implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "SIGNER")
        private String signer;

        @Parameters(index = "2", paramLabel = "REASON")
        private String reason;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                service.deny(number, signer, Common.asDay(day), reason);
                group.out().println(number + " denied");
            });
            return Main.OK;
        }
    }

    /** Hand the key over and take the deposit. */
    @Command(name = "issue", mixinStandardHelpOptions = true, description = "Hand the key over and take the deposit.")
    static final class Issue implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(names = "--due", converter = Common.DayConverter.class, description = "due back")
        private Optional<LocalDateTime> due = Optional.empty();

        @Option(names = "--deposit", description = "cents, or the standing rate")
        private Optional<Integer> deposit = Optional.empty();

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                IssuedKeyRow key =
                        service.issue(number, Common.asDay(day), due.map(LocalDateTime::toLocalDate), deposit);
                group.out()
                        .println("tag " + key.getTag() + " issued to " + key.getKeyholder() + ", deposit "
                                + key.getDepositCents());
            });
            return Main.OK;
        }
    }

    /** A key comes back and the deposit goes with it. */
    @Command(
            name = "return",
            mixinStandardHelpOptions = true,
            description = "A key comes back and the deposit goes with it.")
    static final class TakeBack implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "TAG")
        private String tag;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                service.takeBack(tag, Common.asDay(day));
                group.out().println("tag " + tag + " back");
            });
            return Main.OK;
        }
    }

    /** A key is gone and the deposit stays here. */
    @Command(name = "lost", mixinStandardHelpOptions = true, description = "A key is gone and the deposit stays here.")
    static final class Lost implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "TAG")
        private String tag;

        @Parameters(index = "1", paramLabel = "NOTE")
        private String note;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.counter(service -> {
                service.reportLost(tag, Common.asDay(day), note);
                group.out().println("tag " + tag + " written off");
            });
            return Main.OK;
        }
    }

    /** The sheet a keyholder signs. */
    @Command(name = "statement", mixinStandardHelpOptions = true, description = "The sheet a keyholder signs.")
    static final class Statement implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "KEYHOLDER")
        private String keyholder;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.reading(space -> Common.echoLines(
                    group.out(),
                    ControlPages.keyholderStatement(
                            space.directory().person(keyholder),
                            space.ledger(),
                            Optional.of(space.deposits()),
                            Optional.of(Common.asDay(day)))));
            return Main.OK;
        }
    }

    /** Every deposit line against one person. */
    @Command(name = "deposits", mixinStandardHelpOptions = true, description = "Every deposit line against one person.")
    static final class Deposits implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "KEYHOLDER")
        private String keyholder;

        @Override
        public Integer call() {
            group.reading(
                    space -> Common.echoLines(group.out(), ControlPages.depositStatement(space.deposits(), keyholder)));
            return Main.OK;
        }
    }

    /** Who in a department holds what. */
    @Command(name = "department", mixinStandardHelpOptions = true, description = "Who in a department holds what.")
    static final class Department implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Parameters(index = "0", paramLabel = "DEPARTMENT")
        private String department;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.reading(space -> Common.echoLines(
                    group.out(),
                    ControlPages.departmentSummary(space.directory(), space.ledger(), department, Common.asDay(day))));
            return Main.OK;
        }
    }

    /** The chase list. */
    @Command(name = "overdue", mixinStandardHelpOptions = true, description = "The chase list.")
    static final class Overdue implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.reading(space ->
                    Common.echoLines(group.out(), ControlPages.overdueReport(space.ledger(), Common.asDay(day))));
            return Main.OK;
        }
    }

    /** What the notices run would send today. */
    @Command(name = "letters", mixinStandardHelpOptions = true, description = "What the notices run would send today.")
    static final class Letters implements Callable<Integer> {
        @ParentCommand
        private CounterCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> day = Optional.empty();

        @Override
        public Integer call() {
            group.reading(space -> Common.echoLines(
                    group.out(),
                    ControlPages.noticeRun(Notices.noticesDue(
                            space.ledger(),
                            Common.asDay(day),
                            new NoticeLog(),
                            group.main.settings().notices()))));
            return Main.OK;
        }
    }
}
