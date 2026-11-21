package io.github.rkaushikdev.staplewright.control;

import io.github.rkaushikdev.staplewright.util.Tallies;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The physical audit, where somebody walks round and looks at the keys.
 *
 * <p>Once a year a department is asked to put every key it holds on a table and read the tag
 * numbers out. What comes back never matches the ledger, and the four ways it fails to match are
 * all different problems, named by {@link FindingKind}. Not one of them is a refusal. They are the
 * output.
 */
public final class Audit {

    private Audit() {}

    /**
     * Compare what was on the table against what the ledger says.
     *
     * <p>A tag held up twice counts once, with whoever held it up last. A key the ledger expects
     * from the department comes first, by tag; anything held up that the department was not
     * expected to have follows, by tag. A key reported lost or stolen is not expected on the table.
     */
    public static AuditReport reconcile(
            KeyLedger ledger, String department, Iterable<Sighting> sightings, LocalDate on) {
        Map<String, String> seen = new LinkedHashMap<>();
        for (Sighting sighting : sightings) {
            seen.put(sighting.tag(), sighting.keyholder());
        }
        Map<String, String> expected = new TreeMap<>();
        for (Keyholder person : ledger.directory().inDepartment(department)) {
            for (IssuedKey key : ledger.heldBy(person.identifier())) {
                expected.put(key.tag(), key.keyholder());
            }
        }

        List<AuditFinding> findings = new ArrayList<>();
        expected.forEach((tag, holder) -> {
            if (!seen.containsKey(tag)) {
                findings.add(AuditFinding.unaccounted(tag, holder));
            } else if (!seen.get(tag).equals(holder)) {
                findings.add(AuditFinding.wrongHolder(tag, holder, seen.get(tag)));
            }
        });
        new TreeMap<>(seen).forEach((tag, holder) -> {
            if (expected.containsKey(tag)) {
                return;
            }
            if (ledger.contains(tag)) {
                IssuedKey key = ledger.key(tag);
                if (key.state() != KeyState.OUT) {
                    findings.add(AuditFinding.closed(tag, holder));
                } else {
                    findings.add(AuditFinding.wrongHolder(tag, key.keyholder(), holder));
                }
            } else {
                findings.add(AuditFinding.stray(tag, holder));
            }
        });
        return new AuditReport(on, department, seen.size(), expected.size(), findings);
    }

    /**
     * Reconcile the deposit book against the keys that are out.
     *
     * <p>Every key out with a deposit on it should have an open charge, and every open charge should
     * belong to a key that is out. The two lists are usually short and they are always interesting.
     */
    public static DepositCheck checkDeposits(KeyLedger ledger, DepositBook book) {
        Set<String> outTags = new HashSet<>();
        for (IssuedKey key : ledger.out()) {
            if (key.depositCents() != 0) {
                outTags.add(key.tag());
            }
        }
        Set<String> openTags = new HashSet<>(book.openTags());
        List<String> missing =
                outTags.stream().filter(tag -> !openTags.contains(tag)).sorted().toList();
        List<String> orphan =
                openTags.stream().filter(tag -> !outTags.contains(tag)).sorted().toList();
        return new DepositCheck(ledger.depositsHeld(), book.totalHeld(), missing, orphan);
    }

    /**
     * How many keys each department is holding, for the cover page: the biggest first, a tie in
     * department order, and a department holding nothing still listed.
     */
    public static Map<String, Integer> holdingsByDepartment(KeyLedger ledger, Iterable<String> departments) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String department : departments) {
            int total = 0;
            for (Keyholder person : ledger.directory().inDepartment(department)) {
                total += ledger.heldBy(person.identifier()).size();
            }
            counts.put(department, total);
        }
        return Tallies.biggestFirst(counts);
    }
}
