package io.github.rkaushikdev.staplewright.campus;

import io.github.rkaushikdev.staplewright.CapacityError;
import io.github.rkaushikdev.staplewright.Facts;
import io.github.rkaushikdev.staplewright.keys.KeySymbol;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Turning an access matrix into a hierarchy somebody could actually key.
 *
 * <p>There is no correct answer here and anybody who says otherwise has not read a real
 * requirement. Set cover is the underlying problem and the input is a department's opinion about
 * who should have what, which changes twice before the doors are hung. So this produces a
 * defensible first draft and says out loud where it had to guess.
 *
 * <p>The draft is built in one pass:
 *
 * <ul>
 *   <li>openings no key could tell apart collapse into one change key
 *   <li>each building takes a grand master
 *   <li>an access group that stays inside one building and covers two or more of those change keys
 *       becomes a master under that grand master
 *   <li>an access group that reaches across buildings gets nothing but the top key, and is
 *       reported, because that is a decision and not a detail
 * </ul>
 *
 * <p>What comes back is a {@link Proposal}, not a schedule. Nothing is cut, no bittings exist, and
 * the useful part is the list of places where the hierarchy grants more than the matrix asked for.
 */
public final class Derivation {

    private static final String LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private Derivation() {}

    /** A master drawn for one access group, and the doors that group asked for. */
    private record Master(KeySymbol symbol, Set<String> wanted) {}

    /**
     * Draw a hierarchy that satisfies the matrix, and say what it guessed.
     *
     * @throws CapacityError when there are more buildings than letters to name their grand masters
     */
    public static Proposal derive(AccessMatrix matrix) {
        List<String> notes = new ArrayList<>();
        Map<String, List<List<String>>> byBuilding = new TreeMap<>();
        for (List<String> openings : matrix.keyedAlikeSets()) {
            TreeSet<String> buildings = new TreeSet<>();
            for (String number : openings) {
                buildings.add(matrix.openings().opening(number).building());
            }
            if (buildings.size() > 1) {
                notes.add(String.join("/", openings.stream().sorted().toList())
                        + " sit in more than one building and were left keyed alike anyway");
            }
            byBuilding
                    .computeIfAbsent(buildings.first(), ignored -> new ArrayList<>())
                    .add(openings);
        }

        List<String> letters = letters(byBuilding.size());
        Set<String> cross = new HashSet<>(matrix.crossBuildingGroups());
        List<Grant> grants = new ArrayList<>();
        int branch = 0;
        for (Map.Entry<String, List<List<String>>> entry : byBuilding.entrySet()) {
            String building = entry.getKey();
            List<List<String>> sets = entry.getValue();
            KeySymbol grand = new KeySymbol(letters.get(branch));
            branch++;
            grants.add(new Grant(grand, flattened(sets), "grand master for " + building));

            List<Master> masters = new ArrayList<>();
            for (String name : matrix.coverage().keySet()) {
                if (cross.contains(name) || !matrix.buildingsReached(name).equals(List.of(building))) {
                    continue;
                }
                Set<String> wanted = new HashSet<>(matrix.openingsFor(name));
                List<List<String>> reach =
                        sets.stream().filter(wanted::containsAll).toList();
                if (reach.size() < 2) {
                    continue;
                }
                KeySymbol symbol = grand.childMaster(String.valueOf(LETTERS.charAt(masters.size())));
                masters.add(new Master(symbol, wanted));
                grants.add(new Grant(symbol, flattened(reach), "master for " + name));
            }

            Map<String, Integer> numbered = new HashMap<>();
            for (List<String> openings : sets) {
                KeySymbol parent = masters.stream()
                        .filter(master -> master.wanted().containsAll(openings))
                        .map(Master::symbol)
                        .findFirst()
                        .orElse(grand);
                int number = numbered.merge(parent.render(), 1, Integer::sum);
                grants.add(new Grant(parent.changeKey(number), openings, "change key"));
            }
        }

        for (String name : new TreeSet<>(cross)) {
            notes.add(name + " reaches " + matrix.buildingsReached(name).size() + " buildings and needs the top key");
        }
        for (String number : matrix.unreachableOpenings()) {
            notes.add(number + " is keyed and no access group asked for it");
        }

        grants.sort(Comparator.comparing(Grant::symbol, KeySymbol.SCHEDULE_ORDER));
        return new Proposal(grants, notes);
    }

    /**
     * Where a proposal hands somebody doors the matrix never mentioned, the most extra doors first.
     *
     * <p>For each group, the smallest single symbol that covers everything it asked for is the key
     * it would be issued. Anything else that key opens is an over grant, and the shop would rather
     * argue about it now.
     *
     * <p>A freshly derived draft is clean by construction, so the interesting input here is an older
     * proposal, or one a person edited. Requirements get withdrawn far more often than keys get
     * taken back, and this is the check that finds the gap between the two.
     */
    public static List<OverGrant> overGrants(AccessMatrix matrix, Proposal proposal) {
        List<OverGrant> found = new ArrayList<>();
        for (AccessGroup group : matrix.groups()) {
            Set<String> wanted = new HashSet<>(matrix.openingsFor(group.name()));
            if (wanted.isEmpty()) {
                continue;
            }
            Optional<Grant> best = proposal.grants().stream()
                    .filter(grant -> grant.openings().containsAll(wanted))
                    .min(Comparator.comparingInt(Grant::size)
                            .thenComparing(grant -> grant.symbol().render()));
            if (best.isEmpty()) {
                continue;
            }
            List<String> extra = best.get().openings().stream()
                    .filter(number -> !wanted.contains(number))
                    .distinct()
                    .sorted()
                    .toList();
            if (!extra.isEmpty()) {
                found.add(new OverGrant(group.name(), best.get().symbol().render(), extra));
            }
        }
        found.sort(Comparator.comparing((OverGrant item) -> item.extra().size(), Comparator.reverseOrder())
                .thenComparing(OverGrant::group));
        return List.copyOf(found);
    }

    /** Groups no single key in the draft covers, in name order. A group asking for nothing is not one. */
    public static List<String> unsatisfied(AccessMatrix matrix, Proposal proposal) {
        List<String> found = new ArrayList<>();
        for (AccessGroup group : matrix.groups()) {
            Set<String> wanted = new HashSet<>(matrix.openingsFor(group.name()));
            if (wanted.isEmpty()) {
                continue;
            }
            if (proposal.grants().stream().noneMatch(grant -> grant.openings().containsAll(wanted))) {
                found.add(group.name());
            }
        }
        return found.stream().sorted().toList();
    }

    private static List<String> letters(int count) {
        if (count > LETTERS.length()) {
            throw new CapacityError("more branches than there are letters to name them", Facts.of("wanted", count));
        }
        return LETTERS.substring(0, count).chars().mapToObj(Character::toString).toList();
    }

    private static List<String> flattened(List<List<String>> runs) {
        return runs.stream().flatMap(List::stream).sorted().toList();
    }
}
