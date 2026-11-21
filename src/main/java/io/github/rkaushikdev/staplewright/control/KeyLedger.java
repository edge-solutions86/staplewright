package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Tallies;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The key ledger, which is the only record of where the metal is: every key ever issued, open and
 * closed, in the order it went out.
 *
 * <p>A key is out, returned, lost or stolen. Everything that moves a key checks with the
 * {@link Directory} first, because a person who may not hold keys gets nothing over the counter
 * and nothing passed to them either.
 */
public final class KeyLedger implements Iterable<IssuedKey> {

    private final Directory directory;
    private final Registry<IssuedKey> keys = new Registry<>("key in the ledger", "tag");

    public KeyLedger(Directory directory) {
        this.directory = directory;
    }

    /** The directory this ledger checks keyholders against. */
    public Directory directory() {
        return directory;
    }

    /** Hand a key over with no due date and no deposit. */
    public IssuedKey issue(String tag, String symbol, String keyholder, LocalDate when) {
        return issue(tag, symbol, keyholder, when, Optional.empty(), 0);
    }

    /** Hand a key over against a deposit, with no due date. */
    public IssuedKey issue(String tag, String symbol, String keyholder, LocalDate when, int depositCents) {
        return issue(tag, symbol, keyholder, when, Optional.empty(), depositCents);
    }

    /** Hand a key over until {@code dueOn}, with no deposit. */
    public IssuedKey issue(String tag, String symbol, String keyholder, LocalDate when, LocalDate dueOn) {
        return issue(tag, symbol, keyholder, when, Optional.of(dueOn), 0);
    }

    /**
     * Hand a key over, refusing anybody who may not hold one.
     *
     * <p>A key lent to somebody whose record runs out cannot be due back after the record does.
     *
     * @throws ConflictError when the tag is already in the ledger, the keyholder may not hold keys,
     *     or the key would be due after their contract ends
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when the keyholder is not in the
     *     directory
     */
    public IssuedKey issue(
            String tag, String symbol, String keyholder, LocalDate when, Optional<LocalDate> dueOn, int depositCents) {
        if (keys.has(tag)) {
            throw new ConflictError("that tag is already in the ledger", Facts.of("tag", tag));
        }
        Keyholder person = directory.person(keyholder);
        if (!person.mayHoldKeys(when)) {
            throw new ConflictError(
                    "that keyholder may not hold keys",
                    Facts.of(
                            "tag",
                            tag,
                            "keyholder",
                            keyholder,
                            "standing",
                            person.standing().value()));
        }
        if (person.endsOn().isPresent()
                && dueOn.isPresent()
                && dueOn.get().isAfter(person.endsOn().get())) {
            throw new ConflictError(
                    "a key cannot be due back after the contract ends",
                    Facts.of(
                            "tag",
                            tag,
                            "due_on",
                            dueOn.get().toString(),
                            "ends_on",
                            person.endsOn().get().toString()));
        }
        return keys.add(tag, new IssuedKey(tag, symbol, keyholder, when, dueOn, depositCents));
    }

    /**
     * The key with this tag, out or not.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when the tag was never issued
     */
    public IssuedKey key(String tag) {
        return keys.get(tag);
    }

    /**
     * Put a key back as it was stored, checking nothing.
     *
     * <p>A key issued in 2019 to somebody who has since separated is still a key that is out, and
     * refusing it on the way back out of the database would hide exactly the row the shop is
     * looking for.
     */
    public IssuedKey load(IssuedKey key) {
        return keys.put(key.tag(), key);
    }

    /** A key comes back over the counter. */
    public IssuedKey takeBack(String tag, LocalDate when) {
        return takeBack(tag, when, "");
    }

    /** A key comes back over the counter, with a word about why. */
    public IssuedKey takeBack(String tag, LocalDate when, String note) {
        return store(mustBeOut(tag).closedAs(KeyState.RETURNED, when, note));
    }

    public IssuedKey reportLost(String tag, LocalDate when) {
        return reportLost(tag, when, "");
    }

    public IssuedKey reportLost(String tag, LocalDate when, String note) {
        return store(mustBeOut(tag).closedAs(KeyState.LOST, when, note));
    }

