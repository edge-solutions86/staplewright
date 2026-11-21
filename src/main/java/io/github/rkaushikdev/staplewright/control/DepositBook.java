package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Every deposit line the shop has written, in order: the only part of key control that involves
 * money.
 *
 * <p>Twenty five dollars a key, fifty for anything that masters, refunded when the key comes back.
 * The amounts are unimportant and the arithmetic is not: a refund has to match a charge on the same
 * tag, a deposit can be settled exactly once, and the total the shop is holding has to reconcile
 * against the ledger of keys that are out.
 *
 * <p>Everything is in whole cents. A fractional amount in here eventually pays somebody back twenty
 * four dollars and ninety nine point nine nine cents, and then the business office asks about it
 * for a week. A single line fits a whole number of cents; the totals are added up in a wider one.
 */
public final class DepositBook implements Iterable<DepositEntry> {

    private final List<DepositEntry> entries = new ArrayList<>();

    /** Take a deposit when a key goes out. A tag takes one deposit, ever. */
    public DepositEntry charge(String tag, String keyholder, int cents, LocalDate when) {
        if (chargeFor(tag).isPresent()) {
            throw new ConflictError("a deposit was already taken on that tag", Facts.of("tag", tag));
        }
        return add(new DepositEntry(tag, keyholder, EntryKind.CHARGE, cents, when));
    }

    /** Give the whole deposit back when the key comes back. */
    public DepositEntry refund(String tag, LocalDate when) {
        return refund(tag, when, mustBeOpen(tag).cents());
    }

    /**
     * Give some of the deposit back. What is not refunded stays with the shop.
     *
     * @throws ConflictError when more is asked for than was charged, or the tag is already settled
     * @throws NotFoundError when no deposit was ever taken on the tag
     */
    public DepositEntry refund(String tag, LocalDate when, int cents) {
        DepositEntry charge = mustBeOpen(tag);
        if (cents > charge.cents()) {
            throw new ConflictError(
                    "a refund cannot be more than the deposit",
                    Facts.of("tag", tag, "charged", charge.cents(), "asked", cents));
        }
        return add(new DepositEntry(tag, charge.keyholder(), EntryKind.REFUND, cents, when));
    }

    /** The key is gone. The money stays with the shop, and the line says why. */
    public DepositEntry forfeit(String tag, LocalDate when, String note) {
        DepositEntry charge = mustBeOpen(tag);
        return add(new DepositEntry(tag, charge.keyholder(), EntryKind.FORFEIT, charge.cents(), when, note));
    }

    /** A department paid centrally, so the person owes nothing. */
    public DepositEntry waive(String tag, LocalDate when) {
        return waive(tag, when, "");
    }

    public DepositEntry waive(String tag, LocalDate when, String note) {
        DepositEntry charge = mustBeOpen(tag);
        return add(new DepositEntry(tag, charge.keyholder(), EntryKind.WAIVER, charge.cents(), when, note));
    }

    /** Put a line back as it was stored, in the order it was written. */
    public DepositEntry load(DepositEntry entry) {
        return add(entry);
    }

    private DepositEntry add(DepositEntry entry) {
        entries.add(entry);
        return entry;
    }

    private Optional<DepositEntry> chargeFor(String tag) {
        return entries.stream()
                .filter(entry -> entry.tag().equals(tag) && entry.kind() == EntryKind.CHARGE)
                .findFirst();
    }

    private DepositEntry mustBeOpen(String tag) {
        DepositEntry charge = chargeFor(tag)
                .orElseThrow(() -> new NotFoundError("no deposit was ever taken on that tag", Facts.of("tag", tag)));
        if (isClosed(tag)) {
            throw new ConflictError("that deposit is already settled", Facts.of("tag", tag));
        }
        return charge;
    }

    /** Whether the deposit on a tag has been refunded, forfeited or waived. */
    public boolean isClosed(String tag) {
        return entries.stream().anyMatch(entry -> entry.tag().equals(tag) && entry.kind() != EntryKind.CHARGE);
    }

    /** What the shop still holds against one tag, in cents. */
    public long heldOn(String tag) {
        return signedSum(entry -> entry.tag().equals(tag));
    }

    /** What the shop holds for one person across every tag, in cents. */
    public long balanceOf(String keyholder) {
        return signedSum(entry -> entry.keyholder().equals(keyholder));
    }

    /** What the shop holds altogether, in cents. */
    public long totalHeld() {
        return signedSum(entry -> true);
    }

    /** Every cent the shop kept because a key never came back. */
    public long forfeited() {
        return entries.stream()
                .filter(entry -> entry.kind() == EntryKind.FORFEIT)
                .mapToLong(DepositEntry::cents)
                .sum();
    }

    private long signedSum(Predicate<DepositEntry> matches) {
        return entries.stream()
                .filter(matches)
                .mapToLong(DepositEntry::signedCents)
                .sum();
    }

    /** The lines on one tag, in the order they were written. */
    public List<DepositEntry> entriesFor(String tag) {
        return entries.stream().filter(entry -> entry.tag().equals(tag)).toList();
    }

    /** The lines for one person, in the order they were written. */
    public List<DepositEntry> entriesOf(String keyholder) {
        return entries.stream()
                .filter(entry -> entry.keyholder().equals(keyholder))
                .toList();
    }

    /** Tags whose deposit is still held, in tag order. */
    public List<String> openTags() {
        return entries.stream()
                .filter(entry -> entry.kind() == EntryKind.CHARGE)
                .map(DepositEntry::tag)
                .filter(tag -> !isClosed(tag))
                .sorted()
                .toList();
    }

    /** The cents each kind of line moved, keyed by the kind as it is written, in that order. */
    public Map<String, Long> byKind() {
        Map<String, Long> totals = new TreeMap<>();
        for (DepositEntry entry : entries) {
            totals.merge(entry.kind().value(), (long) entry.cents(), Long::sum);
        }
        return Collections.unmodifiableMap(totals);
    }

    /** Every line, in the order it was written. */
    @Override
    public Iterator<DepositEntry> iterator() {
        return List.copyOf(entries).iterator();
    }

    public int size() {
        return entries.size();
    }

    /** Money the way a receipt prints it: {@code $25.00}, {@code -$25.00}. */
    public static String dollars(long cents) {
        String sign = cents < 0 ? "-" : "";
        long whole = Math.abs(cents) / 100;
        long part = Math.abs(cents) % 100;
        return sign + "$" + whole + "." + String.format(Locale.ROOT, "%02d", part);
    }
}
