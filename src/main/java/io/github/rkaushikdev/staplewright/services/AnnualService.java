package io.github.rkaushikdev.staplewright.services;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.persistence.Session;
import io.github.rkaushikdev.staplewright.persistence.Workspace;
import io.github.rkaushikdev.staplewright.persistence.models.AnnualReturnRow;
import io.github.rkaushikdev.staplewright.reports.AnnualPages;
import io.github.rkaushikdev.staplewright.reports.AnnualReturn;
import io.github.rkaushikdev.staplewright.reports.Tally;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Taking the return, filing it, and arguing with last year.
 *
 * <p>{@link #measure} and {@link #fileReturn} are separate on purpose. Everybody wants to run the
 * numbers before they commit to them, and a shop that has to file a return in order to see it will
 * file three and delete two, which is how a trend acquires a hole in it.
 *
 * <p>Filing over a year already on record is refused rather than merged. The one time somebody
 * wants to do that is after a clean up, when the numbers have got better, and letting last year
 * improve quietly is exactly what a return exists to prevent.
 */
public final class AnnualService {

    private final Session session;
    private final Workspace space;
    private final ReturnRepository returns;

    public AnnualService(Session session) {
        this(session, new Workspace(session));
    }

    /** The return read through a workspace the caller already holds. */
    public AnnualService(Session session, Workspace workspace) {
        this.session = session;
        this.space = workspace;
        this.returns = new ReturnRepository(session);
    }

    public Session session() {
        return session;
    }

    /** The returns on file. */
    public ReturnRepository returns() {
        return returns;
    }

    /** The numbers a filed row holds. */
    public static AnnualReturn toReturn(AnnualReturnRow row) {
        return new AnnualReturn(
                row.getYear(), row.getKeysOut(), row.getMastersOut(), row.getUnaccounted(), row.getKeyholders());
    }

    /** A row for a return measured on a day, filed by nobody named and with no note. */
    public static AnnualReturnRow toRow(AnnualReturn found, LocalDate on) {
        return toRow(found, on, "", "");
    }

    /** A row for a return measured on a day, with who filed it and why. */
    public static AnnualReturnRow toRow(AnnualReturn found, LocalDate on, String filedBy, String note) {
        AnnualReturnRow row = new AnnualReturnRow(
                found.year(), on, found.keysOut(), found.mastersOut(), found.unaccounted(), found.keyholders());
        row.setFiledBy(filedBy);
        row.setNote(note);
        return row;
    }

    /** Run the numbers without committing to them. */
    public AnnualReturn measure(LocalDate on) {
        return AnnualPages.take(space.ledger(), space.directory(), on);
    }

    /** The return filed for a year, refusing a year nobody filed. */
    public AnnualReturnRow filed(int year) {
        return returns.require(year, "no return was filed for that year", Facts.of("year", year));
    }

    /** The most recent filed return before a year, if there is one. */
    public Optional<AnnualReturn> previous(int year) {
        return returns.all().stream()
                .filter(row -> row.getYear() < year)
                .reduce((first, second) -> second)
                .map(AnnualService::toReturn);
    }

    /** Measure and write it down, filed by nobody named. */
    public AnnualReturnRow fileReturn(LocalDate on) {
        return fileReturn(on, "", "");
    }

    public AnnualReturnRow fileReturn(LocalDate on, String filedBy) {
        return fileReturn(on, filedBy, "");
    }

    /**
     * Measure and write it down, refusing to file over a year.
     *
     * @throws ConflictError when the year of {@code on} is already filed
     */
    public AnnualReturnRow fileReturn(LocalDate on, String filedBy, String note) {
        if (returns.find(on.getYear()).isPresent()) {
            throw new ConflictError("that year is already filed", Facts.of("year", on.getYear()));
        }
        AnnualReturnRow row = toRow(measure(on), on, filedBy, note);
        session.add(row);
        session.flush();
        return row;
    }

    /**
     * Take a return off the record, which needs a reason on it.
     *
     * <p>Not a delete. The row stays and the note says why the numbers on it are not to be used,
     * because a year that vanishes from a trend is a year somebody has to go and look for in an
     * email.
     *
     * @throws ConflictError when no reason is given
     */
    public AnnualReturnRow withdraw(int year, String note) {
        if (Text.isBlank(note)) {
            throw new ConflictError("withdrawing a return says why", Facts.of("year", year));
        }
        AnnualReturnRow row = filed(year);
        row.setNote("withdrawn: " + note);
        session.flush();
        return row;
    }

    /** Every filed year, oldest first. */
    public List<AnnualReturn> history() {
        return returns.all().stream().map(AnnualService::toReturn).toList();
    }

    /** The whole return as the page it gets printed on. */
    public List<String> sheet(LocalDate on) {
        return AnnualPages.annualReturn(
                space.ledger(), space.directory(), on, Optional.of(space.coverage()), previous(on.getYear()));
    }

    /** Every master key that is out, and who has it. */
    public List<String> masters() {
        return AnnualPages.masterTable(space.ledger(), space.coverage());
    }

    /** The keys nobody can produce. */
    public List<String> unaccounted(LocalDate on) {
        return AnnualPages.unaccountedTable(space.ledger(), on);
    }

    /** The five people holding the most keys. */
    public List<Tally> concentration() {
        return concentration(AnnualPages.CONCENTRATION_LIMIT);
    }

    /** The people holding the most keys, which is where one loss hurts most. */
    public List<Tally> concentration(int limit) {
        return AnnualPages.concentration(space.ledger(), limit);
    }

    /** Every filed year side by side. */
    public List<String> trend() {
        return AnnualPages.returnsTable(history());
    }

    /** What changed against the last filed year, without filing this one. */
    public List<String> againstLastYear(LocalDate on) {
        return AnnualPages.movement(measure(on), previous(on.getYear()));
    }
}
