package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.Registry;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Every opening on campus, indexed the two ways anybody asks for them.
 *
 * <p>By number, because that is what a work order carries, and by building, because that is how a
 * survey gets walked. A building is walked floor by floor, then room by room. Everything else comes
 * back in opening number order, and the schedule walks its openings that way too.
 */
public final class OpeningSchedule implements Iterable<Opening> {

    private static final Comparator<Opening> WALKING_ORDER =
            Comparator.comparing(Opening::floor).thenComparing(Opening::room).thenComparing(Opening::number);

    private static final Comparator<Opening> BY_NUMBER = Comparator.comparing(Opening::number);

    private final Registry<Building> byCode = new Registry<>("building", "code");
    private final Registry<Opening> byNumber = new Registry<>("opening", "number");

    /**
     * Put a building on the list.
     *
     * @throws ConflictError when a building with that code is already on it
     */
    public Building addBuilding(Building building) {
        if (byCode.has(building.code())) {
            throw new ConflictError("that building is already on the list", Facts.of("code", building.code()));
        }
        return byCode.add(building.code(), building);
    }

    /**
     * The building with this code.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody added it
     */
    public Building building(String code) {
        return byCode.get(code);
    }

    /**
     * Put an opening on the schedule, in a building already on the list.
     *
     * @throws ConflictError when the opening number is taken
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when its building was never added
     */
    public Opening add(Opening opening) {
        if (byNumber.has(opening.number())) {
            throw new ConflictError("that opening number is taken", Facts.of("number", opening.number()));
        }
        building(opening.building());
        return byNumber.add(opening.number(), opening);
    }

    /**
     * The opening with this number.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is none
     */
    public Opening opening(String number) {
        return byNumber.get(number);
    }

    /**
     * A door that got bricked up. It happens twice a decade.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when there is no such opening
     */
    public Opening remove(String number) {
        return byNumber.remove(number);
    }

    /** Every opening in one building, in walking order: floor, then room, then number. */
    public List<Opening> inBuilding(String code) {
        building(code);
        return byNumber.where(opening -> opening.building().equals(code)).stream()
                .sorted(WALKING_ORDER)
                .toList();
    }

    /** One floor of a building, in walking order. */
    public List<Opening> onFloor(String code, String floor) {
        return inBuilding(code).stream()
                .filter(opening -> opening.floor().equals(floor))
                .toList();
    }

    /** Every opening with one function, in number order. */
    public List<Opening> withFunction(DoorFunction function) {
        return numbered(opening -> opening.function() == function);
    }

    /** Every opening that takes a cylinder, in number order. */
    public List<Opening> keyedOpenings() {
        return numbered(Opening::isKeyed);
    }

    /** Every opening facing out of its building, in number order. */
    public List<Opening> exteriorOpenings() {
        return numbered(Opening::exterior);
    }

    /** Every building, in code order. */
    public List<Building> buildings() {
        return byCode.byKey();
    }

    /** The floors of one building, each once, in order. */
    public List<String> floors(String code) {
        return inBuilding(code).stream().map(Opening::floor).distinct().sorted().toList();
    }

    /** How many openings each building holds, in code order, a building with none counting nought. */
    public Map<String, Integer> countByBuilding() {
        Map<String, Integer> counts = new TreeMap<>();
        for (String code : byCode.keys()) {
            counts.put(code, 0);
        }
        for (Opening opening : byNumber) {
            counts.merge(opening.building(), 1, Integer::sum);
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(counts));
    }

    /** Whether an opening with this number is on the schedule. */
    public boolean contains(Object number) {
        return byNumber.contains(number);
    }

    /** Every opening, in number order. */
    @Override
    public Iterator<Opening> iterator() {
        return byNumber.byKey().iterator();
    }

    /** How many openings are on the schedule. */
    public int size() {
        return byNumber.size();
    }

    private List<Opening> numbered(Predicate<Opening> matches) {
        return byNumber.where(matches).stream().sorted(BY_NUMBER).toList();
    }
}
