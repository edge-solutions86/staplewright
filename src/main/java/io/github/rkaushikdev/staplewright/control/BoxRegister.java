package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.InputError;
import io.github.rkaushikdev.staplewright.Lifecycle;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import io.github.rkaushikdev.staplewright.util.Tallies;
import io.github.rkaushikdev.staplewright.util.Text;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every sealed key box on the campus, the one place the shop hands over control.
 *
 * <p>A broken seal is not an incident on its own. Firefighters break them, and that is the point of
 * the box. What matters is that the key inside is out of anybody's control from that moment, so the
 * box goes into a state that is not fine and not compromised either, and stays there until somebody
 * puts a new key in and a new seal on.
 *
 * <p>The rule that catches people out is the one about what is in the box. A box holding a building
 * master is a building master hanging on a wall outdoors. The register's {@link BoxPolicy} says
 * which symbol levels may sit in a box, and the register refuses the rest, because the alternative
 * is finding out from a fire report.
 */
public final class BoxRegister implements Iterable<KeyBox> {

    private static final String TOO_WIDE = "that symbol is too wide to leave in a box";

    private final BoxPolicy policy;
    private final Registry<KeyBox> boxes = new Registry<>("key box", "number");

    /** A register on the shop's standing box policy. */
    public BoxRegister() {
        this(new BoxPolicy());
    }

    public BoxRegister(BoxPolicy policy) {
        this.policy = policy;
    }

    /** What this register lets into a box, and how often it wants one looked at. */
    public BoxPolicy policy() {
        return policy;
    }

    /** Put a box up, refusing a key nobody should leave on a wall. A box goes up sealed. */
    public KeyBox install(KeyBox box) {
        if (!policy.allows(box.symbol())) {
            throw new ConflictError(
                    TOO_WIDE,
                    Facts.of(
                            "box",
                            box.number(),
                            "symbol",
                            box.symbol().render(),
                            "level",
                            box.symbol().level()));
        }
        if (box.state() != BoxState.SEALED) {
            throw new ConflictError("a box goes up sealed", Facts.of("box", box.number()));
        }
        return boxes.add(box.number(), box);
    }

    /** Put a box back as it was stored, in whatever state it reached. */
    public KeyBox load(KeyBox box) {
        return boxes.put(box.number(), box);
    }

    /**
     * The box with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody installed it
     */
    public KeyBox box(String number) {
        return boxes.get(number);
    }

    /**
     * Walk past and read the seal. A seal that does not match is an opening.
     *
     * <p>This is the method that does the work. Nobody reports opening a box, so the only way one is
     * ever found open is somebody reading the number off the tag and it not being the number on
     * file. White space round the number read is ignored.
     */
    public KeyBox check(String number, String seal, LocalDate when) {
        KeyBox box = box(number);
        Lifecycle.requireState(
                box.state(), List.of(BoxState.SEALED), "only a sealed box gets its seal read", Facts.of("box", number));
        String read = Text.strip(seal);
        if (read.equals(box.seal())) {
            return store(box.toBuilder().checkedOn(when).build());
        }
        return store(box.toBuilder()
                .state(BoxState.OPENED)
                .checkedOn(when)
                .openedOn(when)
                .openedBy(OpenedBy.UNKNOWN)
                .note("seal read " + read + ", expected " + box.seal())
                .build());
    }

    /** Somebody said they opened it, which is better than finding out. */
    public KeyBox reportOpened(String number, OpenedBy by, LocalDate when) {
        return reportOpened(number, by, when, "");
    }

    public KeyBox reportOpened(String number, OpenedBy by, LocalDate when, String note) {
        KeyBox box = box(number);
        Lifecycle.requireState(
                box.state(), List.of(BoxState.SEALED), "that box was already open", Facts.of("box", number));
        return store(box.toBuilder()
                .state(BoxState.OPENED)
                .openedOn(when)
                .openedBy(by)
                .note(note)
                .build());
    }

    /** The key is gone. The box is still on the wall and still a hole. */
    public KeyBox empty(String number, LocalDate when) {
        return empty(number, when, "");
    }

    public KeyBox empty(String number, LocalDate when, String note) {
        KeyBox box = box(number);
        Lifecycle.requireState(
                box.state(),
                List.of(BoxState.OPENED),
                "a key only goes missing out of an open box",
                Facts.of("box", number));
        return store(
                box.toBuilder().state(BoxState.EMPTY).checkedOn(when).note(note).build());
    }

