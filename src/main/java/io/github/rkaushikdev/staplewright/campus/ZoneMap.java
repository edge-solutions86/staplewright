package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.NotFoundError;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Tallies;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every zone, and which building sits in which.
 *
 * <p>Zones are how a campus with forty buildings stops being forty things. A zone earns its place
 * the moment somebody asks for a key to everything in the science precinct: without zones that
 * request is a list of eleven buildings that will be wrong within a year.
 *
 * <p>A building sits in one zone. That is a decision rather than a fact about the world, and the
 * map enforces it, because a building in two zones means two zone masters open it and neither of
 * them knows about the other. The map walks its zones in code order.
 */
public final class ZoneMap implements Iterable<Zone> {

    private final OpeningSchedule openings;
    private final Registry<Zone> zones = new Registry<>("zone", "code");
    private final Map<String, String> where = new LinkedHashMap<>();

    public ZoneMap(OpeningSchedule openings) {
        this.openings = openings;
    }

    /** The openings the zones are drawn over. */
    public OpeningSchedule openings() {
        return openings;
    }

    /**
     * Draw a new zone.
     *
     * @throws ConflictError when a zone with that code is already here
     */
    public Zone add(Zone zone) {
        return zones.add(zone.code(), zone);
    }

    /**
     * The zone with this code.
     *
     * @throws NotFoundError when there is none
     */
    public Zone zone(String code) {
        return zones.get(code);
    }

    /**
     * Put a building in a zone, refusing one that is already placed elsewhere. Placing it where it
     * already is changes nothing.
     *
     * @return the zone's code
     * @throws NotFoundError for an unknown building or zone
     * @throws ConflictError when the building is already in another zone, naming that zone
     */
    public String place(String building, String zoneCode) {
        openings.building(building);
        zones.get(zoneCode);
        String held = where.get(building);
        if (held != null && !held.equals(zoneCode)) {
            throw new ConflictError("that building is already in a zone", Facts.of("building", building, "zone", held));
        }
        where.put(building, zoneCode);
        return zoneCode;
    }

    /**
     * Move a building deliberately, which happens after a reorganisation.
     *
     * @return the zone's code
     * @throws NotFoundError for an unknown building or zone, or a building that is in no zone yet
     */
    public String move(String building, String zoneCode) {
        openings.building(building);
        zones.get(zoneCode);
        if (!where.containsKey(building)) {
            throw new NotFoundError("that building is not in a zone", Facts.of("building", building));
        }
        where.put(building, zoneCode);
        return zoneCode;
    }

    /** The zone a building sits in, when somebody placed it. */
    public Optional<Zone> zoneOf(String building) {
        return Optional.ofNullable(where.get(building)).map(zones::get);
    }

    /** The buildings in one zone, in code order. */
    public List<String> buildingsIn(String zoneCode) {
        zones.get(zoneCode);
        return where.entrySet().stream()
                .filter(entry -> entry.getValue().equals(zoneCode))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Every keyed door in a zone, in number order, which is what a zone master would open. */
    public List<String> openingsIn(String zoneCode) {
        return buildingsIn(zoneCode).stream()
                .flatMap(building -> openings.inBuilding(building).stream())
                .filter(Opening::isKeyed)
                .map(Opening::number)
                .sorted()
                .toList();
    }

    /** Buildings nobody has put in a zone, in code order. */
    public List<String> unplaced() {
        return openings.buildings().stream()
                .map(Building::code)
                .filter(code -> !where.containsKey(code))
                .sorted()
                .toList();
    }

    /** Zones holding no buildings, in code order. */
    public List<String> emptyZones() {
        return zones.sortedKeys().stream()
                .filter(code -> buildingsIn(code).isEmpty())
                .toList();
    }

    /** Doors per zone, biggest first and a tie in code order, for the crew assignment. */
    public Map<String, Integer> counts() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String code : zones.sortedKeys()) {
            counts.put(code, openingsIn(code).size());
        }
        return Tallies.biggestFirst(counts);
    }

    /**
     * Who is on call for this building tonight.
     *
     * @throws NotFoundError when the building is in no zone
     */
    public String rotaFor(String building) {
        return zoneOf(building)
                .map(Zone::afterHoursRota)
                .orElseThrow(() -> new NotFoundError("that building is not in a zone", Facts.of("building", building)));
    }

    /** Every zone, in code order. */
    public List<Zone> zones() {
        return zones.byKey();
    }

    /** Whether a zone with this code is on the map. */
    public boolean contains(Object code) {
        return zones.contains(code);
    }

    /** Every zone, in code order. */
    @Override
    public Iterator<Zone> iterator() {
        return zones().iterator();
    }

    /** How many zones there are. */
    public int size() {
        return zones.size();
    }

    /** Whether a set of buildings reaches more than one zone. A building in no zone adds nothing. */
    public static boolean spansZones(ZoneMap zones, Collection<String> buildings) {
        return buildings.stream()
                        .map(zones::zoneOf)
                        .flatMap(Optional::stream)
                        .map(Zone::code)
                        .distinct()
                        .count()
                > 1;
    }
}
