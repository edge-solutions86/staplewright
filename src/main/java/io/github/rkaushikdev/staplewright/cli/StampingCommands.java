package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.keys.BlindCode;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.keys.Stamp;
import io.github.rkaushikdev.staplewright.persistence.models.BlindCodeRow;
import io.github.rkaushikdev.staplewright.services.StampingService;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
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
 * Stamping commands, one of which is the only reason anybody opens this group.
 *
 * <p>{@code identify} is that one. Somebody has walked in with a key they found in a car park and
 * there is a number on it. Everything else here is set up for the morning that happens. The whole
 * book is behind a command that says what it is, asks first, and prints a warning line before it
 * prints anything else, because a terminal history is not where that table should end up.
 */
@Command(
        name = "stamping",
        mixinStandardHelpOptions = true,
        description = "What goes on the brass, and the table that reads it back.",
        subcommands = {
            StampingCommands.Identify.class,
            StampingCommands.Code.class,
            StampingCommands.Assign.class,
            StampingCommands.OneKey.class,
            StampingCommands.Run.class,
            StampingCommands.Everything.class,
            StampingCommands.Blank.class,
            StampingCommands.Symbols.class,
            StampingCommands.Retire.class,
            StampingCommands.WholeBook.class
        })
final class StampingCommands {

    @ParentCommand
    Main main;

    void working(Consumer<StampingService> work) {
        Common.working(main, StampingService::new, work);
    }

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Who did it, and the day it was stamped: the options most stamping commands share. */
    static final class Signed {
        @Option(names = "--by", defaultValue = "", description = "who did it")
        String by;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = "the day it was stamped")
        Optional<LocalDateTime> when = Optional.empty();
    }

    /** A number off a found key, and what it means. */
    @Command(
            name = "identify",
            mixinStandardHelpOptions = true,
            description = "A number off a found key, and what it means.")
    static final class Identify implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(index = "0", paramLabel = "CODE")
        private String code;

        @Override
        public Integer call() {
            group.working(service -> group.out().println(service.identify(code)));
            return Main.OK;
        }
    }

    /** The number a symbol is stamped with. */
    @Command(name = "code", mixinStandardHelpOptions = true, description = "The number a symbol is stamped with.")
    static final class Code implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(index = "0", paramLabel = "SYMBOL")
        private String symbol;

        @Override
        public Integer call() {
            group.working(service -> group.out().println(service.codeFor(KeySymbol.parse(symbol))));
            return Main.OK;
        }
    }

    /** Give a symbol a blind code, or print the one it has. */
    @Command(
            name = "assign",
            mixinStandardHelpOptions = true,
            description = "Give a symbol a blind code, or print the one it has.")
    static final class Assign implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(index = "0", paramLabel = "SYMBOL")
        private String symbol;

        @CommandLine.Mixin
        private Signed signed;

        @Override
        public Integer call() {
            group.working(service -> {
                BlindCodeRow row = service.assign(KeySymbol.parse(symbol), Common.asDay(signed.when), signed.by);
                group.out().println(row.getSymbol() + " is " + row.getCode());
            });
            return Main.OK;
        }
    }

    /** What goes on one key. */
    @Command(name = "key", mixinStandardHelpOptions = true, description = "What goes on one key.")
    static final class OneKey implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(index = "0", paramLabel = "TAG")
        private String tag;

        @CommandLine.Mixin
        private Signed signed;

        @Override
        public Integer call() {
            group.working(service -> group.out()
                    .println(service.stampKey(tag, Common.asDay(signed.when), signed.by)
                            .describe()));
            return Main.OK;
        }
    }

    /** A sheet for the stamping machine. */
    @Command(name = "run", mixinStandardHelpOptions = true, description = "A sheet for the stamping machine.")
    static final class Run implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(paramLabel = "TAGS", arity = "1..*")
        private List<String> tags;

        @CommandLine.Mixin
        private Signed signed;

        @Override
        public Integer call() {
            group.working(service ->
                    Common.echoLines(group.out(), service.sheet(tags, Common.asDay(signed.when), signed.by)));
            return Main.OK;
        }
    }

    /** The stamping for every key that is out, for a re-marking run. */
    @Command(
            name = "everything",
            mixinStandardHelpOptions = true,
            description = "The stamping for every key that is out, for a re-marking run.")
    static final class Everything implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @CommandLine.Mixin
        private Signed signed;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(
                    group.out(),
                    service.everythingOut(Common.asDay(signed.when), signed.by).stream()
                            .map(Stamp::describe)
                            .toList()));
            return Main.OK;
        }
    }

    /** Keys this policy would leave unmarked, which nobody could identify. */
    @Command(
            name = "blank",
            mixinStandardHelpOptions = true,
            description = "Keys this policy would leave unmarked, which nobody could identify.")
    static final class Blank implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = "the day it was stamped")
        private Optional<LocalDateTime> when = Optional.empty();

        @Override
        public Integer call() {
            group.working(service -> {
                List<String> found = StampingService.unstampedOut(service.session(), service.policy());
                if (found.isEmpty()) {
                    group.out().println("every key out carries something");
                    return;
                }
                Common.echoLines(group.out(), found);
            });
            return Main.OK;
        }
    }

    /** Every symbol currently out, which is what needs a code. */
    @Command(
            name = "symbols",
            mixinStandardHelpOptions = true,
            description = "Every symbol currently out, which is what needs a code.")
    static final class Symbols implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Override
        public Integer call() {
            group.working(service -> Common.echoLines(group.out(), StampingService.issuedSymbols(service.session())));
            return Main.OK;
        }
    }

    /** Take a symbol out of the book, once nobody carries it. */
    @Command(
            name = "retire",
            mixinStandardHelpOptions = true,
            description = "Take a symbol out of the book, once nobody carries it.")
    static final class Retire implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Parameters(index = "0", paramLabel = "SYMBOL")
        private String symbol;

        @Override
        public Integer call() {
            group.working(service -> {
                service.retireCode(KeySymbol.parse(symbol));
                group.out().println(symbol + " is out of the book");
            });
            return Main.OK;
        }
    }

    /** Every pair in the table. This is the thing not to leave on a screen. */
    @Command(
            name = "the-whole-book",
            mixinStandardHelpOptions = true,
            description = "Every pair in the table. This is the thing not to leave on a screen.")
    static final class WholeBook implements Callable<Integer> {
        @ParentCommand
        private StampingCommands group;

        @Option(names = "--yes", description = "Confirm the action without prompting.")
        private boolean yes;

        @Override
        public Integer call() {
            if (!yes) {
                group.main.confirm("print the whole blind code table?");
            }
            group.working(service -> {
                group.out().println("this is the table that turns every stamped number into a symbol");
                for (BlindCode pair : service.theWholeBook()) {
                    group.out().println(Text.left(pair.symbol(), 10) + pair.code());
                }
            });
            return Main.OK;
        }
    }
}
