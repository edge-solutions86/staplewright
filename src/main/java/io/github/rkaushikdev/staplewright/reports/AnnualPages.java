package io.github.rkaushikdev.staplewright.reports;

import io.github.rkaushikdev.staplewright.campus.CoverageMap;
import io.github.rkaushikdev.staplewright.control.Directory;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.control.KeyState;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Numbers;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The annual key control return, which is the document the shop is judged on.
 *
 * <p>Once a year somebody in risk management asks four questions and the answers go in a file
 * nobody reads until something happens. How many keys are out. How many of those the shop cannot
 * account for. How many masters exist and who has them. And what changed since last year.
 *
 * <p>The last one is the only interesting one. A shop with two hundred keys out is neither good nor
 * bad on its own, but a shop that had a hundred and eighty out last year and two hundred and forty
 * this year has stopped saying no to somebody, and that is a finding.
 *
 * <p>The masters section is the one that gets read. Every key above change key level is listed by
 * name with who holds it, because the whole argument for a master key system is that a small number
 * of people can open a lot of doors, and the only way that stays true is somebody looking at the
 * list.
 */
public final class AnnualPages {

    static final int NAME_WIDTH = 18;
    static final int SYMBOL_WIDTH = 8;

    /** How many holders the concentration list names unless asked for more or fewer. */
    public static final int CONCENTRATION_LIMIT = 5;

    private AnnualPages() {}

    /** Whether a symbol is above change key level, which is the whole cut. */
    public static boolean isMaster(String symbol) {
        return !KeySymbol.parse(symbol).isChangeKey();
    }

    /** Every master key that is out, shallowest symbol first then by tag. */
    public static List<IssuedKey> mastersOut(KeyLedger ledger) {
        return ledger.out().stream()
                .filter(key -> isMaster(key.symbol()))
                .sorted(Comparator.comparingInt(
                                (IssuedKey key) -> KeySymbol.parse(key.symbol()).level())
                        .thenComparing(IssuedKey::tag))
                .toList();
    }

    /** Keys nobody can produce: lost, or out past the day they were due, in tag order. */
    public static List<IssuedKey> unaccountedKeys(KeyLedger ledger, LocalDate on) {
        List<IssuedKey> found = new ArrayList<>();
        for (IssuedKey key : ledger) {
            if (key.state() == KeyState.LOST) {
                found.add(key);
            }
        }
        for (IssuedKey key : ledger.overdue(on)) {
            if (key.state() != KeyState.LOST) {
                found.add(key);
            }
        }
        found.sort(Comparator.comparing(IssuedKey::tag));
        return List.copyOf(found);
    }

    /**
     * Measure the year, which is four counts off two objects.
     *
     * @param directory the people the ledger issues to, which the return is filed against
     */
    public static AnnualReturn take(KeyLedger ledger, Directory directory, LocalDate on) {
        List<IssuedKey> out = ledger.out();
        Set<String> holders = new HashSet<>();
        for (IssuedKey key : out) {
            holders.add(key.keyholder());
        }
        return new AnnualReturn(
                on.getYear(),
                out.size(),
                mastersOut(ledger).size(),
                unaccountedKeys(ledger, on).size(),
                holders.size());
    }

    /** Every master that is out, with who has it. */
    public static List<String> masterTable(KeyLedger ledger) {
        return masterTable(ledger, Optional.empty());
    }

    /** Every master that is out, with who has it and how many doors it reaches. */
    public static List<String> masterTable(KeyLedger ledger, CoverageMap coverage) {
        return masterTable(ledger, Optional.of(coverage));
    }

    /** Every master that is out, with who has it and, when the coverage is known, how far it reaches. */
    public static List<String> masterTable(KeyLedger ledger, Optional<CoverageMap> coverage) {
        List<IssuedKey> found = mastersOut(ledger);
        if (found.isEmpty()) {
            return List.of("no master keys are out");
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " master keys are out");
        lines.add(Layout.columns(List.of("symbol", "tag", "keyholder", "doors"), SYMBOL_WIDTH, 10, NAME_WIDTH));
        for (IssuedKey key : found) {
            String reach = coverage.map(map -> String.valueOf(map.reachOf(KeySymbol.parse(key.symbol()))))
                    .orElse("");
            lines.add(Layout.columns(
                    List.of(key.symbol(), key.tag(), key.keyholder(), reach), SYMBOL_WIDTH, 10, NAME_WIDTH));
        }
        return List.copyOf(lines);
    }

