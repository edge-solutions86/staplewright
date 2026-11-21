package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.Schema;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ParentCommand;

/**
 * The commands that are about the database rather than about keys: building the schema, listing
 * the tables, printing the settings, filling in an example and running the self check. None of
 * them belong to a group because none of them are about one part of the shop.
 */
final class ServiceCommands {

    private ServiceCommands() {}

    /** Create the tables, or bring an older schema up to date. */
    @Command(name = "build", mixinStandardHelpOptions = true, description = "Create the tables.")
    static final class Build implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            main.database().migrate();
            main.terminal().out().println("schema built at " + main.url());
            return Main.OK;
        }
    }

    /** Print the numbers this shop is running on. */
    @Command(
            name = "settings",
            mixinStandardHelpOptions = true,
            description = "Print the numbers this shop is running on.")
    static final class ShowSettings implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Common.echoLines(main.terminal().out(), main.settings().describe());
            return Main.OK;
        }
    }

    /** List the tables, which is the quickest check that a URL is right. */
    @Command(
            name = "tables",
            mixinStandardHelpOptions = true,
            description = "List the tables, which is the quickest check that a url is right.")
    static final class Tables implements Callable<Integer> {

        @ParentCommand
        private Main main;

        @Override
        public Integer call() {
            Common.echoLines(main.terminal().out(), Schema.tableNames());
            return Main.OK;
        }
    }
}
