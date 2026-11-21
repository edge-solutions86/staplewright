package io.github.rkaushikdev.staplewright.cli;

import io.github.rkaushikdev.staplewright.persistence.Session;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/**
 * The options every command group shares, spelled one way.
 *
 * <p>A day is {@value #DAY}, a moment is {@value #MOMENT}, and both default to now when nobody
 * says. A person typing at the wall terminal should not have to remember which group they are in.
 */
public final class Common {

    /** The option for a day. */
    public static final String DAY = "--on";

    /** The option for a moment, logged to the minute. */
    public static final String MOMENT = "--at";

    /** What the day option says it is, unless a command says better. */
    public static final String DAY_HELP = "the day it happened";

    /** What the moment option says it is, unless a command says better. */
    public static final String MOMENT_HELP = "when it happened";

    /** The one shape a day is typed in. */
    public static final String DAY_FORMAT = "%Y-%m-%d";

    private static final DateTimeFormatter DAY_PATTERN = DateTimeFormatter.ofPattern("uuuu-MM-dd", Locale.ROOT);
    private static final List<DateTimeFormatter> MOMENT_PATTERNS = List.of(
            DAY_PATTERN,
            DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss", Locale.ROOT),
            DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ROOT));

    private Common() {}

    /** A {@value #DAY} value, or today when nobody said. */
    public static LocalDate asDay(Optional<LocalDateTime> day) {
        return day.map(LocalDateTime::toLocalDate).orElseGet(LocalDate::now);
    }

    /** A {@value #MOMENT} value, or now when nobody said. */
    public static LocalDateTime asMoment(Optional<LocalDateTime> moment) {
        return moment.orElseGet(LocalDateTime::now);
    }

    /** A {@value #DAY} value, or a day the caller already knows. */
    public static LocalDate asGivenDay(Optional<LocalDateTime> day, LocalDate otherwise) {
        return day.map(LocalDateTime::toLocalDate).orElse(otherwise);
    }

    /** Whatever hands out sessions that commit when the work returns and roll back when it throws. */
    @FunctionalInterface
    public interface Sessions {

        /** {@code work} in a session of its own. */
        void inSession(Consumer<Session> work);
    }

    /**
     * A service built on a session and handed to {@code work}, which is what a command needs. Every
     * command opens a session and builds its service on the next line, and doing it here means the
     * two can never be passed the wrong way round.
     */
    public static <S> void working(Sessions shop, Function<Session, S> service, Consumer<S> work) {
        shop.inSession(session -> work.accept(service.apply(session)));
    }

    /** Print a report, one line each. Reports come back as lines and always have. */
    public static void echoLines(PrintWriter out, Iterable<String> lines) {
        for (String line : lines) {
            out.println(line);
        }
    }

    /** Reads a {@value #DAY} value: a day written year, month, day. */
    public static final class DayConverter implements ITypeConverter<LocalDateTime> {
        @Override
        public LocalDateTime convert(String value) {
            try {
                return LocalDate.parse(value, DAY_PATTERN).atStartOfDay();
            } catch (DateTimeParseException wrongShape) {
                throw new TypeConversionException("'" + value + "' does not match the format '" + DAY_FORMAT + "'.");
            }
        }
    }

    /** Reads a {@value #MOMENT} value: a day, or a day and a time to the second. */
    public static final class MomentConverter implements ITypeConverter<LocalDateTime> {
        @Override
        public LocalDateTime convert(String value) {
            return MOMENT_PATTERNS.stream()
                    .map(pattern -> attempt(value, pattern))
                    .flatMap(Optional::stream)
                    .findFirst()
                    .orElseThrow(() -> wrong(value));
        }

        private static Optional<LocalDateTime> attempt(String value, DateTimeFormatter pattern) {
            try {
                return Optional.of(
                        pattern == DAY_PATTERN
                                ? LocalDate.parse(value, pattern).atStartOfDay()
                                : LocalDateTime.parse(value, pattern));
            } catch (DateTimeParseException notThisShape) {
                return Optional.empty();
            }
        }

        private static TypeConversionException wrong(String value) {
            return new TypeConversionException("'" + value + "' does not match the formats '%Y-%m-%d', "
                    + "'%Y-%m-%dT%H:%M:%S', '%Y-%m-%d %H:%M:%S'.");
        }
    }
}
