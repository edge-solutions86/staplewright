package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.HierarchyError;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One name out of the keying schedule, held so it can be walked.
 *
 * <p>The notation is old and it is compact. GGM is the one key at the top. A single letter is a
 * grand master, two letters a master, three a sub master, and a letter group followed by a number
 * is a change key, the key an actual person carries. So AB3 is the third change key under master
 * AB, which itself sits under grand master A, which sits under GGM. Depth in the notation is depth
 * in the hierarchy, and that is the only reason the notation survives: reading a symbol tells you
 * what a key ought to open before anybody looks up a single cylinder.
 *
 * @param letters the empty string for the top key, one character longer at each master level
 * @param number set only on a change key, which is always a leaf
 */
public record KeySymbol(String letters, Optional<Integer> number) implements Comparable<KeySymbol> {

    /** The one key at the top of a system. Written this way on every chart. */
    public static final String TOP_SYMBOL = "GGM";

    /** The top key. */
    public static final KeySymbol TOP = new KeySymbol("");

    private static final Pattern PATTERN = Pattern.compile("([A-Z]{1,4})([0-9]{1,4})?");

    private static final Map<Integer, String> LEVEL_NAMES = Map.of(
            0, "great grand master",
            1, "grand master",
            2, "master",
            3, "sub master",
            4, "sub sub master");

    /** Schedule order: shallow before deep, then letters, then number. */
    public static final Comparator<KeySymbol> SCHEDULE_ORDER = Comparator.comparingInt(KeySymbol::level)
            .thenComparing(KeySymbol::letters)
            .thenComparingInt(symbol -> symbol.number().orElse(0));

    public KeySymbol {
        if (!letters.isEmpty() && !Text.isAlpha(letters)) {
            throw new HierarchyError("a symbol group is letters", Facts.of("letters", letters));
        }
        if (!letters.equals(Text.upper(letters))) {
            throw new HierarchyError("symbols are written in capitals", Facts.of("letters", letters));
        }
        if (number.isPresent()) {
            if (number.get() < 1) {
                throw new HierarchyError("change keys are numbered from one", Facts.of("number", number.get()));
            }
            if (letters.isEmpty()) {
                throw new HierarchyError("a numbered key hangs under a master", Facts.of("number", number.get()));
            }
        }
    }

    /** A master, or the top key for no letters at all. */
    public KeySymbol(String letters) {
        this(letters, Optional.empty());
    }

    /** A change key under the master these letters name. */
    public KeySymbol(String letters, int number) {
        this(letters, Optional.of(number));
    }

    /** Read a symbol as the shop writes it, tidying case and white space. */
    public static KeySymbol parse(String text) {
        String cleaned = Text.upper(Text.strip(text));
        if (cleaned.isEmpty()) {
            throw new HierarchyError("a symbol cannot be blank");
        }
        if (cleaned.equals(TOP_SYMBOL)) {
            return TOP;
        }
        Matcher match = PATTERN.matcher(cleaned);
        if (!match.matches()) {
            throw new HierarchyError("that is not a key symbol", Facts.of("symbol", text));
        }
        String digits = match.group(2);
        return digits == null ? new KeySymbol(match.group(1)) : new KeySymbol(match.group(1), Integer.parseInt(digits));
    }

    public boolean isTop() {
        return letters.isEmpty() && number.isEmpty();
    }

    public boolean isChangeKey() {
        return number.isPresent();
    }

    /** Distance below the top key. The top key is nought. */
    public int level() {
        return letters.length() + (number.isPresent() ? 1 : 0);
    }

    /** What the shop calls a key at this level. */
    public String levelName() {
        if (isChangeKey()) {
            return "change key";
        }
        return LEVEL_NAMES.getOrDefault(level(), "level " + level() + " master");
    }

    /** The key directly above this one, or nothing at the top. */
    public Optional<KeySymbol> parent() {
        if (isTop()) {
            return Optional.empty();
        }
        if (number.isPresent()) {
            return Optional.of(new KeySymbol(letters));
        }
        return Optional.of(new KeySymbol(letters.substring(0, letters.length() - 1)));
    }

    /** This symbol, then everything above it, ending at the top key. */
    public List<KeySymbol> ancestry() {
        List<KeySymbol> line = new ArrayList<>();
        Optional<KeySymbol> walk = Optional.of(this);
        while (walk.isPresent()) {
            line.add(walk.get());
            walk = walk.get().parent();
        }
        return List.copyOf(line);
    }

    /** Whether this key sits somewhere under the other one, or is it. */
    public boolean descendsFrom(KeySymbol other) {
        return ancestry().contains(other);
    }

    /** The master one level down, named by adding a letter. */
    public KeySymbol childMaster(String letter) {
        if (isChangeKey()) {
            throw new HierarchyError("a change key has nothing under it", Facts.of("symbol", render()));
        }
        if (letter.length() != 1 || !Text.isAlpha(letter)) {
            throw new HierarchyError("a master is named by one letter", Facts.of("letter", letter));
        }
        return new KeySymbol(letters + Text.upper(letter));
    }

    /** A numbered key directly under this master. */
    public KeySymbol changeKey(int number) {
        if (isChangeKey()) {
            throw new HierarchyError("a change key has nothing under it", Facts.of("symbol", render()));
        }
        if (isTop()) {
            throw new HierarchyError("change keys do not hang off the top key");
        }
        return new KeySymbol(letters, number);
    }

    /** The symbol as the shop writes it. */
    public String render() {
        if (isTop()) {
            return TOP_SYMBOL;
        }
        return number.map(value -> letters + value).orElse(letters);
    }

    @Override
    public String toString() {
        return render();
    }

    /** Letters first, then number, a master before the change keys under it. */
    @Override
    public int compareTo(KeySymbol other) {
        int compared = letters.compareTo(other.letters);
        if (compared != 0) {
            return compared;
        }
        return Integer.compare(number.orElse(0), other.number.orElse(0));
    }

    /** Schedule order: shallow before deep, then letters, then number. */
    public static List<KeySymbol> sortSymbols(Collection<KeySymbol> symbols) {
        return symbols.stream().sorted(SCHEDULE_ORDER).toList();
    }

    /** The lowest key that sits above both. Always exists, at worst the top. */
    public static KeySymbol commonAncestor(KeySymbol first, KeySymbol second) {
        Set<KeySymbol> above = new HashSet<>(second.ancestry());
        for (KeySymbol symbol : first.ancestry()) {
            if (above.contains(symbol)) {
                return symbol;
            }
        }
        throw new HierarchyError(
                "two symbols with no key above them", Facts.of("first", first.render(), "second", second.render()));
    }
}
