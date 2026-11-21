package io.github.rkaushikdev.staplewright.persistence;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.persistence.models.CounterRow;
import io.github.rkaushikdev.staplewright.util.Text;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.stream.IntStream;

/**
 * The next number in each series, taken one at a time, without leaving holes.
 *
 * <p>Every series the shop uses is consecutive and people notice when it is not. A key tag between
 * 41207 and 41209 exists somewhere by definition, and a work order number that skips 1042 gets
 * somebody asking what happened on that job. So the counter row is read with a lock and written in
 * the same transaction as whatever consumes the number: if the transaction rolls back, the number
 * is never handed out.
 */
public final class Numbering {

    private final Session session;

    public Numbering(Session session) {
        this.session = session;
    }

    /** Start a series with no prefix, four digits wide, at one. */
    public CounterRow openSeries(String name) {
        return openSeries(name, "", 4, 1);
    }

    /**
     * Start a series. A series is opened once and then only walked.
     *
     * @throws InputError for a blank name, a width under one digit, or a start below one
     * @throws ConflictError when the series is already open
     */
    public CounterRow openSeries(String name, String prefix, int width, int start) {
        if (Text.isBlank(name)) {
            throw new InputError("a series needs a name");
        }
        if (width < 1) {
            throw new InputError("a number is at least one digit wide", Facts.of("series", name));
        }
        if (start < 1) {
            throw new InputError("a series starts at one or later", Facts.of("series", name));
        }
        if (session.get(CounterRow.class, name).isPresent()) {
            throw new ConflictError("that series is already open", Facts.of("series", name));
        }
        CounterRow row = new CounterRow(name);
        row.setPrefix(prefix);
        row.setWidth(width);
        row.setNextValue(start);
        session.add(row);
        session.flush();
        return row;
    }

    private CounterRow locked(String name) {
        return session
                .query("select c from CounterRow c where c.name = ?1", CounterRow.class, name)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList()
                .stream()
                .findFirst()
                .orElseThrow(() -> new NotFoundError("no such series", Facts.of("series", name)));
    }

    /** What the next number would be, without taking it. */
    public String peek(String name) {
        CounterRow row = locked(name);
        return row.render(row.getNextValue());
    }

    /** Take the next number. Rolling back puts it back. */
    public String next(String name) {
        CounterRow row = locked(name);
        int value = row.getNextValue();
        row.setNextValue(value + 1);
        session.flush();
        return row.render(value);
    }

    /**
     * Take a run of numbers at once, for a batch of keys off one order.
     *
     * @throws InputError when asked for fewer than one
     */
    public List<String> reserve(String name, int count) {
        if (count < 1) {
            throw new InputError("reserving nothing is not a reservation", Facts.of("series", name));
        }
        CounterRow row = locked(name);
        int first = row.getNextValue();
        row.setNextValue(first + count);
        session.flush();
        return IntStream.range(first, first + count).mapToObj(row::render).toList();
    }

    /** The counter row of one series, locked for the rest of the transaction. */
    public CounterRow series(String name) {
        return locked(name);
    }

    /** Every series, by name. */
    public List<CounterRow> allSeries() {
        return session.list("select c from CounterRow c order by c.name", CounterRow.class);
    }
}
