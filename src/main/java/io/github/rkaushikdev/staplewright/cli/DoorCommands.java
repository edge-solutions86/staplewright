package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.campus.Condition;
import io.github.rkaushikdev.staplewright.campus.Deficiency;
import io.github.rkaushikdev.staplewright.campus.Identification;
import io.github.rkaushikdev.staplewright.campus.KeyedAlikeSet;
import io.github.rkaushikdev.staplewright.campus.Survey;
import io.github.rkaushikdev.staplewright.campus.SurveyLine;
import io.github.rkaushikdev.staplewright.control.Answer;
import io.github.rkaushikdev.staplewright.control.Review;
import io.github.rkaushikdev.staplewright.persistence.models.LockoutRow;
import io.github.rkaushikdev.staplewright.persistence.models.ReviewRow;
import io.github.rkaushikdev.staplewright.persistence.models.SurveyRow;
import io.github.rkaushikdev.staplewright.services.KeyedAlikeService;
import io.github.rkaushikdev.staplewright.services.LockoutService;
import io.github.rkaushikdev.staplewright.services.ReviewService;
import io.github.rkaushikdev.staplewright.services.SurveyService;
import io.github.rkaushikdev.staplewright.util.Text;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Commands for the doors: lockout calls, surveys, reviews, keyed alike sets.
 *
 * <p>These are the ones typed with one hand. A lockout is logged while somebody is still on the
 * telephone, so the command takes the door and the person and nothing else, and the technician and
 * the time go on afterwards when there is time to type them.
 */
@Command(
        name = "doors",
        mixinStandardHelpOptions = true,
        description = "Lockouts, surveys, reviews and keyed alike sets.",
        subcommands = {
            DoorCommands.LockoutSeries.class,
            DoorCommands.LogLockout.class,
            DoorCommands.Attend.class,
            DoorCommands.LetIn.class,
            DoorCommands.SurveyStart.class,
            DoorCommands.SurveyLineCommand.class,
            DoorCommands.SurveyCommand.class,
            DoorCommands.ReviewOpen.class,
            DoorCommands.ReviewAnswer.class,
            DoorCommands.Reviews.class,
            DoorCommands.Alike.class,
            DoorCommands.AlikeList.class
        })
final class DoorCommands {

    /** What a review answer is typed as, and the answer each word stands for. */
    private static final Map<String, Answer> ANSWERS =
            Map.of("confirmed", Answer.CONFIRMED, "return", Answer.RETURN, "unknown", Answer.UNKNOWN);

    @ParentCommand
    Main main;

    PrintWriter out() {
        return main.terminal().out();
    }

    /** Refuse a value outside {@code allowed} the way any other unreadable option is refused. */
    static String oneOf(CommandSpec spec, String option, String value, List<String> allowed) {
        if (!allowed.contains(value)) {
            String listed = allowed.stream().map(item -> "'" + item + "'").collect(Collectors.joining(", "));
            throw new CommandLine.ParameterException(
                    spec.commandLine(),
                    "Invalid value for '" + option + "': '" + value + "' is not one of " + listed + ".");
        }
        return value;
    }

    /** The moment option the lockout commands share. */
    static final class Moment {
        @Option(names = Common.MOMENT, converter = Common.MomentConverter.class, description = Common.MOMENT_HELP)
        Optional<LocalDateTime> when = Optional.empty();
    }

    /** The day option the survey and review commands share. */
    static final class Day {
        @Option(names = Common.DAY, converter = Common.DayConverter.class, description = Common.DAY_HELP)
        Optional<LocalDateTime> when = Optional.empty();
    }

    /** Every identification a door is opened on: anything but none. */
    static final class Identifications implements Iterable<String> {
        static List<String> values() {
            return Arrays.stream(Identification.values())
                    .filter(item -> item != Identification.NONE)
                    .map(Identification::value)
                    .toList();
        }

        @Override
        public Iterator<String> iterator() {
            return values().iterator();
        }
    }

    /** Every condition as it is typed: its name in lower case. */
    static final class Conditions implements Iterable<String> {
        static List<String> values() {
            return Arrays.stream(Condition.values())
                    .map(item -> Text.lower(item.name()))
                    .toList();
        }

        @Override
        public Iterator<String> iterator() {
            return values().iterator();
        }
    }

