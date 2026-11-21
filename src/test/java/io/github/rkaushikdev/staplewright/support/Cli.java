package io.github.rkaushikdev.staplewright.support;

import io.github.rkaushikdev.staplewright.cli.Main;
import io.github.rkaushikdev.staplewright.cli.Terminal;
import io.github.rkaushikdev.staplewright.persistence.Database;
import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The command line as the tests drive it: one call, its exit code, and everything it printed on
 * either stream, in the order it printed it.
 *
 * <p>The suite's test database is handed over as it is whenever a command names its URL, so a
 * command works on what the test wrote there without a new mapping being started per call. Any
 * other URL is opened afresh and closed when the command is done, as it is for a person.
 */
public final class Cli {

    private static final Terminal.Databases SHARED_FIRST = new Terminal.Databases() {
        @Override
        public Database open(String url) {
            return url.equals(TestDatabase.URL) ? TestDatabase.database() : Database.open(url);
        }

        @Override
        public void release(Database database) {
            if (database != TestDatabase.database()) {
                database.close();
            }
        }
    };

    private Cli() {}

    /** One command line, with no environment and nothing typed. */
    public static Result call(String... args) {
        return run(Map.of(), "", args);
    }

    /** One command line against the suite's test database. */
    public static Result onTestDatabase(String... args) {
        List<String> all = new ArrayList<>(List.of("--database", TestDatabase.URL));
        all.addAll(List.of(args));
        return call(all.toArray(String[]::new));
    }

    /** One command line with an environment and what the person types. */
    public static Result run(Map<String, String> environment, String input, String... args) {
        StringWriter printed = new StringWriter();
        PrintWriter out = new PrintWriter(printed, true);
        Terminal terminal =
                new Terminal(environment::get, new BufferedReader(new StringReader(input)), out, out, SHARED_FIRST);
        int code = Main.run(terminal, args);
        out.flush();
        return new Result(code, printed.toString());
    }

    /**
     * What one command did.
     *
     * @param exitCode what the shell would see
     * @param output everything printed, answers and refusals together
     */
    public record Result(int exitCode, String output) {

        /** The output, one entry a line. */
        public List<String> lines() {
            return output.lines().toList();
        }
    }
}
