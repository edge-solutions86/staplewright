package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.Loaders;
import io.github.rkaushikdev.staplewright.persistence.models.IncidentRow;
import io.github.rkaushikdev.staplewright.persistence.models.WorkOrderRow;
import io.github.rkaushikdev.staplewright.rekey.Assessment;
import io.github.rkaushikdev.staplewright.rekey.Cause;
import io.github.rkaushikdev.staplewright.rekey.Estimate;
import io.github.rkaushikdev.staplewright.rekey.LineState;
import io.github.rkaushikdev.staplewright.rekey.OrderLine;
import io.github.rkaushikdev.staplewright.rekey.OrderState;
import io.github.rkaushikdev.staplewright.rekey.ScopeDecision;
import io.github.rkaushikdev.staplewright.rekey.WorkOrder;
import io.github.rkaushikdev.staplewright.reports.RekeyPages;
import io.github.rkaushikdev.staplewright.services.IncidentService;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Commands for the rekey side: report, assess, plan, book, print the packet.
 *
 * <p>The order these appear in is the order they get used on the morning of an incident, and the
 * plan command is the one that gets run three times while somebody argues about the scope. It
 * writes nothing, so running it three times costs nothing but the reading.
 */
@Command(
        name = "rekey",
        mixinStandardHelpOptions = true,
        description = "Missing keys and the work they cause.",
        subcommands = {
            RekeyCommands.Series.class,
            RekeyCommands.Report.class,
            RekeyCommands.Assess.class,
            RekeyCommands.Plan.class,
            RekeyCommands.Book.class,
            RekeyCommands.Packet.class,
            RekeyCommands.Orders.class
        })
final class RekeyCommands {

    @ParentCommand
    Main main;

    void working(Consumer<IncidentService> work) {
        Common.working(
                main, session -> new IncidentService(session, main.settings().scope()), work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Open the incident and work order counters. */
    @Command(
            name = "series",
            mixinStandardHelpOptions = true,
            description = "Open the incident and work order counters.")
    static final class Series implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                service.openSeries();
                group.out().println("incident and work order series opened");
            });
            return Main.OK;
        }
    }

    /** Every cause a key can go missing by, as the option names them. */
    static final class Causes implements Iterable<String> {
        static String listed() {
            return Arrays.stream(Cause.values())
                    .map(item -> "'" + item.value() + "'")
                    .collect(Collectors.joining(", "));
        }

        @Override
        public Iterator<String> iterator() {
            return Arrays.stream(Cause.values()).map(Cause::value).iterator();
        }
    }

    /** Report a key missing and retire its cut. */
    @Command(name = "report", mixinStandardHelpOptions = true, description = "Report a key missing and retire its cut.")
    static final class Report implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "TAG")
        private String tag;

        @Option(
                names = "--cause",
                description = "how it went: ${COMPLETION-CANDIDATES}",
                completionCandidates = Causes.class)
        private String cause = Cause.LOST.value();

        @Option(names = "--note", defaultValue = "", description = "required for a theft")
        private String note;

        @Option(names = "--keep-cut", description = "do not retire the bitting")
        private boolean keepCut;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            Cause how = Cause.find(cause)
                    .orElseThrow(() -> new CommandLine.ParameterException(
                            spec.commandLine(),
                            "Invalid value for '--cause': '" + cause + "' is not one of " + Causes.listed() + "."));
            group.working(service -> {
                IncidentRow row = service.report(tag, how, Common.asDay(when), note, !keepCut);
                group.out().println(row.getNumber() + " logged, graded " + row.getSeverity());
            });
            return Main.OK;
        }
    }

    /** What one incident exposes, on one page. */
    @Command(name = "assess", mixinStandardHelpOptions = true, description = "What one incident exposes, on one page.")
    static final class Assess implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            group.working(
                    service -> Common.echoLines(group.out(), RekeyPages.incidentSheet(service.assessment(number))));
            return Main.OK;
        }
    }

    /** What the shop would do about it, and what that costs. */
    @Command(
            name = "plan",
            mixinStandardHelpOptions = true,
            description = "What the shop would do about it, and what that costs.")
    static final class Plan implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(names = "--cost", description = "price it as well")
        private boolean cost;

        @Override
        public Integer call() {
            group.working(service -> {
                ScopeDecision decision = service.plan(number);
                Common.echoLines(group.out(), RekeyPages.scopeSheet(decision));
                if (cost) {
                    Assessment found = service.assessment(number);
                    Estimate priced = Estimate.of(
                            decision,
                            Loaders.loadCoverage(service.session()),
                            found.keysToRecut(),
                            group.main.settings().rates());
                    Common.echoLines(group.out(), RekeyPages.costSheet(priced));
                }
            });
            return Main.OK;
        }
    }

    /** Raise the work order off an incident. */
    @Command(name = "book", mixinStandardHelpOptions = true, description = "Raise the work order off an incident.")
    static final class Book implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(names = "--for", converter = Common.DayConverter.class, description = "the day it is booked for")
        private Optional<LocalDateTime> wanted = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> {
                WorkOrderRow order = wanted.isPresent()
                        ? service.raiseOrder(number, wanted.get().toLocalDate())
                        : service.raiseOrder(number);
                group.out()
                        .println(order.getNumber() + " raised with "
                                + order.getLines().size() + " cylinders");
            });
            return Main.OK;
        }
    }

    /** Print the sheets for one work order. */
    @Command(name = "packet", mixinStandardHelpOptions = true, description = "Print the sheets for one work order.")
    static final class Packet implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Override
        public Integer call() {
            group.working(service -> {
                WorkOrderRow row = service.orders().get(number);
                List<OrderLine> lines = row.getLines().stream()
                        .map(line -> new OrderLine(
                                line.getSerial(), line.getOpeningNumber(), LineState.PENDING, line.getNote()))
                        .toList();
                WorkOrder order = new WorkOrder(
                        row.getNumber(),
                        row.getIncidentNumber().orElse(""),
                        lines,
                        OrderState.DRAFTED,
                        row.getTechnician(),
                        Optional.empty(),
                        Optional.empty(),
                        "");
                Common.echoLines(group.out(), RekeyPages.orderPacket(order));
            });
            return Main.OK;
        }
    }

    /** The open work orders. */
    @Command(name = "orders", mixinStandardHelpOptions = true, description = "The open work orders.")
    static final class Orders implements Callable<Integer> {
        @ParentCommand
        private RekeyCommands group;

        @Option(names = "--technician", description = "only this person's work")
        private String technician;

        @Override
        public Integer call() {
            group.working(service -> {
                List<WorkOrderRow> rows = technician != null && !technician.isEmpty()
                        ? service.orders().forTechnician(technician)
                        : service.orders().openOrders();
                if (rows.isEmpty()) {
                    group.out().println("nothing open");
                    return;
                }
                for (WorkOrderRow row : rows) {
                    String who = row.getTechnician().isEmpty() ? "unassigned" : row.getTechnician();
                    group.out()
                            .println(Text.left(row.getNumber(), 10) + Text.left(row.getState(), 12) + Text.left(who, 14)
                                    + row.getLines().size() + " cylinders");
                }
            });
            return Main.OK;
        }
    }
}
