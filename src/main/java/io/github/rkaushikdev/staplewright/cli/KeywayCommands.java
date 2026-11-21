package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.services.KeywayService;
import io.github.rkaushikdev.staplewright.spec.Exposure;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.Patents;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Keyway commands, which is where somebody finds out about a patent.
 *
 * <p>The patent question is one nobody asks on purpose. It comes up when a locksmith notices a blank
 * for sale on a website that should not have it, and the first thing they want is a straight answer
 * about which sections have gone and what is sitting on them. So the risk report runs across every
 * family, and everything else here is a way of narrowing it down.
 */
@Command(
        name = "keyways",
        mixinStandardHelpOptions = true,
        description = "Keyway families, sections, and where the patents stand.",
        subcommands = {
            KeywayCommands.Families.class,
            KeywayCommands.Sections.class,
            KeywayCommands.Tree.class,
            KeywayCommands.PatentReport.class,
            KeywayCommands.Risk.class,
            KeywayCommands.Check.class,
            KeywayCommands.Carrying.class
        })
final class KeywayCommands {

    private static final String READ_AGAINST = "the day to read the patents against";

    @ParentCommand
    Main main;

    void working(Consumer<KeywayService> work) {
        Common.working(main, KeywayService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** List the families the shop holds, with how many sections each has. */
    @Command(
            name = "families",
            mixinStandardHelpOptions = true,
            description = "List the families the shop holds, with how many sections each has.")
    static final class Families implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> found = service.families();
                if (found.isEmpty()) {
                    group.out().println("no keyway families stored");
                    return;
                }
                for (String name : found) {
                    group.out().println(name + "  " + service.family(name).size() + " sections");
                }
            });
            return Main.OK;
        }
    }

    /** Every section in a family, soonest patent expiry first. */
    @Command(
            name = "sections",
            mixinStandardHelpOptions = true,
            description = "Every section in a family, soonest patent expiry first.")
    static final class Sections implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Parameters(index = "0", paramLabel = "FAMILY")
        private String family;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = READ_AGAINST)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            LocalDate today = Common.asDay(when);
            group.working(service -> {
                for (Exposure item : service.exposures(family)) {
                    group.out().println(item.describe(today));
                }
            });
            return Main.OK;
        }
    }

    /** The family as a tree, which is how the catalogue draws it. */
    @Command(
            name = "tree",
            mixinStandardHelpOptions = true,
            description = "The family as a tree, which is how the catalogue draws it.")
    static final class Tree implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Parameters(index = "0", paramLabel = "FAMILY")
        private String family;

        @Override
        public Integer call() {
            group.working(service -> {
                KeywayFamily found = service.family(family);
                for (String code : found.codes()) {
                    group.out().println("  ".repeat(found.depthOf(code)) + code);
                }
            });
            return Main.OK;
        }
    }

    /** Where one family stands: what is gone, what is going, what is next. */
    @Command(
            name = "patents",
            mixinStandardHelpOptions = true,
            description = "Where one family stands: what is gone, what is going, what is next.")
    static final class PatentReport implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Parameters(index = "0", paramLabel = "FAMILY")
        private String family;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = READ_AGAINST)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.report(family, Common.asDay(when))));
            return Main.OK;
        }
    }

    /** Sections the shop keys on that have already gone open. */
    @Command(
            name = "risk",
            mixinStandardHelpOptions = true,
            description = "Sections the shop keys on that have already gone open.")
    static final class Risk implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = READ_AGAINST)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.riskReport(Common.asDay(when))));
            return Main.OK;
        }
    }

    /** Say whether a new system may be laid down on a section. */
    @Command(
            name = "check",
            mixinStandardHelpOptions = true,
            description = "Say whether a new system may be laid down on a section.")
    static final class Check implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @CommandLine.Spec
        private CommandLine.Model.CommandSpec spec;

        @Parameters(index = "0", paramLabel = "FAMILY")
        private String family;

        @Parameters(index = "1", paramLabel = "SECTION")
        private String section;

        @Option(
                names = "--years",
                defaultValue = "" + Patents.PLANNING_YEARS,
                showDefaultValue = CommandLine.Help.Visibility.ALWAYS,
                description = "how long the section has to stay restricted")
        private int years;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = READ_AGAINST)
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            if (years < 0) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for '--years': " + years + " is not in the range x>=0.");
            }
            LocalDate today = Common.asDay(when);
            group.working(service -> {
                service.checkSection(family, section, today, years, false);
                group.out().println(section + " is good for " + years + " years");
            });
            return Main.OK;
        }
    }

    /** Which live systems sit on one section. */
    @Command(name = "carrying", mixinStandardHelpOptions = true, description = "Which live systems sit on one section.")
    static final class Carrying implements Callable<Integer> {
        @ParentCommand
        private KeywayCommands group;

        @Parameters(index = "0", paramLabel = "FAMILY")
        private String family;

        @Parameters(index = "1", paramLabel = "SECTION")
        private String section;

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> found = service.systemsOn(family, section);
                if (found.isEmpty()) {
                    group.out().println("nothing is keyed on " + section);
                    return;
                }
                Common.echoLines(group.out(), found);
            });
            return Main.OK;
        }
    }
}
