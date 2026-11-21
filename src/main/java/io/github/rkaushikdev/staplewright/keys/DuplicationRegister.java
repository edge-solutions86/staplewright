package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ceilings, the ones somebody raised, and what is over them.
 *
 * <p>This is arithmetic rather than policy. Every symbol has a ceiling, either the standing one or
 * a specific one somebody signed for, and the check is a count of what is out against that ceiling.
 */
public final class DuplicationRegister {

    private final DuplicationPolicy policy;
    private final Map<String, Authorisation> authorised = new HashMap<>();

    /** A register under the shop's standing ceilings. */
    public DuplicationRegister() {
        this(DuplicationPolicy.DEFAULT);
    }

    public DuplicationRegister(DuplicationPolicy policy) {
        this.policy = policy;
    }

    public DuplicationPolicy policy() {
        return policy;
    }

    /**
     * Raise the ceiling on one symbol. It cannot be lowered here.
     *
     * @throws ConflictError when the symbol already has a ceiling at least that high
     */
    public Authorisation authorise(Authorisation item) {
        Authorisation held = authorised.get(item.symbol());
        if (held != null && item.ceiling() <= held.ceiling()) {
            throw new ConflictError(
                    "that symbol already has a ceiling at least that high",
                    Facts.of("symbol", item.symbol(), "ceiling", held.ceiling()));
        }
        authorised.put(item.symbol(), item);
        return item;
    }

    /** The authorisation standing on one symbol, as the shop writes it. */
    public Optional<Authorisation> authorisation(String symbol) {
        return Optional.ofNullable(authorised.get(symbol));
    }

    /** The signed ceiling when there is one, the policy ceiling otherwise. */
    public int ceilingFor(KeySymbol symbol) {
        Authorisation held = authorised.get(symbol.render());
        if (held != null) {
            return held.ceiling();
        }
        return policy.ceilingFor(symbol);
    }

    public boolean isAuthorised(KeySymbol symbol) {
        return authorised.containsKey(symbol.render());
    }

    /** Every symbol with more keys out than it is allowed, worst first, then by symbol. */
    public List<Overrun> overruns(KeyLedger ledger) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (IssuedKey key : ledger.out()) {
            counts.merge(key.symbol(), 1, Integer::sum);
        }
        List<Overrun> found = new ArrayList<>();
        counts.forEach((rendered, count) -> {
            KeySymbol symbol = KeySymbol.parse(rendered);
            int ceiling = ceilingFor(symbol);
            if (count > ceiling) {
                found.add(new Overrun(rendered, count, ceiling, isAuthorised(symbol)));
            }
        });
        found.sort(Comparator.comparingInt((Overrun item) -> -item.overBy()).thenComparing(Overrun::symbol));
        return List.copyOf(found);
    }

    /** How many more of this key can go out before the ceiling, never less than nought. */
    public int roomFor(KeySymbol symbol, KeyLedger ledger) {
        int out = ledger.cutTo(symbol.render()).size();
        return Math.max(ceilingFor(symbol) - out, 0);
    }

    /** Whether one more of this key would go over the ceiling. */
    public boolean wouldExceed(KeySymbol symbol, KeyLedger ledger) {
        return roomFor(symbol, ledger) == 0;
    }

    /** Every authorisation standing, in symbol order. */
    public List<Authorisation> authorisations() {
        return authorised.keySet().stream().sorted().map(authorised::get).toList();
    }
}
