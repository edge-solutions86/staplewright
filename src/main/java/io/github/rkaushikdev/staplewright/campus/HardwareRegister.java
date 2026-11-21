package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.Registry;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every cylinder the shop owns, and where each one is at the moment.
 *
 * <p>Openings are permanent and cylinders move, so the register is keyed on the cylinder and
 * points at the opening rather than the other way round. What is not allowed is two cylinders in
 * one position, and a cylinder in two places at once, which is the pair of mistakes a paper
 * register makes every year. The register walks its cylinders in serial order.
 */
public final class HardwareRegister implements Iterable<Cylinder> {

    private static final Comparator<Cylinder> BY_SERIAL = Comparator.comparing(Cylinder::serial);

    private final OpeningSchedule openings;
    private final Registry<Cylinder> cylinders = new Registry<>("cylinder", "serial");

    public HardwareRegister(OpeningSchedule openings) {
        this.openings = openings;
    }

    /** The openings the cylinders go into. */
    public OpeningSchedule openings() {
        return openings;
    }

    /**
     * Put a new cylinder on the register. It joins on the shelf and gets installed after.
     *
     * @throws ConflictError when the cylinder claims to be installed already, or the serial is taken
     */
    public Cylinder add(Cylinder cylinder) {
        if (cylinder.isInstalled()) {
            throw new ConflictError(
                    "a cylinder joins the register on the shelf and gets installed after",
                    Facts.of("serial", cylinder.serial()));
        }
        return cylinders.add(cylinder.serial(), cylinder);
    }

    /**
     * The cylinder with this serial.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is none
     */
    public Cylinder cylinder(String serial) {
        return cylinders.get(serial);
    }

    /**
     * Put a cylinder in a door, refusing anything already occupied.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError for an unknown cylinder or opening
     * @throws ConflictError when the cylinder is already in a door, the opening takes no cylinder,
     *     or something already sits in that position, naming it
     */
    public Cylinder install(String serial, String openingNumber, Position position) {
        Cylinder cylinder = cylinder(serial);
        Opening opening = openings.opening(openingNumber);
        if (cylinder.isInstalled()) {
            throw new ConflictError(
                    "that cylinder is already in a door",
                    Facts.of(
                            "serial",
                            serial,
                            "installed_at",
                            cylinder.installedAt().get()));
        }
        if (!opening.isKeyed()) {
            throw new ConflictError(
                    "that opening does not take a cylinder",
                    Facts.of(
                            "opening",
                            openingNumber,
                            "function",
                            opening.function().value()));
        }
        Optional<Cylinder> sitting = at(openingNumber, position);
        if (sitting.isPresent()) {
            throw new ConflictError(
                    "something is already in that position",
                    Facts.of(
                            "opening",
                            openingNumber,
                            "position",
                            position.value(),
                            "serial",
                            sitting.get().serial()));
        }
        Cylinder updated = cylinder.toBuilder()
                .installedAt(openingNumber)
                .position(position)
                .build();
        return cylinders.put(serial, updated);
    }

    /**
     * Take a cylinder out and put it back on the shelf.
     *
     * @throws ConflictError when it is already on the shelf
     */
    public Cylinder pull(String serial) {
        Cylinder cylinder = cylinder(serial);
        if (!cylinder.isInstalled()) {
            throw new ConflictError("that cylinder is already on the shelf", Facts.of("serial", serial));
        }
        Cylinder updated = cylinder.toBuilder()
                .installedAt(Optional.empty())
                .position(Optional.empty())
                .build();
        return cylinders.put(serial, updated);
    }

    /** Pull and install in one step, which is what a swap really is. */
    public Cylinder move(String serial, String openingNumber, Position position) {
        pull(serial);
        return install(serial, openingNumber, position);
    }

    /** Write the keying group a cylinder is pinned to. */
    public Cylinder assignGroup(String serial, String group) {
        Cylinder updated = cylinder(serial).toBuilder().group(group).build();
        return cylinders.put(serial, updated);
    }

    /** Whatever sits in one position on one opening. */
    public Optional<Cylinder> at(String openingNumber, Position position) {
        return cylinders.first(cylinder -> cylinder.installedAt().equals(Optional.of(openingNumber))
                && cylinder.position().equals(Optional.of(position)));
    }

    /**
     * The cylinders in one opening, by position as written and then by serial.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError for an unknown opening
     */
    public List<Cylinder> onOpening(String openingNumber) {
        openings.opening(openingNumber);
        return cylinders.where(cylinder -> cylinder.installedAt().equals(Optional.of(openingNumber))).stream()
                .sorted(Comparator.comparing(HardwareRegister::positionText).thenComparing(Cylinder::serial))
                .toList();
    }

    /** The cylinders installed in one building, by opening number and then by position as written. */
    public List<Cylinder> inBuilding(String code) {
        Set<String> numbers =
                openings.inBuilding(code).stream().map(Opening::number).collect(Collectors.toSet());
        return cylinders
                .where(cylinder ->
                        cylinder.installedAt().filter(numbers::contains).isPresent())
                .stream()
                .sorted(Comparator.comparing(
                                (Cylinder cylinder) -> cylinder.installedAt().orElse(""))
                        .thenComparing(HardwareRegister::positionText))
                .toList();
    }

    /** Every cylinder not in a door, in serial order. */
    public List<Cylinder> onTheShelf() {
        return bySerial(cylinders.where(cylinder -> !cylinder.isInstalled()));
    }

    /** Every cylinder pinned to one keying group, in serial order. */
    public List<Cylinder> inGroup(String group) {
        return bySerial(cylinders.where(cylinder -> cylinder.group().equals(group)));
    }

    /** Installed cylinders nobody has said what they open, in serial order. */
    public List<Cylinder> unkeyed() {
        return bySerial(cylinders.where(
                cylinder -> cylinder.isInstalled() && cylinder.group().isEmpty()));
    }

    /** Keyed openings with no cylinder in the lockset position, in number order. */
    public List<Opening> uncoveredOpenings() {
        return openings.keyedOpenings().stream()
                .filter(opening -> at(opening.number(), Position.LOCKSET).isEmpty())
                .toList();
    }

    /** Whether a cylinder with this serial is on the register. */
    public boolean contains(Object serial) {
        return cylinders.contains(serial);
    }

    /** Every cylinder, in serial order. */
    @Override
    public Iterator<Cylinder> iterator() {
        return cylinders.byKey().iterator();
    }

    /** How many cylinders the shop owns. */
    public int size() {
        return cylinders.size();
    }

    private static List<Cylinder> bySerial(List<Cylinder> found) {
        return found.stream().sorted(BY_SERIAL).toList();
    }

    /** The position as it is written, which is the order a door's cylinders are listed in. */
    private static String positionText(Cylinder cylinder) {
        return cylinder.position().map(Position::value).orElse("");
    }
}