    /** The keys nobody can produce, which is the number that gets quoted. */
    public static List<String> unaccountedTable(KeyLedger ledger, LocalDate on) {
        List<IssuedKey> found = unaccountedKeys(ledger, on);
        if (found.isEmpty()) {
            return List.of("every key out is accounted for");
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " keys cannot be accounted for");
        for (IssuedKey key : found) {
            String why = key.state() == KeyState.LOST ? "lost" : "overdue";
            lines.add(Layout.columns(
                    List.of(key.tag(), key.symbol(), key.keyholder(), why), 10, SYMBOL_WIDTH, NAME_WIDTH));
        }
        return List.copyOf(lines);
    }

    /** Who holds the most keys, which is where a single loss hurts most. */
    public static List<Tally> concentration(KeyLedger ledger) {
        return concentration(ledger, CONCENTRATION_LIMIT);
    }

    /** The {@code limit} people holding the most keys, most first and a tie in identifier order. */
    public static List<Tally> concentration(KeyLedger ledger, int limit) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IssuedKey key : ledger.out()) {
            counts.merge(key.keyholder(), 1, Integer::sum);
        }
        return Tally.firstOf(
                counts.entrySet().stream()
                        .map(entry -> new Tally(entry.getKey(), entry.getValue()))
                        .sorted(Tally.MOST_FIRST)
                        .toList(),
                limit);
    }

    /** What changed against last year, when there was a return last year. */
    public static List<String> movement(AnnualReturn thisYear, AnnualReturn lastYear) {
        return movement(thisYear, Optional.of(lastYear));
    }

    /** What changed, which is the only part anybody argues about. */
    public static List<String> movement(AnnualReturn thisYear, Optional<AnnualReturn> lastYear) {
        if (lastYear.isEmpty()) {
            return List.of("no return was filed last year, so there is nothing to compare");
        }
        AnnualReturn before = lastYear.get();
        List<String> lines = new ArrayList<>();
        lines.add("against " + before.year());
        lines.add(change("keys out", thisYear.keysOut(), before.keysOut()));
        lines.add(change("masters out", thisYear.mastersOut(), before.mastersOut()));
        lines.add(change("unaccounted", thisYear.unaccounted(), before.unaccounted()));
        lines.add(change("keyholders", thisYear.keyholders(), before.keyholders()));
        return List.copyOf(lines);
    }

    private static String change(String name, int now, int before) {
        int change = now - before;
        String way = change > 0 ? "up" : change < 0 ? "down" : "level";
        return "  " + Text.left(name, 14) + Text.right(now, 6) + Text.right(before, 8) + "   " + way + " "
                + Math.abs(change);
    }

    /** The whole return, with no coverage and nothing to compare against. */
    public static List<String> annualReturn(KeyLedger ledger, Directory directory, LocalDate on) {
        return annualReturn(ledger, directory, on, Optional.empty(), Optional.empty());
    }

    /** The whole return, saying how far each master reaches. */
    public static List<String> annualReturn(KeyLedger ledger, Directory directory, LocalDate on, CoverageMap coverage) {
        return annualReturn(ledger, directory, on, Optional.of(coverage), Optional.empty());
    }

    /** The whole return, in the order the form asks for it. */
    public static List<String> annualReturn(
            KeyLedger ledger,
            Directory directory,
            LocalDate on,
            Optional<CoverageMap> coverage,
            Optional<AnnualReturn> lastYear) {
        AnnualReturn found = take(ledger, directory, on);
        List<String> lines = new ArrayList<>();
        lines.add("key control return for " + found.year());
        lines.add(found.describe());
        lines.add(found.keyholders() + " keyholders, " + Numbers.fixed(found.keysPerHolder(), 1) + " keys each");
        lines.add("");
        lines.addAll(masterTable(ledger, coverage));
        lines.add("");
        lines.addAll(unaccountedTable(ledger, on));
        lines.add("");
        lines.add("most keys held");
        for (Tally held : concentration(ledger)) {
            lines.add("  " + Text.left(held.name(), NAME_WIDTH) + held.count());
        }
        lines.add("");
        lines.addAll(movement(found, lastYear));
        return List.copyOf(lines);
    }

    /** Several years side by side, oldest first, for the trend. */
    public static List<String> returnsTable(Collection<AnnualReturn> found) {
        if (found.isEmpty()) {
            return List.of("no returns have been filed");
        }
        List<String> lines = new ArrayList<>();
        lines.add(Layout.columns(List.of("year", "out", "masters", "unaccounted", "holders"), 8, 8, 10, 14));
        found.stream()
                .sorted(Comparator.comparingInt(AnnualReturn::year))
                .forEach(item -> lines.add(Layout.columns(
                        List.of(item.year(), item.keysOut(), item.mastersOut(), item.unaccounted(), item.keyholders()),
                        8,
                        8,
                        10,
                        14)));
        return List.copyOf(lines);
    }
}
