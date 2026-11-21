package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.StaplewrightError;
import io.github.rkaushikdev.staplewright.persistence.Database;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.settings.Settings;
import io.github.rkaushikdev.staplewright.settings.ShopSettings;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * The command line, which is how the bench uses any of this.
 *
 * <p>Nobody in the shop opens a browser to look up a bitting. They have a terminal on the wall by
 * the key machine and they type four characters and a symbol, so the commands are short, the
 * output is plain text with no colour, and every one of them prints something a person can read
 * out over a telephone.
 *
 * <p>A refusal comes back as one line on standard error, {@code refused: } and the reason, with an
 * exit code of {@value #REFUSED}. Arguments that cannot be read exit {@value #MISUSED}. Nothing
 * prints a stack trace at somebody holding a key blank.
 */
@Command(
        name = "staplewright",
        mixinStandardHelpOptions = true,
        version = "staplewright, version " + Staplewright.VERSION,
        description = "Key control and master key system design for the lock shop.")
public final class Main implements Common.Sessions {

    /** It worked. */
    public static final int OK = 0;

    /** A refusal, or a person declining to go on. */
    public static final int REFUSED = 1;

    /** The arguments could not be read. */
    public static final int MISUSED = 2;

    /** The database a command works on when nobody names one: a file beside where it runs. */
    public static final String DEFAULT_URL = "jdbc:h2:./staplewright";

    /** The variable that names the database when {@code --database} does not. */
    public static final String DATABASE_VARIABLE = Settings.PREFIX + "DATABASE";

    /** The commands that hang off the root rather than off a group. */
    public static final List<Class<?>> COMMANDS = List.of(
            ServiceCommands.Build.class,
            ServiceCommands.ShowSettings.class,
            ServiceCommands.Tables.class,
            ServiceCommands.Demo.class,
            ServiceCommands.Check.class);

    /**
     * Every command group, in the order the help lists them: sorted by the name a person types,
     * which for the handover group is {@code import} and for the annual return group is {@code
     * return}.
     */
    public static final List<Class<?>> GROUPS = List.of(
            BoxCommands.class,
            ControlCommands.class,
            CounterCommands.class,
            DoorCommands.class,
            ExportCommands.class,
            ImportCommands.class,
            KeywayCommands.class,
            PhasingCommands.class,
            RekeyCommands.class,
            ReleaseCommands.class,
            ReportCommands.class,
            AnnualCommands.class,
            StampingCommands.class,
            StockCommands.class,
            SystemCommands.class);

    private final Terminal terminal;

    @Option(
            names = "--database",
            paramLabel = "URL",
            description = "the database to work on (default: $" + "STAPLEWRIGHT_DATABASE, then " + DEFAULT_URL + ")")
    private String database;

    private Database opened;

    Main(Terminal terminal) {
        this.terminal = terminal;
    }

    /** The console entry point. */
    public static void main(String... args) {
        System.exit(run(Terminal.system(), args));
    }

    /** Run one command line, with refusals turned into one readable line and an exit code. */
    public static int run(Terminal terminal, String... args) {
        Main main = new Main(terminal);
        CommandLine command = commandLine(main);
        try {
            return command.execute(args);
        } finally {
            main.close();
            terminal.out().flush();
            terminal.err().flush();
        }
    }

    /** The command tree on one root, for running and for reading its help. */
    static CommandLine commandLine(Main main) {
        CommandLine command = new CommandLine(main);
        for (Class<?> each : COMMANDS) {
            command.addSubcommand(each);
        }
        for (Class<?> each : GROUPS) {
            command.addSubcommand(each);
        }
        return command.setExpandAtFiles(false)
                .setOverwrittenOptionsAllowed(true)
                .setOut(main.terminal.out())
                .setErr(main.terminal.err())
                .setExecutionExceptionHandler((trouble, line, parsed) -> main.refused(trouble));
    }

    private int refused(Exception trouble) throws Exception {
        if (trouble instanceof StaplewrightError refusal) {
            terminal.err().println("refused: " + refusal.getMessage());
            return REFUSED;
        }
        if (trouble instanceof Stopped) {
            terminal.err().println("stopped");
            return REFUSED;
        }
        throw trouble;
    }

    Terminal terminal() {
        return terminal;
    }

    /** The database URL: {@code --database}, then the environment, then the file beside here. */
    String url() {
        if (database != null && !database.isEmpty()) {
            return database;
        }
        String named = terminal.environment().apply(DATABASE_VARIABLE);
        return named == null || Text.isBlank(named) ? DEFAULT_URL : Text.strip(named);
    }

    /** The shop's settings, off the environment the command was started in. */
    ShopSettings settings() {
        return Settings.fromLookup(terminal.environment());
    }

    /** The database, opened the first time a command asks. */
    Database database() {
        if (opened == null) {
            opened = terminal.databases().open(url());
        }
        return opened;
    }

    /** {@code work} in a session that commits when it returns and rolls back when it throws. */
    @Override
    public void inSession(Consumer<Session> work) {
        database().inSession(work);
    }

    /** What {@code work} answers, in a session that commits when it returns. */
    <T> T fromSession(Function<Session, T> work) {
        return database().fromSession(work);
    }

    /**
     * Ask before going on, and stop on anything but a yes. An empty answer, a no, or nothing typed at
     * all is a refusal to go on; anything else asks again.
     *
     * @throws Stopped when the person declines
     */
    void confirm(String question) {
        while (true) {
            terminal.out().print(question + " [y/N]: ");
            terminal.out().flush();
            String answer;
            try {
                answer = terminal.in().readLine();
            } catch (java.io.IOException unreadable) {
                throw new java.io.UncheckedIOException(unreadable);
            }
            if (answer == null) {
                terminal.out().println();
                throw new Stopped();
            }
            String said = Text.lower(Text.strip(answer));
            if (said.equals("y") || said.equals("yes")) {
                return;
            }
            if (said.isEmpty() || said.equals("n") || said.equals("no")) {
                throw new Stopped();
            }
            terminal.out().println("Error: invalid input");
        }
    }

    private void close() {
        if (opened != null) {
            terminal.databases().release(opened);
            opened = null;
        }
    }
}
