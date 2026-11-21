package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.Database;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.util.function.UnaryOperator;

/**
 * What a command talks to: the environment it was started in, the keyboard, the two streams, and
 * the way a database is opened from a URL.
 *
 * <p>Everything a command prints goes to {@code out}; a refusal is one line on {@code err}.
 *
 * @param environment the variables the command was started with
 * @param in what the person types
 * @param out where the command's answer goes
 * @param err where a refusal goes
 * @param databases how a database URL becomes an open database, and how it is let go
 */
public record Terminal(
        UnaryOperator<String> environment, BufferedReader in, PrintWriter out, PrintWriter err, Databases databases) {

    /** The process's own environment, keyboard and streams, opening each database afresh. */
    public static Terminal system() {
        Charset charset = Charset.defaultCharset();
        return new Terminal(
                System.getenv()::get,
                new BufferedReader(new InputStreamReader(System.in, charset)),
                new PrintWriter(System.out, true, charset),
                new PrintWriter(System.err, true, charset),
                Databases.FRESH);
    }

    /** How a command gets its database, and gives it back. */
    public interface Databases {

        /** Open a database for every command and close it when the command is done. */
        Databases FRESH = new Databases() {
            @Override
            public Database open(String url) {
                return Database.open(url);
            }

            @Override
            public void release(Database database) {
                database.close();
            }
        };

        /** The database at {@code url}. Nothing connects until something is asked of it. */
        Database open(String url);

        /** The command is done with it. */
        void release(Database database);
    }
}
