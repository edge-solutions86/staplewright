package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.control.IssuedKey;
import io.github.rkaushikdev.staplewright.control.KeyLedger;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.progression.KeyingGroup;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * What one key opens, which is the question every incident starts with.
 *
 * <p>Three records have to line up to answer it. A cylinder sits in an opening. A cylinder belongs
 * to a keying group. A keying group lists the symbols that operate it. Follow those and a symbol
 * turns into a list of doors.
 *
 * <p>The reverse direction is the one that gets asked at eight in the morning. A tag number comes
 * off a key somebody lost, the ledger says what it was cut to, and the shop needs every opening that
 * key turns before it can decide how much of the campus to rekey.
 *
 * <p>Nothing here is derived from the hierarchy. A grand master opens a door because the door's
 * group says so, not because the symbol sits high up, and the difference matters: the whole point
 * of an incident review is finding the cylinder where the two disagree.
 */
public final class CoverageMap {

    /** How many keys the widest list names unless told otherwise. */
    public static final int WIDEST_LIMIT = 5;

    private static final Comparator<KeySymbol> SHALLOW_FIRST =
            Comparator.comparingInt(KeySymbol::level).thenComparing(KeySymbol::render);

    private static final Comparator<Cylinder> BY_SERIAL = Comparator.comparing(Cylinder::serial);

    private final HardwareRegister register;
    private final Map<String, KeyingGroup> groups;

    /** A map over these cylinders, reading their groups from a copy of {@code groups}. */
    public CoverageMap(HardwareRegister register, Map<String, KeyingGroup> groups) {
        this.register = register;
        this.groups = Collections.unmodifiableMap(new LinkedHashMap<>(groups));
    }

    /** The cylinders the map reads. */
    public HardwareRegister register() {
        return register;
    }

    /** Every keying group by name, read only. */
    public Map<String, KeyingGroup> groups() {
        return groups;
    }

    /**
     * The keying group by this name.
     *
     * @throws NotFoundError when it was never defined
     */
    public KeyingGroup group(String name) {
        KeyingGroup found = groups.get(name);
        if (found == null) {
            throw new NotFoundError("no such keying group", Facts.of("group", name));
        }
        return found;
    }

    /** The group a cylinder is pinned to, when it names one the map knows. */
    public Optional<KeyingGroup> groupOf(Cylinder cylinder) {
        if (cylinder.group().isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(cylinder.group()));
    }

    /** Every symbol that turns anything on one opening, shallow first and then as written. */
    public List<KeySymbol> symbolsOpening(String openingNumber) {
        Set<KeySymbol> found = new HashSet<>();
        for (Cylinder cylinder : register.onOpening(openingNumber)) {
            groupOf(cylinder).ifPresent(group -> found.addAll(group.symbols()));
        }
        return found.stream().sorted(SHALLOW_FIRST).toList();
    }

    /** Every opening a key cut to this symbol turns, in number order. */
    public List<String> openingsFor(KeySymbol symbol) {
        Set<String> found = new TreeSet<>();
        for (Cylinder cylinder : register) {
            if (cylinder.isInstalled()
                    && groupOf(cylinder).filter(group -> group.holds(symbol)).isPresent()) {
                found.add(cylinder.installedAt().orElse(""));
            }
        }
        return List.copyOf(found);
    }

    /** Every opening any of these keys turns, each once, in number order. */
    public List<String> openingsForAny(Iterable<KeySymbol> symbols) {
        Set<String> found = new TreeSet<>();
        for (KeySymbol symbol : symbols) {
            found.addAll(openingsFor(symbol));
        }
        return List.copyOf(found);
    }

    /** The buildings a key reaches, in code order. */
    public List<String> buildingsFor(KeySymbol symbol) {
        return openingsFor(symbol).stream()
                .map(number -> register.openings().opening(number).building())
                .distinct()
                .sorted()
                .toList();
    }

    /**
     * The cylinders that would have to come apart to lock a symbol out, in serial order, shelved
     * cylinders pinned to a group that lists it included.
     */
    public List<Cylinder> cylindersFor(KeySymbol symbol) {
        return stream()
                .filter(cylinder ->
                        groupOf(cylinder).filter(group -> group.holds(symbol)).isPresent())
                .sorted(BY_SERIAL)
                .toList();
    }

    /** Installed cylinders whose group is missing or unknown, in serial order. */
    public List<Cylinder> unmappedCylinders() {
        return stream()
                .filter(cylinder -> cylinder.isInstalled() && groupOf(cylinder).isEmpty())
                .sorted(BY_SERIAL)
                .toList();
    }

    /** Keyed openings with nothing in the lockset, which open to nobody, in number order. */
    public List<OpeningPosition> uncoveredPositions() {
        return register.uncoveredOpenings().stream()
                .map(opening -> new OpeningPosition(opening.number(), Position.LOCKSET))
                .toList();
    }

    /** How many doors a key turns. */
    public int reachOf(KeySymbol symbol) {
        return openingsFor(symbol).size();
    }

    /** The five keys that turn the most installed cylinders, which are the ones to watch. */
    public List<SymbolReach> widestSymbols() {
        return widestSymbols(WIDEST_LIMIT);
    }

    /** The keys that turn the most installed cylinders, the most first and a tie as written. */
    public List<SymbolReach> widestSymbols(int limit) {
        Map<KeySymbol, Integer> counts = new LinkedHashMap<>();
        for (Cylinder cylinder : register) {
            if (!cylinder.isInstalled()) {
                continue;
            }
            groupOf(cylinder)
                    .ifPresent(group -> group.symbols().forEach(symbol -> counts.merge(symbol, 1, Integer::sum)));
        }
        return counts.entrySet().stream()
                .map(entry -> new SymbolReach(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(SymbolReach::cylinders, Comparator.reverseOrder())
                        .thenComparing(reach -> reach.symbol().render()))
                .limit(limit)
                .toList();
    }

    /**
     * What one stamped key opens, read through the ledger.
     *
     * @throws NotFoundError when the tag was never issued
     */
    public static List<String> openingsATagTurns(CoverageMap coverage, KeyLedger ledger, String tag) {
        IssuedKey key = ledger.key(tag);
        return coverage.openingsFor(KeySymbol.parse(key.symbol()));
    }

    /** Every key out that is cut to one of these symbols, in tag order. */
    public static List<IssuedKey> keysThatOpen(KeyLedger ledger, Iterable<KeySymbol> symbols) {
        Set<String> wanted = new HashSet<>();
        symbols.forEach(symbol -> wanted.add(symbol.render()));
        return ledger.out().stream()
                .filter(key -> wanted.contains(key.symbol()))
                .sorted(Comparator.comparing(IssuedKey::tag))
                .toList();
    }

    /** Everybody currently holding a key that turns one door, in identifier order. */
    public static List<String> peopleWhoCanOpen(CoverageMap coverage, KeyLedger ledger, String openingNumber) {
        List<KeySymbol> symbols = coverage.symbolsOpening(openingNumber);
        return keysThatOpen(ledger, symbols).stream()
                .map(IssuedKey::keyholder)
                .distinct()
                .sorted()
                .toList();
    }

    private Stream<Cylinder> stream() {
        return StreamSupport.stream(register.spliterator(), false);
    }
}
