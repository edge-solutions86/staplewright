package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.control.DepositBook;
import io.github.rkaushikdev.staplewright.control.DepositEntry;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.Keyholder;
import io.github.rkaushikdev.staplewright.control.Notice;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;

/**
 * The paper key control produces: statements, chase lists and summaries.
 *
 * <p>A keyholder statement is the one that gets signed. It lists every key one person is holding,
 * what each one is cut to, when it is due, and what the shop is holding in deposits against it, and
 * the person signs at the bottom to say they agree. Getting the deposit figure wrong on that sheet is
 * how an argument starts, so it comes off the deposit book rather than off a field somebody typed.
 */
public final class ControlPages {

    static final int TAG_WIDTH = 8;
    static final int SYMBOL_WIDTH = 10;
    static final int DATE_WIDTH = 12;

    private ControlPages() {}

    private static String day(Optional<LocalDate> when) {
        return when.map(LocalDate::toString).orElse("");
    }

    /** A deposit book with nothing in it counts as no book at all, so the column stays blank. */
    static Optional<DepositBook> inUse(Optional<DepositBook> book) {
        return book.filter(found -> found.size() > 0);
    }

    /** One key on one line: tag, cut, issued and due, with no deposit column filled in. */
    public static String keyLine(IssuedKey key) {
        return keyLine(key, Optional.empty());
    }

    /** One key on one line, with what the book holds against it. */
    public static String keyLine(IssuedKey key, DepositBook book) {
        return keyLine(key, Optional.of(book));
    }

    /**
     * One key on one line: tag, cut, issued and due, then the deposit the book holds against it. The
     * deposit is blank when there is no book or the book has nothing in it.
     */
    public static String keyLine(IssuedKey key, Optional<DepositBook> book) {
        String held = inUse(book)
                .map(found -> DepositBook.dollars(found.heldOn(key.tag())))
                .orElse("");
        return Text.left(key.tag(), TAG_WIDTH)
                + Text.left(key.symbol(), SYMBOL_WIDTH)
                + Text.left(key.issuedOn().toString(), DATE_WIDTH)
                + Text.left(day(key.dueOn()), DATE_WIDTH)
                + held;
    }

    /** Every key one person is holding, with no deposits and no date at the top. */
    public static List<String> keyholderStatement(Keyholder person, KeyLedger ledger) {
        return keyholderStatement(person, ledger, Optional.empty(), Optional.empty());
    }

    /** Every key one person is holding, with the deposits the book holds against them. */
    public static List<String> keyholderStatement(Keyholder person, KeyLedger ledger, DepositBook book) {
        return keyholderStatement(person, ledger, Optional.of(book), Optional.empty());
    }

    /** Every key one person is holding, dated, with no deposits. */
    public static List<String> keyholderStatement(Keyholder person, KeyLedger ledger, LocalDate on) {
        return keyholderStatement(person, ledger, Optional.empty(), Optional.of(on));
    }

    /** Every key one person is holding, for them to sign against. */
    public static List<String> keyholderStatement(
            Keyholder person, KeyLedger ledger, Optional<DepositBook> book, Optional<LocalDate> on) {
        List<IssuedKey> keys = ledger.heldBy(person.identifier());
        List<String> lines = new ArrayList<>();
        lines.add(person.label() + "  " + person.department());
        on.ifPresent(day -> lines.add("as at " + day));
        lines.add(Text.left("tag", TAG_WIDTH)
                + Text.left("cut", SYMBOL_WIDTH)
                + Text.left("issued", DATE_WIDTH)
                + Text.left("due", DATE_WIDTH)
                + "deposit");
        for (IssuedKey key : keys) {
            lines.add(Layout.stripEnd(keyLine(key, book)));
        }
        lines.add(keys.size() + " keys");
        book.ifPresent(
                found -> lines.add("deposits held " + DepositBook.dollars(found.balanceOf(person.identifier()))));
        lines.add("signed ............................  date ..............");
        return List.copyOf(lines);
    }

    /** Who in a department holds what, and who should not hold anything. */
    public static List<String> departmentSummary(
            Directory directory, KeyLedger ledger, String department, LocalDate today) {
        List<Keyholder> people = directory.inDepartment(department);
        List<String> lines = new ArrayList<>();
        lines.add(department + ": " + people.size() + " on the books");
        int total = 0;
        for (Keyholder person : people) {
            int keys = ledger.heldBy(person.identifier()).size();
            total += keys;
            String standing =
                    person.mayHoldKeys(today) ? "" : "  [" + person.standing().value() + "]";
            lines.add("  " + Text.left(person.label(), 34) + keys + " keys" + standing);
        }
        lines.add(total + " keys out to this department");
        return List.copyOf(lines);
    }

    /** The chase list, worst first. */
    public static List<String> overdueReport(KeyLedger ledger, LocalDate today) {
        List<IssuedKey> late = ledger.overdue(today);
        if (late.isEmpty()) {
            return List.of("nothing overdue as at " + today);
        }
        List<String> lines = new ArrayList<>();
        lines.add(late.size() + " keys overdue as at " + today);
        for (IssuedKey key : late) {
            String who = Text.left(key.tag(), TAG_WIDTH) + Text.left(key.keyholder(), SYMBOL_WIDTH);
            lines.add("  " + who + key.daysLate(today) + " days");
        }
        return List.copyOf(lines);
    }

    /** What the letters run produced, and the holds it asks the counter to place. */
    public static List<String> noticeRun(List<Notice> notices) {
        if (notices.isEmpty()) {
            return List.of("no letters to send today");
        }
        List<String> lines = new ArrayList<>();
        lines.add(notices.size() + " letters");
        for (Notice notice : notices) {
            lines.add("  " + notice.describe());
        }
        TreeSet<String> holds = new TreeSet<>();
        for (Notice notice : notices) {
            if (notice.placesHold()) {
                holds.add(notice.keyholder());
            }
        }
        if (!holds.isEmpty()) {
            lines.add("holds to place: " + String.join(", ", holds));
        }
        return List.copyOf(lines);
    }

    /** Every deposit line against one person, and what is left. */
    public static List<String> depositStatement(DepositBook book, String keyholder) {
        List<String> lines = new ArrayList<>();
        lines.add("deposits for " + keyholder);
        for (DepositEntry entry : book.entriesOf(keyholder)) {
            lines.add("  " + entry.when() + "  " + Text.left(entry.tag(), TAG_WIDTH)
                    + Text.left(entry.kind().value(), 10) + DepositBook.dollars(entry.signedCents()));
        }
        lines.add("held " + DepositBook.dollars(book.balanceOf(keyholder)));
        return List.copyOf(lines);
    }
}
