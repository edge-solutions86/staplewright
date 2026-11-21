package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.services.ExportService;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
 * Export commands, which end with somebody dragging a file into a system.
 *
 * <p>Everything writes to standard output by default rather than to a file, so a person can look at
 * it before it goes anywhere, and a pipe does the rest. The one command that writes files is the bulk
 * one, because dropping four files in a shared folder is the whole job it exists for.
 */
@Command(
        name = "export",
        mixinStandardHelpOptions = true,
        description = "Tables out of the database, for whatever is asking for them.",
        subcommands = {
            ExportCommands.Listing.class,
            ExportCommands.OneTable.class,
            ExportCommands.Columns.class,
            ExportCommands.Everything.class,
            ExportCommands.Schedule.class,
            ExportCommands.NameFor.class
        })
final class ExportCommands {

    private static final String WHEN_HELP = "the day to stamp on the file";

    @ParentCommand
    Main main;

    void working(Consumer<ExportService> work) {
        Common.working(main, ExportService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Write {@code text} to {@code path} as it is, in UTF-8. */
    static void write(Path path, String text) {
        try {
            Files.writeString(path, text, StandardCharsets.UTF_8);
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
    }

    /** The format option every export command shares. */
    static final class Shape {
        @Spec(Spec.Target.MIXEE)
        private CommandSpec spec;

        @Option(
                names = "--as",
                description = "the format to write: ${COMPLETION-CANDIDATES} (default: csv)",
                completionCandidates = Formats.class)
        private String shape = "csv";

        /** The format asked for, refusing one nothing writes. */
        String chosen() {
            if (!ExportService.FORMATS.contains(shape)) {
                String listed = ExportService.FORMATS.stream()
                        .map(item -> "'" + item + "'")
                        .collect(Collectors.joining(", "));
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for '--as': '" + shape + "' is not one of " + listed + ".");
            }
            return shape;
        }
    }

    /** The formats, as the option lists them. */
    static final class Formats implements Iterable<String> {
        @Override
        public Iterator<String> iterator() {
            return ExportService.FORMATS.iterator();
        }
    }

    /** The day option the commands that name a file share. */
    static final class Day {
        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = WHEN_HELP)
        Optional<LocalDateTime> when = Optional.empty();
    }

    /** What can be exported. */
    @Command(name = "list", mixinStandardHelpOptions = true, description = "What can be exported.")
    static final class Listing implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.names()));
            return Main.OK;
        }
    }

    /** One table, written to standard output. */
    @Command(name = "table", mixinStandardHelpOptions = true, description = "One table, written to standard output.")
    static final class OneTable implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @CommandLine.Mixin
        private Shape shape;

        @Option(names = "--system", description = "which keying system, for a schedule")
        private String system = "";

        @Override
        public Integer call() {
            String format = shape.chosen();
            group.working(service -> group.out().print(service.text(name, format, system)));
            return Main.OK;
        }
    }

    /** The columns one export has, which is what a mapping needs. */
    @Command(
            name = "columns",
            mixinStandardHelpOptions = true,
            description = "The columns one export has, which is what a mapping needs.")
    static final class Columns implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> live = service.systemsToExport();
                String system = name.equals("schedule") && !live.isEmpty() ? live.get(0) : "";
                Common.echoLines(group.out(), service.rowsFor(name, system).header());
            });
            return Main.OK;
        }
    }

    /** Drop every table into a folder, stamped with the day. */
    @Command(
            name = "all",
            mixinStandardHelpOptions = true,
            description = "Drop every table into a folder, stamped with the day.")
    static final class Everything implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "FOLDER")
        private Path folder;

        @CommandLine.Mixin
        private Shape shape;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            String format = shape.chosen();
            if (Files.isRegularFile(folder)) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for 'FOLDER': Directory '" + folder + "' is a file.");
            }
            LocalDate on = Common.asDay(day.when);
            try {
                Files.createDirectories(folder);
            } catch (IOException trouble) {
                throw new UncheckedIOException(trouble);
            }
            group.working(service -> {
                for (Map.Entry<String, String> each :
                        service.everyTable(on, format).entrySet()) {
                    Path path = folder.resolve(each.getKey());
                    write(path, each.getValue());
                    group.out().println(path);
                }
            });
            return Main.OK;
        }
    }

    /** One system schedule, to standard output or to a file. */
    @Command(
            name = "schedule",
            mixinStandardHelpOptions = true,
            description = "One system schedule, to standard output or to a file.")
    static final class Schedule implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "SYSTEM")
        private String system;

        @CommandLine.Mixin
        private Shape shape;

        @CommandLine.Mixin
        private Day day;

        @Option(names = "--to", paramLabel = "FILE", description = "the file to write instead of standard output")
        private Optional<Path> target = Optional.empty();

        @Override
        public Integer call() {
            String format = shape.chosen();
            if (target.isPresent() && Files.isDirectory(target.get())) {
                throw new CommandLine.ParameterException(
                        spec.commandLine(), "Invalid value for '--to': File '" + target.get() + "' is a directory.");
            }
            group.working(service -> {
                String text = service.text("schedule", format, system);
                if (target.isEmpty()) {
                    group.out().print(text);
                    return;
                }
                write(target.get(), text);
                group.out().println(target.get());
            });
            return Main.OK;
        }
    }

    /** What the file would be called, without writing it. */
    @Command(
            name = "name",
            mixinStandardHelpOptions = true,
            description = "What the file would be called, without writing it.")
    static final class NameFor implements Callable<Integer> {
        @ParentCommand
        private ExportCommands group;

        @Parameters(index = "0", paramLabel = "TABLE")
        private String table;

        @CommandLine.Mixin
        private Shape shape;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            String format = shape.chosen();
            group.working(service -> group.out().println(service.filename(table, Common.asDay(day.when), format)));
            return Main.OK;
        }
    }
}
