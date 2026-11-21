package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.rekey.Door;
import io.github.rkaushikdev.staplewright.rekey.Window;
import io.github.rkaushikdev.staplewright.rekey.WorkCalendar;
import io.github.rkaushikdev.staplewright.services.PhasingService;
import io.github.rkaushikdev.staplewright.util.Text;
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
 * Phasing commands, which get typed once a year and argued about for months.
 *
 * <p>Every command here builds the standard academic year rather than taking a calendar as an
 * argument. A shop that wants different windows edits the year in code, because a calendar typed at
 * a prompt is a calendar nobody else can reproduce, and the whole value of this plan is that two
 * people get the same answer out of it.
 */
@Command(
        name = "phasing",
        mixinStandardHelpOptions = true,
        description = "Spreading a rekey across the windows a campus actually has.",
        subcommands = {
            PhasingCommands.Windows.class,
            PhasingCommands.Buildings.class,
            PhasingCommands.Orders.class,
            PhasingCommands.OneBuilding.class,
            PhasingCommands.OneOrder.class,
            PhasingCommands.Campus.class,
            PhasingCommands.Day.class,
            PhasingCommands.CarryOver.class
        })
final class PhasingCommands {

    static final int EARLIEST_YEAR = 2000;
    static final int LATEST_YEAR = 2100;

    @ParentCommand
    Main main;

    void working(Consumer<PhasingService> work) {
        Common.working(main, PhasingService::new, work);
    }

    void planning(WorkCalendar calendar, Consumer<PhasingService> work) {
        Common.working(main, session -> new PhasingService(session, calendar), work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** The year and the rate every plan is made at: the options the planning commands share. */
    static final class Year {
        @Option(names = "--year", description = "the year the summer window falls in")
        Optional<Integer> year = Optional.empty();

        @Option(
                names = "--rate",
                defaultValue = "" + PhasingService.DEFAULT_RATE,
                showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
                description = "doors a crew gets through in a day")
        int rate;

        /**
         * The year to plan against, defaulting to the one we are in.
         *
         * @throws CommandLine.ParameterException for a year outside the century or a rate under one
         */
        WorkCalendar calendar(CommandSpec spec) {
            if (year.isPresent() && (year.get() < EARLIEST_YEAR || year.get() > LATEST_YEAR)) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(),
                        "Invalid value for '--year': " + year.get() + " is not in the range " + EARLIEST_YEAR + "<=x<="
                                + LATEST_YEAR + ".");
            }
            if (rate < 1) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for '--rate': " + rate + " is not in the range x>=1.");
            }
            return PhasingService.academicYear(
                    year.orElseGet(() -> LocalDate.now().getYear()), rate);
        }
    }

    /** The windows in a year, and how many doors fit in each. */
    @Command(
            name = "windows",
            mixinStandardHelpOptions = true,
            description = "The windows in a year, and how many doors fit in each.")
    static final class Windows implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            WorkCalendar calendar = year.calendar(spec);
            Common.echoLines(
                    group.out(),
                    calendar.inOrder().stream().map(Window::describe).toList());
            group.out().println(calendar.totalCapacity() + " doors in the year");
            return Main.OK;
        }
    }

    /** Which buildings have keyed doors to plan. */
    @Command(
            name = "buildings",
            mixinStandardHelpOptions = true,
            description = "Which buildings have keyed doors to plan.")
    static final class Buildings implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> found = PhasingService.buildingsWithWork(service.session());
                if (found.isEmpty()) {
                    group.out().println("no keyed doors anywhere");
                    return;
                }
                Common.echoLines(group.out(), found);
            });
            return Main.OK;
        }
    }

    /** Work orders that still need a slot. */
    @Command(name = "orders", mixinStandardHelpOptions = true, description = "Work orders that still need a slot.")
    static final class Orders implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> found = PhasingService.ordersToPlan(service.session());
                if (found.isEmpty()) {
                    group.out().println("nothing open to plan");
                    return;
                }
                Common.echoLines(group.out(), found);
            });
            return Main.OK;
        }
    }

    /** The year plan for one building. */
    @Command(name = "building", mixinStandardHelpOptions = true, description = "The year plan for one building.")
    static final class OneBuilding implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            group.planning(
                    year.calendar(spec),
                    service -> Common.echoLines(group.out(), service.sheet(service.doorsIn(code))));
            return Main.OK;
        }
    }

    /** The year plan for the doors on one work order. */
    @Command(
            name = "order",
            mixinStandardHelpOptions = true,
            description = "The year plan for the doors on one work order.")
    static final class OneOrder implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            group.planning(
                    year.calendar(spec),
                    service -> Common.echoLines(group.out(), service.sheet(service.doorsOn(number))));
            return Main.OK;
        }
    }

    /** The year plan for every keyed door there is. */
    @Command(
            name = "campus",
            mixinStandardHelpOptions = true,
            description = "The year plan for every keyed door there is.")
    static final class Campus implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            group.planning(year.calendar(spec), service -> {
                List<Door> doors = service.everyKeyedDoor();
                PhasingService.Demand demand = service.capacityAgainst(doors);
                group.out().println(demand.wanted() + " doors against " + demand.room() + " the year can take");
                Common.echoLines(group.out(), service.sheet(doors));
            });
            return Main.OK;
        }
    }

    /** What a crew is doing on one day. */
    @Command(name = "day", mixinStandardHelpOptions = true, description = "What a crew is doing on one day.")
    static final class Day implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "DAY", converter = Common.DayConverter.class)
        private LocalDateTime day;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            group.planning(
                    year.calendar(spec),
                    service -> Common.echoLines(
                            group.out(), service.daySheet(service.everyKeyedDoor(), day.toLocalDate())));
            return Main.OK;
        }
    }

    /** The doors that do not fit this year, for next year. */
    @Command(
            name = "carry-over",
            mixinStandardHelpOptions = true,
            description = "The doors that do not fit this year, for next year.")
    static final class CarryOver implements Callable<Integer> {
        @ParentCommand
        private PhasingCommands group;

        @Spec
        private CommandSpec spec;

        @CommandLine.Mixin
        private Year year;

        @Override
        public Integer call() {
            group.planning(year.calendar(spec), service -> {
                List<Door> found = service.nextYear(service.everyKeyedDoor());
                if (found.isEmpty()) {
                    group.out().println("nothing carries over");
                    return;
                }
                Common.echoLines(
                        group.out(),
                        found.stream()
                                .map(door -> Text.left(door.number(), 12)
                                        + door.restriction().value())
                                .toList());
            });
            return Main.OK;
        }
    }
}