    /** Every review answer as it is typed. */
    static final class Answers implements Iterable<String> {
        static List<String> values() {
            return List.of("confirmed", "return", "unknown");
        }

        @Override
        public Iterator<String> iterator() {
            return values().iterator();
        }
    }

    /** Open the lockout call counter. Once, when the shop is set up. */
    @Command(
            name = "lockout-series",
            mixinStandardHelpOptions = true,
            description = "Open the lockout call counter. Once, when the shop is set up.")
    static final class LockoutSeries implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                new LockoutService(session).openSeries();
                group.out().println("lockout series opened");
            });
            return Main.OK;
        }
    }

    /** Log a lockout call. */
    @Command(name = "lockout", mixinStandardHelpOptions = true, description = "Log a lockout call.")
    static final class LogLockout implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "OPENING")
        private String opening;

        @Parameters(index = "1", paramLabel = "KEYHOLDER")
        private String keyholder;

        @CommandLine.Mixin
        private Moment moment;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                LockoutRow row = new LockoutService(session).log(opening, keyholder, Common.asMoment(moment.when));
                group.out().println(row.getNumber() + " logged for " + keyholder + " at " + opening);
            });
            return Main.OK;
        }
    }

    /** Record who went. */
    @Command(name = "attend", mixinStandardHelpOptions = true, description = "Record who went.")
    static final class Attend implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Parameters(index = "1", paramLabel = "TECHNICIAN")
        private String technician;

        @CommandLine.Mixin
        private Moment moment;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                new LockoutService(session).attend(number, technician, Common.asMoment(moment.when));
                group.out().println(number + " attended by " + technician);
            });
            return Main.OK;
        }
    }

    /** Open the door, recording how they were identified. */
    @Command(
            name = "let-in",
            mixinStandardHelpOptions = true,
            description = "Open the door, recording how they were identified.")
    static final class LetIn implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "NUMBER")
        private String number;

        @Option(
                names = "--how",
                description = "how they were identified: ${COMPLETION-CANDIDATES}",
                completionCandidates = Identifications.class)
        private String how = Identification.CAMPUS_CARD.value();

        @CommandLine.Mixin
        private Moment moment;

        @Override
        public Integer call() {
            Identification identified = Identification.fromValue(oneOf(spec, "--how", how, Identifications.values()));
            group.main.inSession(session -> {
                new LockoutService(session).openDoor(number, identified, Common.asMoment(moment.when));
                group.out().println(number + " opened, identified by " + how);
            });
            return Main.OK;
        }
    }

    /** Start a walk round a building. */
    @Command(name = "survey-start", mixinStandardHelpOptions = true, description = "Start a walk round a building.")
    static final class SurveyStart implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "BUILDING")
        private String building;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                SurveyRow row = new SurveyService(session).start(building, Common.asDay(day.when));
                group.out().println("survey of " + row.getBuildingCode() + " opened for " + row.getWalkedOn());
            });
            return Main.OK;
        }
    }

    /** Record one door on a walk, or correct the line already there. */
    @Command(
            name = "survey-line",
            mixinStandardHelpOptions = true,
            description = "Record one door on a walk, or correct the line already there.")
    static final class SurveyLineCommand implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "BUILDING")
        private String building;

        @Parameters(index = "1", paramLabel = "OPENING")
        private String opening;

        @Parameters(index = "2", paramLabel = "SURVEYOR")
        private String surveyor;

        @Option(
                names = "--condition",
                description = "how the hardware is doing: ${COMPLETION-CANDIDATES}",
                completionCandidates = Conditions.class)
        private String condition = "good";

        @Option(names = "--fault", description = "a deficiency, repeatable")
        private List<String> faults = new ArrayList<>();

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            Condition found = Condition.valueOf(Text.upper(oneOf(spec, "--condition", condition, Conditions.values())));
            List<Deficiency> deficiencies = new ArrayList<>();
            for (String name : faults) {
                deficiencies.add(Deficiency.find(name)
                        .orElseThrow(() -> new CommandLine.ParameterException(
                                spec.commandLine(), "Invalid value: no such deficiency: " + name)));
            }
            group.main.inSession(session -> {
                new SurveyService(session)
                        .record(building, Common.asDay(day.when), opening, surveyor, found, deficiencies);
                group.out().println(opening + " recorded " + condition);
            });
            return Main.OK;
        }
    }

    /** Print one walk, worst door first. */
    @Command(name = "survey", mixinStandardHelpOptions = true, description = "Print one walk, worst door first.")
    static final class SurveyCommand implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "BUILDING")
        private String building;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                Survey walk = new SurveyService(session).survey(building, Common.asDay(day.when));
                group.out().println(walk.building() + " " + walk.walkedOn() + ", " + walk.size() + " doors");
                for (SurveyLine line : walk.worstFirst()) {
                    group.out().println("  " + line.describe());
                }
            });
            return Main.OK;
        }
    }

    /** Open a review and freeze what the department holds. */
    @Command(
            name = "review-open",
            mixinStandardHelpOptions = true,
            description = "Open a review and freeze what the department holds.")
    static final class ReviewOpen implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "DEPARTMENT")
        private String department;

        @Parameters(index = "1", paramLabel = "DUE", converter = Common.DayConverter.class)
        private LocalDateTime due;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                ReviewRow row =
                        new ReviewService(session).openReview(department, Common.asDay(day.when), due.toLocalDate());
                group.out().println(row.getDepartment() + " review opened, due " + row.getDueOn());
            });
            return Main.OK;
        }
    }

    /** Record what a department said about one key. */
    @Command(
            name = "review-answer",
            mixinStandardHelpOptions = true,
            description = "Record what a department said about one key.")
    static final class ReviewAnswer implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Spec
        private CommandSpec spec;

        @Parameters(index = "0", paramLabel = "DEPARTMENT")
        private String department;

        @Parameters(index = "1", paramLabel = "TAG")
        private String tag;

        @Option(
                names = "--answer",
                description = "what the department said: ${COMPLETION-CANDIDATES}",
                completionCandidates = Answers.class)
        private String answer = "confirmed";

        @Option(names = "--opened", required = true, converter = Common.DayConverter.class)
        private LocalDateTime opened;

        @CommandLine.Mixin
        private Day day;

        @Override
        public Integer call() {
            Answer wanted = ANSWERS.get(oneOf(spec, "--answer", answer, Answers.values()));
            group.main.inSession(session -> {
                new ReviewService(session)
                        .answer(department, opened.toLocalDate(), tag, wanted, Common.asDay(day.when));
                group.out().println(tag + " " + wanted.value());
            });
            return Main.OK;
        }
    }

    /** The reviews still waiting on a department. */
    @Command(
            name = "reviews",
            mixinStandardHelpOptions = true,
            description = "The reviews still waiting on a department.")
    static final class Reviews implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Override
        public Integer call() {
            Common.working(group.main, ReviewService::new, service -> {
                List<ReviewRow> rows = service.openReviews();
                if (rows.isEmpty()) {
                    group.out().println("nothing open");
                    return;
                }
                for (ReviewRow row : rows) {
                    Review review = service.review(row.getDepartment(), row.getOpenedOn());
                    group.out()
                            .println(Text.left(row.getDepartment(), 16) + "due " + row.getDueOn() + "  "
                                    + review.summary());
                }
            });
            return Main.OK;
        }
    }

    /** Make a keyed alike set out of two or more doors. */
    @Command(
            name = "alike",
            mixinStandardHelpOptions = true,
            description = "Make a keyed alike set out of two or more doors.")
    static final class Alike implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Parameters(index = "0", paramLabel = "NAME")
        private String name;

        @Parameters(index = "1..*", arity = "1..*", paramLabel = "OPENINGS")
        private List<String> openings;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                new KeyedAlikeService(session).create(new KeyedAlikeSet(name, openings));
                group.out().println(name + " keyed alike across " + openings.size() + " doors");
            });
            return Main.OK;
        }
    }

    /** Every keyed alike set the shop maintains. */
    @Command(
            name = "alike-list",
            mixinStandardHelpOptions = true,
            description = "Every keyed alike set the shop maintains.")
    static final class AlikeList implements Callable<Integer> {
        @ParentCommand
        private DoorCommands group;

        @Override
        public Integer call() {
            group.main.inSession(session -> {
                List<KeyedAlikeSet> found = new KeyedAlikeService(session).sets();
                if (found.isEmpty()) {
                    group.out().println("no keyed alike sets");
                    return;
                }
                for (KeyedAlikeSet item : found) {
                    group.out().println(item.describe());
                }
            });
            return Main.OK;
        }
    }
}
