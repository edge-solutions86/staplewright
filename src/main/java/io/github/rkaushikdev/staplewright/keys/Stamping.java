package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * What gets stamped on a key, which is a security decision disguised as a chore.
 *
 * <p>A key bow has room for about eight characters and every one of them is a choice. Stamp the
 * symbol and anybody who finds the key knows it is a master and roughly where it fits. Stamp nothing
 * and the shop cannot identify a key somebody hands in, which happens more often than losing one.
 *
 * <p>The answer the trade settled on is a blind code from the {@link BlindCodeBook}. So a key
 * carries its tag and a blind code, the symbol stays on paper, and the one place they are joined up
 * is a table in this building.
 */
public final class Stamping {

    /** The mark that goes beside every stamp when policy asks for it. */
    public static final String DO_NOT_DUPLICATE = "DND";

    private Stamping() {}

    /**
     * Work out what goes on one key with no code book to hand, refusing anything that will not fit.
     *
     * @throws ConflictError when the key would go blind, or the text does not fit on the bow
     * @throws InputError for a blank tag
     */
    public static Stamp stampFor(String tag, KeySymbol symbol, StampPolicy policy) {
        return stamp(tag, symbol, policy, Optional.empty());
    }

    /**
     * Work out what goes on one key, giving the symbol a blind code when policy says so.
     *
     * @throws ConflictError when the text does not fit on the bow
     * @throws InputError for a blank tag
     */
    public static Stamp stampFor(String tag, KeySymbol symbol, StampPolicy policy, BlindCodeBook book) {
        return stamp(tag, symbol, policy, Optional.of(book));
    }

    private static Stamp stamp(String tag, KeySymbol symbol, StampPolicy policy, Optional<BlindCodeBook> book) {
        Marking marking = policy.markingFor(symbol);
        String text =
                switch (marking) {
                    case NOTHING -> "";
                    case TAG -> fits(tag, policy);
                    case BLIND -> fits(blindCode(tag, symbol, book), policy);
                    case SYMBOL -> fits(symbol.render(), policy);
                };
        return new Stamp(tag, symbol.render(), text, marking);
    }

    private static String blindCode(String tag, KeySymbol symbol, Optional<BlindCodeBook> book) {
        BlindCodeBook codes = book.orElseThrow(() -> new ConflictError(
                "a blind stamp needs the code book", Facts.of("tag", tag, "symbol", symbol.render())));
        return codes.assign(symbol);
    }

    private static String fits(String text, StampPolicy policy) {
        if (text.codePointCount(0, text.length()) > policy.bowCharacters()) {
            throw new ConflictError(
                    "that does not fit on the bow", Facts.of("text", text, "room", policy.bowCharacters()));
        }
        return text;
    }

    /** A batch for the stamping machine with no code book to hand, in tag order. */
    public static List<Stamp> stampRun(List<TaggedKey> wanted, StampPolicy policy) {
        return run(wanted, policy, Optional.empty());
    }

    /** A batch for the stamping machine, in tag order. A symbol gets one code however often it comes. */
    public static List<Stamp> stampRun(List<TaggedKey> wanted, StampPolicy policy, BlindCodeBook book) {
        return run(wanted, policy, Optional.of(book));
    }

    /** Stamped in the order given, so codes are handed out in that order, then sorted by tag. */
    private static List<Stamp> run(List<TaggedKey> wanted, StampPolicy policy, Optional<BlindCodeBook> book) {
        List<Stamp> found = new ArrayList<>();
        for (TaggedKey key : wanted) {
            found.add(stamp(key.tag(), key.symbol(), policy, book));
        }
        found.sort(Comparator.comparing(Stamp::tag));
        return List.copyOf(found);
    }

    /** The sheet that goes to the bench with the blanks. */
    public static List<String> stampSheet(List<Stamp> found, StampPolicy policy) {
        if (found.isEmpty()) {
            return List.of("nothing to stamp");
        }
        List<String> lines = new ArrayList<>();
        lines.add(found.size() + " keys to stamp");
        String trailer = policy.addDoNotDuplicate() ? " and " + DO_NOT_DUPLICATE : "";
        for (Stamp stamp : found) {
            String text = stamp.text().isEmpty() ? "(blank)" : stamp.text();
            lines.add("  " + Text.left(stamp.tag(), 10) + Text.left(text, 10) + trailer);
        }
        long blind = found.stream().filter(Stamp::isBlind).count();
        if (blind > 0) {
            lines.add(blind + " of them are blind, the symbols stay on paper");
        }
        return List.copyOf(lines);
    }

    /**
     * Somebody handed in a key with a number on it. This is the answer.
     *
     * @throws NotFoundError for a number no key was ever stamped with
     */
    public static String identify(String code, BlindCodeBook book) {
        return book.symbolFor(code);
    }

    /** Keys that carry nothing, which the shop cannot identify if handed one, in tag order. */
    public static List<String> unstamped(List<Stamp> found) {
        return found.stream()
                .filter(stamp -> stamp.text().isEmpty())
                .map(Stamp::tag)
                .sorted()
                .toList();
    }
}
