package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Literal;
import io.github.rkaushikdev.staplewright.util.Text;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The table that joins a number on a key to a symbol on paper.
 *
 * <p>A blind code is a number that means nothing to anybody without the shop's book, and everything
 * with it. It is not a hash and is not meant to be: it is a short number from a series, and its
 * security is entirely that this table is not public. Anything cleverer would be a cipher somebody
 * eventually recovers the key for, and would still be a number on a piece of brass.
 *
 * <p>This is the whole security model, so it is one object with one job and no convenience methods
 * that hand back the mapping in bulk beyond {@link #codes()}, which is for the safe. Anything that
 * wants the symbol behind a code asks for exactly one, which is the sort of thing a log can be read
 * against later.
 */
public final class BlindCodeBook implements Iterable<String> {

    /** Where a series starts unless the shop says otherwise. */
    public static final int START = 4000;

    /** How far apart two codes in a series sit unless the shop says otherwise. */
    public static final int STEP = 7;

    private final int start;
    private final int step;
    private long next;
    private final Registry<String> bySymbol = new Registry<>("symbol", "symbol");
    private final Map<String, String> byCode = new LinkedHashMap<>();

    /** A book whose series starts at 4000 and steps by seven. */
    public BlindCodeBook() {
        this(START, STEP);
    }

    /**
     * A book whose series starts and steps where it is told.
     *
     * @throws InputError for a start below one or a step below one
     */
    public BlindCodeBook(int start, int step) {
        if (start < 1) {
            throw new InputError("a blind series starts at one or later");
        }
        if (step < 1) {
            throw new InputError("a blind series steps forward");
        }
        this.start = start;
        this.step = step;
        this.next = start;
    }

    public int start() {
        return start;
    }

    public int step() {
        return step;
    }

    /** Give a symbol a code, or hand back the one it already has. */
    public String assign(KeySymbol symbol) {
        String rendered = symbol.render();
        Optional<String> held = bySymbol.find(rendered);
        if (held.isPresent()) {
            return held.get();
        }
        String code = String.valueOf(next);
        next += step;
        bySymbol.add(rendered, code);
        byCode.put(code, rendered);
        return code;
    }

    /**
     * The code a symbol was given.
     *
     * @throws NotFoundError for a symbol that was never given one
     */
    public String codeFor(KeySymbol symbol) {
        return bySymbol.find(symbol.render())
                .orElseThrow(
                        () -> new NotFoundError("that symbol has no blind code", Facts.of("symbol", symbol.render())));
    }

    /**
     * What a number on a key means, which is the reason this exists. White space round the number
     * is ignored.
     *
     * @throws NotFoundError for a number no key was ever stamped with
     */
    public String symbolFor(String code) {
        String found = byCode.get(Text.strip(code));
        if (found == null) {
            throw new NotFoundError("no key was ever stamped with that", Facts.of("code", code));
        }
        return found;
    }

    /**
     * Put a pair back as it was stored, refusing to reuse a code, and move the series past it.
     *
     * @throws ConflictError for a code already against another symbol
     * @throws IllegalArgumentException for a code that is not a whole number
     */
    public void load(String symbol, String code) {
        String held = byCode.get(code);
        if (held != null && !held.equals(symbol)) {
            throw new ConflictError("that code is already against another symbol", Facts.of("code", code));
        }
        long number = Text.wholeNumber(code)
                .orElseThrow(() -> new IllegalArgumentException("a blind code is a whole number: " + Literal.of(code)));
        bySymbol.put(symbol, code);
        byCode.put(code, symbol);
        next = Math.max(next, number + step);
    }

    /** Every code and the symbol it stands for, in symbol order. The one bulk method, for the safe. */
    public List<BlindCode> codes() {
        return byCode.entrySet().stream()
                .map(entry -> new BlindCode(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(BlindCode::symbol))
                .toList();
    }

    /** Whether any key was ever stamped with this code. */
    public boolean contains(String code) {
        return byCode.containsKey(code);
    }

    /** Every code in the book, in code order. */
    @Override
    public Iterator<String> iterator() {
        return byCode.keySet().stream().sorted().toList().iterator();
    }

    /** How many codes are in the book. */
    public int size() {
        return byCode.size();
    }
}
