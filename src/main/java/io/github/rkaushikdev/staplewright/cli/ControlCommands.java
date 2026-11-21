package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.keys.Bitting;
import io.github.rkaushikdev.staplewright.persistence.models.CoreRow;
import io.github.rkaushikdev.staplewright.services.InterchangeService;
import io.github.rkaushikdev.staplewright.spec.ControlKey;
import io.github.rkaushikdev.staplewright.spec.ControlRegister;
import io.github.rkaushikdev.staplewright.spec.CoreFormat;
import io.github.rkaushikdev.staplewright.spec.HousingKind;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * Control key commands, and the one report nobody has ever run.
 *
 * <p>{@code crossings} is that report. It takes about a second and tells a shop whether a core
 * somebody pulls in one building can be put back in another building on a different control key. On
 * a campus that has bought locks from two suppliers the answer is always yes, and the fix is a
 * project.
 */
@Command(
        name = "control",
        mixinStandardHelpOptions = true,
        description = "Control keys and interchangeable cores.",
        subcommands = {
            ControlCommands.Cut.class,
            ControlCommands.HandTo.class,
            ControlCommands.Withdraw.class,
            ControlCommands.RegisterCore.class,
            ControlCommands.Install.class,
            ControlCommands.Pull.class,
            ControlCommands.Keys.class,
            ControlCommands.Holders.class,
            ControlCommands.Cores.class,
            ControlCommands.Crossings.class
        })
final class ControlCommands {

    @ParentCommand
    Main main;

