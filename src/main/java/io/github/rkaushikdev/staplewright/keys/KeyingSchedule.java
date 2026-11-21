package io.github.rkaushikdev.staplewright.keys;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.HierarchyError;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.spec.CutViolation;
import io.github.rkaushikdev.staplewright.spec.KeywayFamily;
import io.github.rkaushikdev.staplewright.spec.RuleSet;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The keying schedule, which is the document the whole shop argues about: every symbol in one
 * system, what each one is cut to, and which keyway section each one is cut on.
 *
 * <p>It is a tree by construction because the symbols are a tree, so the schedule only has to
 * refuse the ways a person can break that tree by hand: hanging a key under a master nobody
 * created, cutting two different keys the same, or cutting a key on a section it will not enter.
 *
 * <p>Bittings are optional on purpose. A schedule gets drawn before it gets cut, and half the value
 * of the document is being able to say which symbols are still open.
 *
 * <p>The top key is created with the schedule because a system without one is not a system.
 * Everything else is added under something already present. Walking a schedule gives its lines in
 * schedule order.
 */
public final class KeyingSchedule implements Iterable<ScheduleEntry> {

    private final String name;
    private final KeywayFamily family;
    private final Map<String, ScheduleEntry> entries = new LinkedHashMap<>();
    private RuleSet ruleset;

    /**
     * A schedule holding only its top key, cut on {@code topSection}.
     *
     * @throws io.github.rkaushikdev.staplewright.KeywayError when the family has no such section
     */
    public KeyingSchedule(String name, RuleSet ruleset, KeywayFamily family, String topSection) {
        family.section(topSection);
        this.name = name;
        this.ruleset = Objects.requireNonNull(ruleset);
        this.family = family;
        entries.put(KeySymbol.TOP.render(), new ScheduleEntry(KeySymbol.TOP, topSection));
    }

    public String name() {
        return name;
    }

    /** The chart and the policy every cut on this schedule is checked against. */
    public RuleSet ruleset() {
        return ruleset;
    }

    /**
     * Put the schedule under a different chart or policy.
     *
     * <p>Policy changes after a system is drawn. Nothing already cut is checked here; {@link
     * #problems()} is where the shop finds out which keys in the ground the new rules refuse.
     */
    public void setRuleset(RuleSet ruleset) {
        this.ruleset = Objects.requireNonNull(ruleset);
    }

    public KeywayFamily family() {
        return family;
    }

    /** The top key, which every schedule holds. */
    public KeySymbol top() {
        return KeySymbol.TOP;
    }

    /** How many chambers every key on this schedule has. */
    public int chambers() {
        return ruleset.chart().chambers();
    }

    /** Put a symbol on the schedule under its parent, on the section the parent is cut on. */
    public ScheduleEntry add(KeySymbol symbol) {
        return add(symbol, Optional.empty(), "");
    }

    /** Put a symbol on the schedule under its parent, cut on {@code section}; blank inherits. */
    public ScheduleEntry add(KeySymbol symbol, String section) {
        return add(symbol, Optional.of(section), "");
    }

    /**
     * Put a symbol in the schedule under a parent that already exists.
     *
     * <p>Without a section, or with a blank one, the key is cut on the section above it.
     *
     * @throws ConflictError for a symbol already there, or a second top key
     * @throws HierarchyError when the parent is missing or would not enter the section
     * @throws io.github.rkaushikdev.staplewright.KeywayError for a section the family does not hold
     */
    public ScheduleEntry add(KeySymbol symbol, Optional<String> section, String note) {
        String key = symbol.render();
        if (entries.containsKey(key)) {
            throw new ConflictError("that symbol is already in the schedule", Facts.of("symbol", key));
        }
        Optional<KeySymbol> parent = symbol.parent();
        if (parent.isEmpty()) {
            throw new ConflictError("the schedule already has a top key", Facts.of("symbol", key));
        }
        ScheduleEntry parentEntry = entries.get(parent.get().render());
        if (parentEntry == null) {
            throw new HierarchyError(
                    "nothing to hang that under",
                    Facts.of("symbol", key, "wanted", parent.get().render()));
        }
        String chosen = section.filter(code -> !code.isEmpty()).orElse(parentEntry.section());
        family.section(chosen);
        if (!family.enters(parentEntry.section(), chosen)) {
            throw new HierarchyError(
                    "the key above would not enter that section",
                    Facts.of("symbol", key, "section", chosen, "above", parentEntry.section()));
        }
        ScheduleEntry entry = new ScheduleEntry(symbol, chosen, Optional.empty(), note);
        entries.put(key, entry);
        return entry;
    }

    /** The line for one symbol, refusing a symbol the schedule does not hold. */
    public ScheduleEntry entry(KeySymbol symbol) {
        ScheduleEntry found = entries.get(symbol.render());
        if (found == null) {
            throw new NotFoundError(
                    "no such symbol in this schedule", Facts.of("symbol", symbol.render(), "system", name));
        }
        return found;
    }

