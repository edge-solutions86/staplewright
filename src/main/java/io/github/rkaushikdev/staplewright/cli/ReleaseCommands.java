package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.Staplewright;
import io.github.rkaushikdev.staplewright.release.Releases;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;

/**
 * Release commands, for the afternoon somebody upgrades a shop two versions. {@code upgrade} is the
 * one worth having: it says which migrations the jump crosses and which notes to read first.
 */
@Command(
        name = "release",
        mixinStandardHelpOptions = true,
        description = "Versions, what each one changed, and what an upgrade crosses.",
        subcommands = {
            ReleaseCommands.Version.class,
            ReleaseCommands.History.class,
            ReleaseCommands.Upgrade.class,
            ReleaseCommands.Migrations.class
        })
final class ReleaseCommands {

    @ParentCommand
    Main main;

    /** This version and what it settled. */
    @Command(name = "version", mixinStandardHelpOptions = true, description = "This version and what it settled.")
    static final class Version implements Callable<Integer> {

        @ParentCommand
        private ReleaseCommands group;

        @Override
        public Integer call() {
            Common.echoLines(group.main.terminal().out(), Releases.describeCurrent());
            if (!Releases.matchesPackage(Staplewright.VERSION)) {
                group.main
                        .terminal()
                        .out()
                        .println("the package says " + Staplewright.VERSION + ", which is not the latest release");
            }
            return Main.OK;
        }
    }

    /** Every release, oldest first. */
    @Command(name = "history", mixinStandardHelpOptions = true, description = "Every release, oldest first.")
    static final class History implements Callable<Integer> {

        @ParentCommand
        private ReleaseCommands group;

        @Override
        public Integer call() {
            Common.echoLines(group.main.terminal().out(), Releases.historySheet());
            return Main.OK;
        }
    }

    /** What an upgrade crosses, and what to read before starting it. */
    @Command(
            name = "upgrade",
            mixinStandardHelpOptions = true,
            description = "What an upgrade crosses, and what to read before starting it.")
    static final class Upgrade implements Callable<Integer> {

        @ParentCommand
        private ReleaseCommands group;

        @Parameters(paramLabel = "OLDER")
        private String older;

        @Option(names = "--to", paramLabel = "NEWER", defaultValue = "", description = "the version being upgraded to")
        private String newer;

        @Override
        public Integer call() {
            Common.echoLines(group.main.terminal().out(), Releases.upgradeSheet(older, newer));
            return Main.OK;
        }
    }

    /** Every migration any release has shipped, oldest first. */
    @Command(
            name = "migrations",
            mixinStandardHelpOptions = true,
            description = "Every schema migration any release has shipped, oldest first.")
    static final class Migrations implements Callable<Integer> {

        @ParentCommand
        private ReleaseCommands group;

        @Override
        public Integer call() {
            Common.echoLines(group.main.terminal().out(), Releases.everyMigration());
            return Main.OK;
        }
    }
}
