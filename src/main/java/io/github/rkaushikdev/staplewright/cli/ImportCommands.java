package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.services.ImportService;
import io.github.rkaushikdev.staplewright.services.Load;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Import commands, which somebody runs holding a file they did not make.
 *
 * <p>Every one of them dry runs by default. A load that writes on the first invocation is a load
 * somebody runs against production while working out what the columns are called, and the person
 * handing over the file is never the person who knows what is in it.
 */
@Command(
        name = "import",
        mixinStandardHelpOptions = true,
        description = "Files into the database, nothing written until the whole file reads.",
        subcommands = {
            ImportCommands.Kinds.class,
            ImportCommands.Columns.class,
            ImportCommands.LoadFile.class,
            ImportCommands.Check.class
        })
final class ImportCommands {

    @ParentCommand
    Main main;

    void working(Consumer<ImportService> work) {
        Common.working(main, ImportService::new, work);
    }

    <T> T answering(Function<ImportService, T> work) {
        return main.fromSession(session -> work.apply(new ImportService(session)));
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /**
     * The text of a file that has to be there and has to be a file, refusing anything else the way
     * any other unreadable argument is refused.
     */
    static String read(CommandSpec spec, Path path) {
        if (!Files.exists(path)) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(), "Invalid value for 'PATH': File '" + path + "' does not exist.");
        }
        if (Files.isDirectory(path)) {
            throw new CommandLine.ParameterException(
                    spec.commandLine(), "Invalid value for 'PATH': File '" + path + "' is a directory.");
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException trouble) {
            throw new UncheckedIOException(trouble);
        }
    }

    /** What can be loaded, and the columns each one wants. */
    @Command(
            name = "kinds",
            mixinStandardHelpOptions = true,
            description = "What can be loaded, and the columns each one wants.")
    static final class Kinds implements Callable<Integer> {
        @ParentCommand
        private ImportCommands group;

        @Override
        public Integer call() {
            group.working(service -> {
                for (String kind : service.kinds()) {
                    group.out().println(Text.left(kind, 14) + String.join(", ", service.columnsFor(kind)));
                }
            });
            return Main.OK;
        }
    }

    /** The columns one kind of file has to have. */
    @Command(
            name = "columns",
            mixinStandardHelpOptions = true,
            description = "The columns one kind of file has to have.")
    static final class Columns implements Callable<Integer> {
        @ParentCommand
        private ImportCommands group;

        @Parameters(index = "0", paramLabel = "KIND")
        private String kind;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), service.columnsFor(kind)));
            return Main.OK;
        }
    }

    /** Read a file and say what it would do, or do it. */
    @Command(
            name = "load",
            mixinStandardHelpOptions = true,
            description = "Read a file and say what it would do, or do it.")
    static final class LoadFile implements Callable<Integer> {
        @ParentCommand
        private ImportCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "KIND")
        private String kind;

        @Parameters(index = "1", paramLabel = "PATH")
        private Path path;

        @Option(names = "--write", description = "actually load it, rather than saying what would load")
        private boolean write;

        @Override
        public Integer call() {
            String text = read(spec, path);
            Load found = group.answering(service -> service.load(kind, text, !write));
            Common.echoLines(group.out(), found.describe());
            return found.isClean() ? Main.OK : Main.REFUSED;
        }
    }

    /** Read a file and report only the problems. */
    @Command(name = "check", mixinStandardHelpOptions = true, description = "Read a file and report only the problems.")
    static final class Check implements Callable<Integer> {
        @ParentCommand
        private ImportCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "KIND")
        private String kind;

        @Parameters(index = "1", paramLabel = "PATH")
        private Path path;

        @Override
        public Integer call() {
            String text = read(spec, path);
            Load found = group.answering(service -> service.load(kind, text, true));
            if (found.isClean()) {
                group.out().println(found.written() + " rows read, nothing to fix");
                return Main.OK;
            }
            Common.echoLines(
                    group.out(), found.describe().subList(1, found.describe().size()));
            return Main.REFUSED;
        }
    }
}
