package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.ConflictError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.Registry;
import io.github.rkaushikdev.staplewright.util.Tallies;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Who has to get in where, written down before any key is designed: access groups against
 * openings, and nothing else.
 *
 * <p>This is the document the shop should be given and almost never is. A department says which of
 * its people have to open which doors, and the answer is a matrix: access groups down one side,
 * openings along the other.
 *
 * <p>Two things fall straight out of it. Openings wanted by exactly the same set of groups can be
 * keyed alike, because no key would ever tell them apart, and that collapse is where most of the
 * savings in a system come from. And an access group that reaches into more than one building
 * needs a key higher up the hierarchy than a building master, which is the conversation nobody
 * wants to have late in a project.
 *
 * <p>The matrix knows nothing about keys. It is the requirement, and the schedule is one answer to
 * it. It walks its groups in name order.
 */
public final class AccessMatrix implements Iterable<AccessGroup> {

    /** The biggest run first, then runs of one size in the order their door numbers read. */
    private static final Comparator<List<String>> BIGGEST_RUN_FIRST = Comparator.comparing(
                    (List<String> run) -> run.size(), Comparator.reverseOrder())
            .thenComparing(AccessMatrix::compareRuns);

    private final OpeningSchedule openings;
    private final Registry<AccessGroup> groups = new Registry<>("access group", "group");
    private final Map<String, Set<String>> wanted = new LinkedHashMap<>();

    public AccessMatrix(OpeningSchedule openings) {
        this.openings = openings;
    }

    /** The openings the matrix is drawn against. */
    public OpeningSchedule openings() {
        return openings;
    }

    /**
     * Put a group down the side of the matrix, wanting nothing yet.
     *
     * @throws ConflictError when a group by that name is already here
     */
    public AccessGroup addGroup(AccessGroup group) {
        groups.add(group.name(), group);
        wanted.put(group.name(), new TreeSet<>());
        return group;
    }

    /**
     * The group by this name.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError when nobody created it
     */
    public AccessGroup group(String name) {
        return groups.get(name);
    }

    /**
     * This group has to open this door. Asking twice is asking once.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError for an unknown group or opening
     * @throws ConflictError when the opening takes no key
     */
    public void require(String groupName, String openingNumber) {
        group(groupName);
        Opening opening = openings.opening(openingNumber);
        if (!opening.isKeyed()) {
            throw new ConflictError(
                    "that opening takes no key, so nobody can be given one",
                    Facts.of(
                            "opening",
                            openingNumber,
                            "function",
                            opening.function().value()));
        }
        wanted.get(groupName).add(openingNumber);
    }

    /** This group has to open every one of these doors. */
    public void requireAll(String groupName, Iterable<String> openingNumbers) {
        for (String number : openingNumbers) {
            require(groupName, number);
        }
    }

    /** Everything keyed in one building, which is how a master is asked for. */
    public void requireBuilding(String groupName, String building) {
        for (Opening opening : openings.inBuilding(building)) {
            if (opening.isKeyed()) {
                require(groupName, opening.number());
            }
        }
    }

    /** Take a door off a group. A door it never asked for is dropped quietly. */
    public void drop(String groupName, String openingNumber) {
        group(groupName);
        wanted.get(groupName).remove(openingNumber);
    }

    /** The doors one group has to open, in number order. */
    public List<String> openingsFor(String groupName) {
        group(groupName);
        return List.copyOf(wanted.get(groupName));
    }

    /**
     * The groups that have to open one door, in name order.
     *
     * @throws io.github.rkaushikdev.staplewright.NotFoundError for an unknown opening
     */
    public List<String> groupsFor(String openingNumber) {
        openings.opening(openingNumber);
        return wanted.entrySet().stream()
                .filter(entry -> entry.getValue().contains(openingNumber))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /** Whether one group has to open one door. */
    public boolean wants(String groupName, String openingNumber) {
        group(groupName);
        return wanted.get(groupName).contains(openingNumber);
    }

    /** The set of groups that must open a door, which is its keying. */
    public List<String> signature(String openingNumber) {
        return groupsFor(openingNumber);
    }

    /**
     * Openings no key could tell apart, grouped, the biggest run first.
     *
     * <p>Each run is in number order. Doors wanted by nobody are left out. They are a separate
     * problem and putting them all in one giant keyed alike set would hide it.
     */
    public List<List<String>> keyedAlikeSets() {
        Map<List<String>, List<String>> buckets = new LinkedHashMap<>();
        for (Opening opening : openings.keyedOpenings()) {
            List<String> signature = signature(opening.number());
            if (signature.isEmpty()) {
                continue;
            }
            buckets.computeIfAbsent(signature, ignored -> new ArrayList<>()).add(opening.number());
        }
        return buckets.values().stream()
                .map(numbers -> numbers.stream().sorted().toList())
                .sorted(BIGGEST_RUN_FIRST)
                .toList();
    }

    /** Keyed doors no access group asked for, in number order. Somebody still has to get in. */
    public List<String> unreachableOpenings() {
        return openings.keyedOpenings().stream()
                .map(Opening::number)
                .filter(number -> signature(number).isEmpty())
                .toList();
    }

    /** The buildings one group's doors sit in, in code order. */
    public List<String> buildingsReached(String groupName) {
        return openingsFor(groupName).stream()
                .map(number -> openings.opening(number).building())
                .distinct()
                .sorted()
                .toList();
    }

    /** Groups whose doors span buildings, which need a key above a master, in name order. */
    public List<String> crossBuildingGroups() {
        return groups.sortedKeys().stream()
                .filter(name -> buildingsReached(name).size() > 1)
                .toList();
    }

    /** How many doors each group is asking for, the most first and a tie in name order. */
    public Map<String, Integer> coverage() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        wanted.forEach((name, doors) -> counts.put(name, doors.size()));
        return Tallies.biggestFirst(counts);
    }

    /** Every group, in name order. */
    public List<AccessGroup> groups() {
        return groups.byKey();
    }

    /** Whether a group by this name is in the matrix. */
    public boolean contains(Object name) {
        return groups.contains(name);
    }

    /** Every group, in name order. */
    @Override
    public Iterator<AccessGroup> iterator() {
        return groups().iterator();
    }

    /** How many groups the matrix holds. */
    public int size() {
        return groups.size();
    }

    /** Two runs of door numbers compared number by number, a shorter run first when one leads the other. */
    private static int compareRuns(List<String> first, List<String> second) {
        int shared = Math.min(first.size(), second.size());
        for (int index = 0; index < shared; index++) {
            int compared = first.get(index).compareTo(second.get(index));
            if (compared != 0) {
                return compared;
            }
        }
        return Integer.compare(first.size(), second.size());
    }
}
