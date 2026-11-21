package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.reports.Page;
import io.github.rkaushikdev.staplewright.reports.ReportIndex;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Finding a report, which is the first thing anybody needs to do.
 *
 * <p>{@code staplewright reports} with nothing after it prints the whole index. That is the command
 * to type when somebody has described a page and nobody can remember whether it exists.
 */
@Command(
        name = "reports",
        mixinStandardHelpOptions = true,
        description = "Every page this library prints.",
        subcommands = {
            ReportCommands.ByModule.class,
            ReportCommands.Modules.class,
            ReportCommands.ByReader.class,
            ReportCommands.Readers.class,
            ReportCommands.Find.class,
            ReportCommands.Unlisted.class
        })
final class ReportCommands implements Callable<Integer> {

    @ParentCommand
    Main main;

    PrintWriter out() {
        return main.terminal().out();
    }

    /** The whole index, when no command follows the group's name. */
    @Override
    public Integer call() {
        Common.echoLines(out(), ReportIndex.indexSheet());
        return Main.OK;
    }

    /** The pages one module prints. */
    @Command(name = "module", mixinStandardHelpOptions = true, description = "The pages one module prints.")
    static final class ByModule implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            Common.echoLines(group.out(), ReportIndex.indexSheet(name));
            return Main.OK;
        }
    }

    /** The report modules there are. */
    @Command(name = "modules", mixinStandardHelpOptions = true, description = "The report modules there are.")
    static final class Modules implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Override
        public Integer call() {
            Common.echoLines(group.out(), ReportIndex.everyModule());
            return Main.OK;
        }
    }

    /** The pages one kind of person asks for. */
    @Command(name = "for", mixinStandardHelpOptions = true, description = "The pages one kind of person asks for.")
    static final class ByReader implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Parameters(index = "0", paramLabel = "READER")
        private String reader;

        @Override
        public Integer call() {
            Common.echoLines(group.out(), ReportIndex.readerSheet(reader));
            return Main.OK;
        }
    }

    /** Who asks for a report, which is how people arrive at one. */
    @Command(
            name = "readers",
            mixinStandardHelpOptions = true,
            description = "Who asks for a report, which is how people arrive at one.")
    static final class Readers implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Override
        public Integer call() {
            Common.echoLines(group.out(), ReportIndex.readers());
            return Main.OK;
        }
    }

    /** Where one page lives, so it can be found again. */
    @Command(
            name = "find",
            mixinStandardHelpOptions = true,
            description = "Where one page lives, so it can be found again.")
    static final class Find implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            Page found = ReportIndex.page(name);
            group.out().println(found.path());
            group.out().println(found.answers() + ", asked for by " + found.askedBy());
            return Main.OK;
        }
    }

    /** Report methods that are not on the index, which should be none. Exits one when there are some. */
    @Command(
            name = "unlisted",
            mixinStandardHelpOptions = true,
            description = "Report methods that are not on the index, which should be none.")
    static final class Unlisted implements Callable<Integer> {
        @ParentCommand
        private ReportCommands group;

        @Override
        public Integer call() {
            Map<String, List<String>> missing = new LinkedHashMap<>();
            for (String module : ReportIndex.everyModule()) {
                List<String> items = ReportIndex.undocumented(module);
                if (!items.isEmpty()) {
                    missing.put(module, items);
                }
            }
            if (missing.isEmpty()) {
                group.out().println("every report is on the index");
                return Main.OK;
            }
            missing.forEach((module, items) -> group.out().println(module + ": " + String.join(", ", items)));
            return Main.REFUSED;
        }
    }
}