    /**
     * A new key and a new seal, which is the only way back to sealed.
     *
     * @throws ConflictError when the box is already sealed, the seal number is the old one, or the
     *     new key is too wide for a box
     * @throws InputError when no seal number is given
     */
    public KeyBox reseal(String number, String seal, KeySymbol symbol, LocalDate when) {
        KeyBox box = box(number);
        Lifecycle.requireState(
                box.state(),
                List.of(BoxState.OPENED, BoxState.EMPTY),
                "that box is already sealed",
                Facts.of("box", number));
        String fresh = Text.strip(seal);
        if (fresh.isEmpty()) {
            throw new InputError("a reseal needs a seal number", Facts.of("box", number));
        }
        if (fresh.equals(box.seal())) {
            throw new ConflictError("a reseal uses a new seal number", Facts.of("box", number, "seal", fresh));
        }
        if (!policy.allows(symbol)) {
            throw new ConflictError(TOO_WIDE, Facts.of("box", number, "symbol", symbol.render()));
        }
        return store(box.toBuilder()
                .state(BoxState.SEALED)
                .seal(fresh)
                .symbol(symbol)
                .sealedOn(when)
                .checkedOn(when)
                .openedOn(Optional.empty())
                .openedBy(Optional.empty())
                .note("")
                .build());
    }

    /** Take the box off the wall for good. The note says why. */
    public KeyBox withdraw(String number, LocalDate when, String note) {
        KeyBox box = box(number);
        if (box.state() == BoxState.WITHDRAWN) {
            throw new ConflictError("that box is already off the wall", Facts.of("box", number));
        }
        if (Text.isBlank(note)) {
            throw new InputError("taking a box down says why", Facts.of("box", number));
        }
        return store(box.toBuilder()
                .state(BoxState.WITHDRAWN)
                .checkedOn(when)
                .note(note)
                .build());
    }

    private KeyBox store(KeyBox box) {
        return boxes.put(box.number(), box);
    }

    /** The boxes in one state, in the order they went up. */
    public List<KeyBox> inState(BoxState state) {
        return boxes.where(box -> box.state() == state);
    }

    /** The boxes on one building, in the order they went up. */
    public List<KeyBox> inBuilding(String building) {
        return boxes.where(box -> box.building().equals(building));
    }

    /** The boxes with a key cut to this symbol still inside. */
    public List<KeyBox> holding(KeySymbol symbol) {
        return boxes.where(box -> box.symbol().equals(symbol) && box.holdsAKey());
    }

    /** Everything not sealed and still on a wall, worst first: the empty ones, then by number. */
    public List<KeyBox> openBoxes() {
        return boxes.where(box -> box.isLive() && box.state() != BoxState.SEALED).stream()
                .sorted(Comparator.comparing((KeyBox box) -> box.state() != BoxState.EMPTY)
                        .thenComparing(KeyBox::number))
                .toList();
    }

    /** Boxes a full check window has passed without anybody looking, the longest unseen first. */
    public List<KeyBox> dueForCheck(LocalDate today) {
        return boxes.where(box -> box.dueForCheck(today, policy)).stream()
                .sorted(Comparator.comparing(KeyBox::lastSeen).thenComparing(KeyBox::number))
                .toList();
    }

    /** Boxes open longer than the reseal window, the longest open first. */
    public List<KeyBox> overdueReseal(LocalDate today) {
        return boxes.where(box -> box.overdueReseal(today, policy)).stream()
                .sorted(Comparator.comparing((KeyBox box) -> box.openedOn().orElse(today))
                        .thenComparing(KeyBox::number))
                .toList();
    }

    /** How many boxes are standing open per building, most first. */
    public Map<String, Integer> exposure() {
        return Tallies.count(openBoxes(), KeyBox::building);
    }

    /** Symbols sitting in a box that is not sealed, which is loose brass, each once, as written. */
    public List<KeySymbol> symbolsAtRisk() {
        return openBoxes().stream()
                .filter(KeyBox::holdsAKey)
                .map(KeyBox::symbol)
                .distinct()
                .sorted(Comparator.comparing(KeySymbol::render))
                .toList();
    }

    /** Whether a box with this number is in the register. */
    public boolean contains(String number) {
        return boxes.has(number);
    }

    /** Every box, in the order it went up. */
    @Override
    public Iterator<KeyBox> iterator() {
        return boxes.iterator();
    }

    public int size() {
        return boxes.size();
    }

    /**
     * Everything a walk round should deal with, in the order to deal with it: the open boxes first,
     * then the ones due a check, no box named twice.
     */
    public static List<KeyBox> boxesNeedingWork(BoxRegister register, LocalDate today) {
        List<KeyBox> wanted = new ArrayList<>(register.openBoxes());
        for (KeyBox box : register.dueForCheck(today)) {
            if (!wanted.contains(box)) {
                wanted.add(box);
            }
        }
        return List.copyOf(wanted);
    }

    /** Buildings with no live box on them, which is the fire brigade's problem, in building order. */
    public static List<String> coverage(BoxRegister register, Collection<String> buildings) {
        Set<String> held = register.boxes.where(KeyBox::isLive).stream()
                .map(KeyBox::building)
                .collect(Collectors.toSet());
        return buildings.stream().filter(code -> !held.contains(code)).sorted().toList();
    }
}