    /**
     * Cut a symbol, refusing anything the ruleset will not cut.
     *
     * @throws ConflictError for a bitting the rules refuse or another symbol already holds
     */
    public ScheduleEntry assign(KeySymbol symbol, Bitting bitting) {
        ScheduleEntry entry = entry(symbol);
        List<CutViolation> violations = ruleset.check(bitting);
        if (!violations.isEmpty()) {
            throw new ConflictError(
                    "that bitting is not one the shop cuts",
                    Facts.of(
                            "symbol",
                            symbol.render(),
                            "reasons",
                            violations.stream().map(CutViolation::rule).toList()));
        }
        Optional<KeySymbol> holder = symbolCutTo(bitting);
        if (holder.isPresent() && !holder.get().equals(symbol)) {
            throw new ConflictError(
                    "another key in this system is already cut that way",
                    Facts.of("symbol", symbol.render(), "held_by", holder.get().render(), "bitting", bitting.render()));
        }
        ScheduleEntry updated = entry.withBitting(bitting);
        entries.put(symbol.render(), updated);
        return updated;
    }

    /** Take the cuts off a symbol and leave it in the schedule. */
    public ScheduleEntry clear(KeySymbol symbol) {
        ScheduleEntry updated = entry(symbol).withoutBitting();
        entries.put(symbol.render(), updated);
        return updated;
    }

    /**
     * Put a line back exactly as it was stored, checking nothing.
     *
     * <p>A schedule coming off the database has already been through add and assign once, years
     * ago, under whatever policy was current then. Making it pass today's rules on the way in would
     * mean refusing to open half the systems on campus, so loading is deliberately dumb and the
     * review is where the shop finds out what is wrong.
     */
    public ScheduleEntry load(ScheduleEntry entry) {
        entries.put(entry.symbol().render(), entry);
        return entry;
    }

    /** What a symbol is cut to, empty while it is open. */
    public Optional<Bitting> bittingOf(KeySymbol symbol) {
        return entry(symbol).bitting();
    }

    /** The symbol cut to this bitting, if any symbol is. */
    public Optional<KeySymbol> symbolCutTo(Bitting bitting) {
        return entries.values().stream()
                .filter(entry -> entry.bitting().filter(bitting::equals).isPresent())
                .map(ScheduleEntry::symbol)
                .findFirst();
    }

    /** The keys directly under one, in schedule order. */
    public List<KeySymbol> children(KeySymbol symbol) {
        entry(symbol);
        Optional<KeySymbol> wanted = Optional.of(symbol);
        return sorted(entry -> entry.symbol().parent().equals(wanted));
    }

    /** Every key anywhere under one, in schedule order. */
    public List<KeySymbol> descendants(KeySymbol symbol) {
        entry(symbol);
        return sorted(entry -> !entry.symbol().equals(symbol) && entry.symbol().descendsFrom(symbol));
    }

    /** Every symbol, in schedule order. */
    public List<KeySymbol> symbols() {
        return sorted(entry -> true);
    }

    /** Every line, in schedule order. */
    public List<ScheduleEntry> entries() {
        return symbols().stream().map(this::entry).toList();
    }

    public List<KeySymbol> changeKeys() {
        return symbols().stream().filter(KeySymbol::isChangeKey).toList();
    }

    /** Every symbol that is not a change key, the top key included. */
    public List<KeySymbol> masters() {
        return symbols().stream().filter(symbol -> !symbol.isChangeKey()).toList();
    }

    /** Symbols nobody has cut yet, in schedule order. */
    public List<KeySymbol> openSymbols() {
        return entries().stream()
                .filter(entry -> !entry.isCut())
                .map(ScheduleEntry::symbol)
                .toList();
    }

    public List<KeySymbol> cutSymbols() {
        return entries().stream()
                .filter(ScheduleEntry::isCut)
                .map(ScheduleEntry::symbol)
                .toList();
    }

    /**
     * Anything cut into the schedule that the ruleset now refuses, each message led by the symbol.
     *
     * <p>Policy changes after a system is drawn, and the shop wants to know which of the keys already
     * in the ground would not be cut today.
     */
    public List<CutViolation> problems() {
        List<CutViolation> found = new ArrayList<>();
        for (ScheduleEntry entry : entries()) {
            if (entry.bitting().isEmpty()) {
                continue;
            }
            for (CutViolation violation : ruleset.check(entry.bitting().get())) {
                found.add(new CutViolation(
                        violation.rule(),
                        entry.symbol().render() + ": " + violation.message(),
                        violation.chamber(),
                        violation.facts()));
            }
        }
        return List.copyOf(found);
    }

    /** Whether this is a symbol the schedule holds. Anything that is not a symbol is not held. */
    public boolean contains(Object symbol) {
        return symbol instanceof KeySymbol key && entries.containsKey(key.render());
    }

    /** Every line in schedule order, over a copy so the schedule can change while it is walked. */
    @Override
    public Iterator<ScheduleEntry> iterator() {
        return entries().iterator();
    }

    /** How many symbols the schedule holds, open or cut. */
    public int size() {
        return entries.size();
    }

    private List<KeySymbol> sorted(Predicate<ScheduleEntry> keep) {
        return KeySymbol.sortSymbols(entries.values().stream()
                .filter(keep)
                .map(ScheduleEntry::symbol)
                .toList());
    }
}