    void working(Consumer<InterchangeService> work) {
        Common.working(main, InterchangeService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** The choice of {@code kind} a person typed by its name in small letters, or a usage error. */
    static <E extends Enum<E>> E choose(
            CommandLine.Model.CommandSpec spec, String option, Class<E> kind, String typed) {
        for (E choice : kind.getEnumConstants()) {
            if (Text.lower(choice.name()).equals(typed)) {
                return choice;
            }
        }
        String allowed = Arrays.stream(kind.getEnumConstants())
                .map(choice -> "'" + Text.lower(choice.name()) + "'")
                .collect(Collectors.joining(", "));
        throw new CommandLine.ParameterException(
                spec.commandLine(),
                "Invalid value for '" + option + "': '" + typed + "' is not one of " + allowed + ".");
    }

    /** Cut a control key onto a section. */
    @Command(name = "cut", mixinStandardHelpOptions = true, description = "Cut a control key onto a section.")
    static final class Cut implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "SECTION")
        private String section;

        @Parameters(index = "2", paramLabel = "BITTING")
        private String bitting;

        @Option(names = "--format", defaultValue = "sfic_a2", description = "which family of housing it drops into")
        private String format;

        @Option(names = "--to", defaultValue = "", description = "who it goes to")
        private String holder;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = "the day it was cut")
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            CoreFormat chosen = choose(spec, "--format", CoreFormat.class, format);
            group.working(service -> {
                service.cutKey(code, section, chosen, Bitting.parse(bitting), Common.asDay(when), holder);
                group.out().println(code + " cut on " + section);
            });
            return Main.OK;
        }
    }

    /** Record who has a control key. */
    @Command(name = "hand-to", mixinStandardHelpOptions = true, description = "Record who has a control key.")
    static final class HandTo implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Parameters(index = "1", paramLabel = "HOLDER")
        private String holder;

        @Override
        public Integer call() {
            group.working(service -> {
                service.handTo(code, holder);
                group.out().println(code + " is with " + holder);
            });
            return Main.OK;
        }
    }

    /** Take a control key out of service. */
    @Command(name = "withdraw", mixinStandardHelpOptions = true, description = "Take a control key out of service.")
    static final class Withdraw implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = "the day it was cut")
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> {
                service.withdraw(code, Common.asDay(when));
                group.out().println(code + " withdrawn");
            });
            return Main.OK;
        }
    }

    /** Put a core on the register. */
    @Command(name = "core", mixinStandardHelpOptions = true, description = "Put a core on the register.")
    static final class RegisterCore implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", paramLabel = "SERIAL")
        private String serial;

        @Parameters(index = "1", paramLabel = "CODE")
        private String code;

        @Option(names = "--format", defaultValue = "sfic_a2", description = "which family of housing it drops into")
        private String format;

        @Option(names = "--housing", defaultValue = "mortise")
        private String housing;

        @Option(names = "--in", defaultValue = "", description = "the door it is already in")
        private String opening;

        @Override
        public Integer call() {
            CoreFormat chosenFormat = choose(spec, "--format", CoreFormat.class, format);
            HousingKind chosenHousing = choose(spec, "--housing", HousingKind.class, housing);
            group.working(service -> {
                service.registerCore(serial, chosenFormat, code, chosenHousing, opening);
                group.out().println(serial + " on the register, pulled by " + code);
            });
            return Main.OK;
        }
    }

    /** Put a core into a housing. */
    @Command(name = "install", mixinStandardHelpOptions = true, description = "Put a core into a housing.")
    static final class Install implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Parameters(index = "0", paramLabel = "SERIAL")
        private String serial;

        @Parameters(index = "1", paramLabel = "OPENING")
        private String opening;

        @Override
        public Integer call() {
            group.working(service -> {
                service.install(serial, opening);
                group.out().println(serial + " is in " + opening);
            });
            return Main.OK;
        }
    }

    /** Take a core out, which is what a control key is for. */
    @Command(
            name = "pull",
            mixinStandardHelpOptions = true,
            description = "Take a core out, which is what a control key is for.")
    static final class Pull implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Parameters(index = "0", paramLabel = "SERIAL")
        private String serial;

        @Override
        public Integer call() {
            group.working(service -> {
                service.pull(serial);
                group.out().println(serial + " is off the wall");
            });
            return Main.OK;
        }
    }

    /** Every control key, live first. */
    @Command(name = "keys", mixinStandardHelpOptions = true, description = "Every control key, live first.")
    static final class Keys implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                ControlRegister found = service.register();
                if (found.size() == 0) {
                    group.out().println("no control keys on the register");
                    return;
                }
                List<String> lines = new ArrayList<>();
                for (ControlKey key : found) {
                    lines.add(key.describe());
                }
                Common.echoLines(group.out(), lines);
            });
            return Main.OK;
        }
    }

    /** Who holds a control key. The shortest page in the building. */
    @Command(
            name = "holders",
            mixinStandardHelpOptions = true,
            description = "Who holds a control key. The shortest page in the building.")
    static final class Holders implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.holders()));
            return Main.OK;
        }
    }

    /** Every core, or every core one control key pulls. */
    @Command(
            name = "cores",
            mixinStandardHelpOptions = true,
            description = "Every core, or every core one control key pulls.")
    static final class Cores implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Parameters(index = "0", paramLabel = "CODE", arity = "0..1", defaultValue = "")
        private String code;

        @Override
        public Integer call() {
            group.working(service -> {
                List<CoreRow> rows =
                        code.isEmpty() ? service.cores().all() : service.cores().pulledBy(code);
                if (rows.isEmpty()) {
                    group.out().println("no cores on the register");
                    return;
                }
                for (CoreRow row : rows) {
                    group.out()
                            .println(Text.left(row.getSerial(), 10)
                                    + Text.left(row.getControlCode(), 8)
                                    + Text.left(row.getHousing(), 16)
                                    + row.getInstalledAt().orElse("on the shelf"));
                }
            });
            return Main.OK;
        }
    }

    /** Where a core could be moved across a control key. */
    @Command(
            name = "crossings",
            mixinStandardHelpOptions = true,
            description = "Where a core could be moved across a control key.")
    static final class Crossings implements Callable<Integer> {
        @ParentCommand
        private ControlCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.report()));
            return Main.OK;
        }
    }
}