    /** A key was taken. A theft is always written up. */
    public IssuedKey reportStolen(String tag, LocalDate when, String note) {
        IssuedKey key = mustBeOut(tag);
        if (Text.isBlank(note)) {
            throw new InputError("a theft is written up", Facts.of("tag", tag));
        }
        return store(key.closedAs(KeyState.STOLEN, when, note));
    }

    /** A key reported lost turned up. It comes back, it does not go out. */
    public IssuedKey found(String tag, LocalDate when) {
        IssuedKey key = key(tag);
        if (!key.isGone()) {
            throw new ConflictError(
                    "that key was not reported gone",
                    Facts.of("tag", tag, "state", key.state().value()));
        }
        return store(key.closedAs(KeyState.RETURNED, when, key.note()));
    }

    /**
     * Move a key between people without it touching the counter.
     *
     * <p>The shop dislikes this and does it anyway, so it is recorded rather than pretended away.
     * The new holder has to be able to hold keys.
     */
    public IssuedKey transfer(String tag, String toKeyholder, LocalDate when) {
        IssuedKey key = mustBeOut(tag);
        Keyholder person = directory.person(toKeyholder);
        if (!person.mayHoldKeys(when)) {
            throw new ConflictError("that keyholder may not hold keys", Facts.of("tag", tag, "keyholder", toKeyholder));
        }
        if (key.keyholder().equals(toKeyholder)) {
            throw new ConflictError("that key is already theirs", Facts.of("tag", tag));
        }
        return store(key.handedTo(toKeyholder, when));
    }

    /** Push a due date out. It cannot be pulled back. */
    public IssuedKey extend(String tag, LocalDate until) {
        IssuedKey key = mustBeOut(tag);
        if (key.dueOn().isEmpty()) {
            throw new ConflictError("that key has no due date", Facts.of("tag", tag));
        }
        if (!until.isAfter(key.dueOn().get())) {
            throw new ConflictError("an extension moves the date forward", Facts.of("tag", tag));
        }
        return store(key.dueBy(until));
    }

    private IssuedKey mustBeOut(String tag) {
        IssuedKey key = key(tag);
        Lifecycle.requireState(key.state(), List.of(KeyState.OUT), "that key is not out", Facts.of("tag", tag));
        return key;
    }

    private IssuedKey store(IssuedKey key) {
        return keys.put(key.tag(), key);
    }

    /** The keys one person has out right now. */
    public List<IssuedKey> heldBy(String keyholder) {
        directory.person(keyholder);
        return keys.where(key -> key.keyholder().equals(keyholder) && key.isOut());
    }

    /** Every key one person has ever signed for, out or not. */
    public List<IssuedKey> historyOf(String keyholder) {
        directory.person(keyholder);
        return keys.where(key -> key.keyholder().equals(keyholder));
    }

    public List<IssuedKey> out() {
        return keys.where(IssuedKey::isOut);
    }

    /** Every key reported lost or stolen. */
    public List<IssuedKey> gone() {
        return keys.where(IssuedKey::isGone);
    }

    /** Keys out past their due date, the latest first, then by tag. */
    public List<IssuedKey> overdue(LocalDate today) {
        return out().stream()
                .filter(key -> key.overdueOn(today))
                .sorted(Comparator.comparing((IssuedKey key) -> key.daysLate(today), Comparator.reverseOrder())
                        .thenComparing(IssuedKey::tag))
                .toList();
    }

    /** The keys out that were cut to one symbol. */
    public List<IssuedKey> cutTo(String symbol) {
        return keys.where(key -> key.symbol().equals(symbol) && key.isOut());
    }

    /** The deposits taken on every key that is out, in cents. */
    public long depositsHeld() {
        return out().stream().mapToLong(IssuedKey::depositCents).sum();
    }

    /** How many keys are out cut to each symbol, the most common first, then by symbol. */
    public Map<String, Integer> countBySymbol() {
        return Tallies.count(out(), IssuedKey::symbol);
    }

    /** Whether a key with this tag was ever issued. */
    public boolean contains(String tag) {
        return keys.has(tag);
    }

    /** Every key, in the order it went out. */
    @Override
    public Iterator<IssuedKey> iterator() {
        return keys.iterator();
    }

    public int size() {
        return keys.size();
    }
}
